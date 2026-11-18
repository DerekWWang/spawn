package dev.qube.spawn_9b5fbb9.industry;

import java.util.*;
import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.transport.Railway;
import dev.qube.spawn_9b5fbb9.transport.VehicleEntity;
import dev.qube.spawn_9b5fbb9.transport.Vehicles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** A works consumes whole batches from its stock ledger, advances them with real labour, heat or time,
 * and places finished goods in a products ledger. Nothing is created without its full bill of materials. */
public class WorksEntity extends BlockEntity {
    /** A works bunkers eight tonnes of fuel; a power station two hundred. */
    public long fuelLimit(){return station().equals("power_station")?64_000_000L:2_560_000L;}
    public final Map<String,Long> stock=new Ledger.Account(),products=new Ledger.Account();
    public final Set<String> workers=new LinkedHashSet<>();
    private final Map<String,Long> activeTicks=new HashMap<>();
    public String recipe="";
    public boolean active;
    public double progress;
    public long fuel,last,batches;
    // Site state (see Facility): unmet requirements for the selected recipe, cleanroom condition, and a power station's load.
    public List<String> site=List.of(),cleanFaults=List.of();
    public double cleanliness;
    public long siteAt=Long.MIN_VALUE/2,loadStamp;
    public int loadUsed;
    public WorksEntity(BlockPos pos,BlockState state){super(IndustrialContent.WORKS_ENTITY.get(),pos,state);}
    public String station(){return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();}
    public boolean usesFuel(){String station=station();return station.equals("foundry")||station.equals("chemical_works")||station.equals("machine_shop")||station.equals("power_station");}
    /** Re-surveys the site. A cleanroom gains cleanliness only while sealed, powered, shut and free of unsuited people. */
    public void survey(ServerLevel level,Industry.Recipe r){siteAt=level.getGameTime();read(level,r,true);}
    /** The site as it stands, read without running it: looking at a works neither cleans its room nor burns the station's coal. */
    public void inspect(ServerLevel level,Industry.Recipe r){read(level,r,false);}
    private void read(ServerLevel level,Industry.Recipe r,boolean step){
        if(r.needs().contains("cleanroom")){
            var room=Facility.cleanroom(level,worldPosition);cleanFaults=List.copyOf(room.faults());
            if(!cleanFaults.isEmpty())cleanliness=0;
            else if(!step){}
            else if(!Facility.draw(level,worldPosition,3+(room.filters()+1)/2,5))cleanliness=Math.max(0,cleanliness-10);
            else if(room.doorOpen()||room.unsuited()>0)cleanliness=Math.max(0,cleanliness-15);
            else cleanliness=Math.min(100,cleanliness+5);
        }else{
            // A room nobody is keeping clean does not stay clean: turned to other work, the fans are off.
            cleanFaults=List.of();if(step)cleanliness=Math.max(0,cleanliness-15);
        }
        site=List.copyOf(Facility.unmet(level,this,r));setChanged();
    }
    public boolean pendingVehicle(){for(String id:products.keySet())if(id.startsWith("vehicle:"))return true;return false;}
    public static void tick(Level world,BlockPos pos,BlockState state,WorksEntity works){
        if(!(world instanceof ServerLevel level)||level.getGameTime()%20!=0)return;
        // Food held as stock or as finished goods goes on ageing while it waits.
        if(level.getGameTime()%100==0){long before=Ledger.count(works.stock,Ledger.SPOILED)+Ledger.count(works.products,Ledger.SPOILED);Ledger.settle(works.stock,level);Ledger.settle(works.products,level);if(Ledger.count(works.stock,Ledger.SPOILED)+Ledger.count(works.products,Ledger.SPOILED)!=before)works.setChanged();}
        works.advance(level,SurvivalApi.calendar(level));
    }
    private boolean begin(Industry.Recipe r){
        if(!Ledger.has(stock,r.inputs()))return false;
        r.inputs().forEach((ingredient,count)->Ledger.take(stock,ingredient,count));active=true;return true;
    }
    public void advance(ServerLevel level,long now){
        long dt=last==0?20:Math.max(0,now-last);last=now;
        Industry.Recipe r=Industry.RECIPES.get(recipe);
        if(r==null||!r.station().equals(station()))return;
        long ticks=Industry.ticks(r);
        if(level.getGameTime()-siteAt>=100)survey(level,r);
        if(!site.isEmpty())return;
        if(!active&&!begin(r))return;
        setChanged();
        if(station().equals("powder_mill")&&nakedFlame(level)){explode(level);return;}
        double work;
        if(r.attended()){
            work=0;var hands=new ArrayList<LivingEntity>();
            for(var iterator=workers.iterator();iterator.hasNext();){
                String id=iterator.next();LivingEntity actor=null;
                try{if(level.getEntity(UUID.fromString(id)) instanceof LivingEntity living)actor=living;}catch(IllegalArgumentException malformed){iterator.remove();continue;}
                if(actor==null)continue;
                if(!actor.isAlive()||actor.getPersistentData().getLongOr("survivalWorksPos",Long.MIN_VALUE)!=worldPosition.asLong()){iterator.remove();continue;}
                if(hands.size()>=r.maxWorkers()||actor.distanceToSqr(Vec3.atCenterOf(worldPosition))>64||!SurvivalApi.isHumanoid(actor))continue;
                SurvivalState body=SurvivalApi.state(actor);
                if(body.manipulation()<.1||body.mental()<=.05||r.skill()!=null&&body.skills.level(r.skill())<r.minimumSkill())continue;
                // One pair of hands: the shared manual-labour gate also serves forges, looms and field work.
                if(LaborGuard.worldBusy(actor)||LaborGuard.gridBusy(actor)||LaborGuard.forgeBusy(actor))continue;
                if(level.getGameTime()-actor.getPersistentData().getLongOr("survivalManualWorkAt",Long.MIN_VALUE/2)<20)continue;
                hands.add(actor);work+=20*SkillApi.manualRate(body,r.skill());
            }
            if(work<=0)return;
            long burn=20L*Industry.fuelRate(station());
            if(r.burnsFuel()&&fuel<burn)return;
            // Energy follows the work actually done, so a batch costs the same kilowatt-hours however many hands are on it.
            if(r.electric()&&!Facility.draw(level,worldPosition,r.load(),work/(20d*Math.max(1,r.maxWorkers())))){site=List.of("the power station cannot carry this load (each generator gives "+Facility.GENERATOR_KW+" kW before cable loss; this job draws "+r.load()+" kW)");siteAt=level.getGameTime();return;}
            if(r.burnsFuel())fuel-=burn;
            // The second is charged to the hands only now that it was worked: a cold hearth or a dead main used to
            // cost them the hunger and credit them the hours all the same, and the hours were paid in skill.
            for(LivingEntity actor:hands){
                LaborGuard.worked(actor);var body=SurvivalApi.state(actor);body.hunger=Math.max(0,body.hunger-20*.0005);SurvivalApi.save(actor,body);SurvivalApi.syncFood(actor,body);
                activeTicks.merge(actor.getUUID().toString(),20L,Long::sum);
            }
        }else{
            work=Math.min(dt,64L*ticks);
            if(r.burnsFuel()){work=Math.min(work,fuel);fuel-=(long)work;}
            if(work<=0)return;
        }
        progress+=work;
        for(int guard=0;active&&progress>=ticks&&guard<64;guard++){
            progress-=ticks;finish(level,r);
            if(!begin(r)){active=false;progress=0;}
        }
    }
    private void finish(ServerLevel level,Industry.Recipe r){
        // Defects show at dicing and test: every point of cleanliness lost there scraps a tenth of the dice. Wafer stages and
        // packaging need the room in order (95%) but pass on what they are given.
        long amount=r.output().endsWith(":silicon_die")?(long)Math.floor(r.amount()*Math.clamp((cleanliness-90)/10,0d,1d)):r.amount();
        Ledger.add(products,r.output(),amount);r.byproducts().forEach((id,count)->Ledger.add(products,id,count));batches++;
        if(r.attended())hazards(level,r);
        if(r.attended()&&r.skill()!=null&&r.practice()>0){
            for(var entry:activeTicks.entrySet())try{if(level.getEntity(UUID.fromString(entry.getKey())) instanceof LivingEntity actor)SkillApi.awardWork(actor,r.skill(),entry.getValue(),r.practice()/25d);}catch(IllegalArgumentException ignored){}
        }
        activeTicks.clear();
        if(r.vehicle())releaseVehicles();
    }
    /** Industry maims. Molten metal burns, acids scald and poison, belts and gears take fingers; practice lowers the odds. */
    private void hazards(ServerLevel level,Industry.Recipe r){
        String station=station();boolean acid=r.output().endsWith("_acid")||r.inputs().keySet().stream().anyMatch(id->id.endsWith("_acid"));
        double base=switch(station){case "foundry"->.02;case "chemical_works"->acid?.04:.01;case "machine_shop"->.015;default->acid?.02:0;};
        if(base<=0)return;
        for(String id:activeTicks.keySet()){
            LivingEntity actor;try{actor=level.getEntity(UUID.fromString(id)) instanceof LivingEntity living?living:null;}catch(IllegalArgumentException malformed){continue;}
            // The machine hurts whoever is at it when the batch comes off, not someone who lent a hand an hour ago and is a mile away.
            if(actor==null||!actor.isAlive()||SurvivalApi.exempt(actor)||actor.distanceToSqr(Vec3.atCenterOf(worldPosition))>64)continue;
            SurvivalState body=SurvivalApi.state(actor);double skill=r.skill()==null?0:body.skills.level(r.skill());
            if(level.getRandom().nextDouble()>=base*(1.5-skill/20))continue;
            var arm=level.getRandom().nextBoolean()?SurvivalState.Part.LEFT_ARM:SurvivalState.Part.RIGHT_ARM;String what;
            if(station.equals("machine_shop")){var digit=SurvivalState.Part.values()[SurvivalState.Part.LEFT_THUMB.ordinal()+level.getRandom().nextInt(10)];body.injure(digit,45,true);what="A hand was caught in the machinery";}
            else if(station.equals("foundry")){body.injure(arm,14,false);what="Burned by molten metal";}
            else{body.injure(arm,10,false);if(r.output().contains("hydrofluoric")||r.inputs().containsKey("spawn_9b5fbb9:hydrofluoric_acid")||r.output().contains("lead"))body.poisonTicks=Math.max(body.poisonTicks,body.rules.ticks("poisonDurationTicks"));what="Scalded by acid";}
            SurvivalApi.save(actor,body);
            if(actor instanceof net.minecraft.server.level.ServerPlayer player)player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(what));
        }
    }
    /** Finished vehicles roll out onto clear ground (rolling stock onto track) beside the yard. */
    public String releaseVehicles(){
        if(!(level instanceof ServerLevel world))return "";var message=new StringJoiner("; ");
        for(String id:new ArrayList<>(products.keySet())){
            if(!id.startsWith("vehicle:"))continue;String kind=id.substring(8);var spec=Vehicles.SPECS.get(kind);if(spec==null){products.remove(id);continue;}
            while(products.getOrDefault(id,0L)>0){
                Vec3 place=spec.rail()?Railway.freeTrackNear(world,worldPosition,8):clearGround(world,spec);
                if(place==null){message.add(spec.rail()?"Lay track within eight blocks to roll out the "+kind.replace('_',' '):"Clear level ground beside the yard for the "+kind.replace('_',' '));break;}
                VehicleEntity vehicle=Vehicles.spawn(world,kind,place,0);if(vehicle==null)break;
                Ledger.take(products,id,1);message.add("A "+kind.replace('_',' ')+" left the yard");
            }
        }
        setChanged();return message.toString();
    }
    private Vec3 clearGround(ServerLevel world,Vehicles.Spec spec){
        for(int radius=2;radius<=5;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy=1;dy>=-1;dy--){
                BlockPos pos=worldPosition.offset(dx,dy,dz);
                if(!world.getBlockState(pos.below()).isFaceSturdy(world,pos.below(),net.minecraft.core.Direction.UP))continue;
                Vec3 at=Vec3.atBottomCenterOf(pos);var box=spec.dimensions().makeBoundingBox(at).deflate(.05);
                if(world.noCollision(box)&&world.getEntities(null,box).isEmpty())return at;
            }
        }
        return null;
    }
    /** Black powder is milled wet and far from fire for a reason. */
    private boolean nakedFlame(ServerLevel level){
        for(BlockPos p:BlockPos.betweenClosed(worldPosition.offset(-4,-1,-4),worldPosition.offset(4,2,4))){
            BlockState state=level.getBlockState(p);
            if(state.is(Blocks.FIRE)||state.is(Blocks.LAVA)||state.is(Blocks.CAMPFIRE)&&state.getValue(CampfireBlock.LIT))return true;
            if(level.getBlockEntity(p) instanceof dev.qube.spawn_9b5fbb9.content.WorkstationEntity station&&station.fuel>0&&!station.input.isEmpty())return true;
        }
        return false;
    }
    private void explode(ServerLevel level){
        double powder=Ledger.count(stock,"minecraft:gunpowder")+Ledger.count(products,"minecraft:gunpowder")+20;
        stock.clear();products.clear();active=false;BlockPos pos=worldPosition;
        level.removeBlock(pos,false);
        level.explode(null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,(float)Math.min(8,2.5+Math.sqrt(powder)/4),true,Level.ExplosionInteraction.TNT);
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);
        Ledger.load(stock,in.read("stock",CompoundTag.CODEC).orElse(new CompoundTag()));Ledger.load(products,in.read("products",CompoundTag.CODEC).orElse(new CompoundTag()));
        // Vehicle tokens are not items; restore them explicitly.
        var pending=in.read("vehicles",CompoundTag.CODEC).orElse(new CompoundTag());for(String key:pending.keySet())if(pending.getLongOr(key,0)>0)products.put("vehicle:"+key,pending.getLongOr(key,0));
        recipe=in.getStringOr("recipe","");active=in.getBooleanOr("active",false);progress=in.getDoubleOr("progress",0);fuel=Math.clamp(in.getLongOr("fuel",0),0L,64_000_000L);last=in.getLongOr("last",0);batches=in.getLongOr("batches",0);cleanliness=Math.clamp(in.getDoubleOr("cleanliness",0),0d,100d);
        workers.clear();for(String id:in.getStringOr("workers","").split(","))if(!id.isBlank())workers.add(id);
        activeTicks.clear();var ticks=in.read("activeTicks",CompoundTag.CODEC).orElse(new CompoundTag());for(String key:ticks.keySet())activeTicks.put(key,Math.clamp(ticks.getLongOr(key,0),0L,2000000000L));
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);
        var items=Ledger.save(products);var vehicles=new CompoundTag();
        products.forEach((id,count)->{if(id.startsWith("vehicle:")){vehicles.putLong(id.substring(8),count);items.remove(id);}});
        out.store("stock",CompoundTag.CODEC,Ledger.save(stock));out.store("products",CompoundTag.CODEC,items);out.store("vehicles",CompoundTag.CODEC,vehicles);
        out.putString("recipe",recipe);out.putBoolean("active",active);out.putDouble("progress",progress);out.putLong("fuel",fuel);out.putLong("last",last);out.putLong("batches",batches);out.putDouble("cleanliness",cleanliness);
        out.putString("workers",String.join(",",workers));
        var ticks=new CompoundTag();activeTicks.forEach(ticks::putLong);out.store("activeTicks",CompoundTag.CODEC,ticks);
    }
    /** Real materials survive demolition: stock and finished goods spill; an unfinished batch is lost. */
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){
        if(level!=null){
            for(var ledger:List.of(stock,products))for(String id:new ArrayList<>(ledger.keySet())){
                if(id.startsWith("vehicle:"))continue;
                for(int stacks=0;stacks<54&&ledger.containsKey(id);stacks++){ItemStack stack=Ledger.withdrawStack(ledger,id,level);if(stack.isEmpty())break;Block.popResource(level,pos,stack);}
            }
            stock.clear();products.clear();
        }
        super.preRemoveSideEffects(pos,state);
    }
}
