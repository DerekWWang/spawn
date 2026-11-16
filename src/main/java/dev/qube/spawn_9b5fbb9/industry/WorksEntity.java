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
    public static final long FUEL_LIMIT=256000;
    public final Map<String,Long> stock=new TreeMap<>(),products=new TreeMap<>();
    public final Set<String> workers=new LinkedHashSet<>();
    private final Map<String,Long> activeTicks=new HashMap<>();
    public String recipe="";
    public boolean active;
    public double progress;
    public long fuel,last,batches;
    public WorksEntity(BlockPos pos,BlockState state){super(IndustrialContent.WORKS_ENTITY.get(),pos,state);}
    public String station(){return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();}
    public boolean usesFuel(){String station=station();return station.equals("foundry")||station.equals("chemical_works")||station.equals("machine_shop")||station.equals("electronics_works");}
    public boolean pendingVehicle(){for(String id:products.keySet())if(id.startsWith("vehicle:"))return true;return false;}
    public static void tick(Level world,BlockPos pos,BlockState state,WorksEntity works){
        if(world instanceof ServerLevel level&&level.getGameTime()%20==0)works.advance(level,SurvivalApi.calendar(level));
    }
    private boolean begin(Industry.Recipe r){
        if(!Ledger.has(stock,r.inputs()))return false;
        r.inputs().forEach((ingredient,count)->Ledger.take(stock,ingredient,count));active=true;return true;
    }
    public void advance(ServerLevel level,long now){
        long dt=last==0?20:Math.max(0,now-last);last=now;
        Industry.Recipe r=Industry.RECIPES.get(recipe);
        if(r==null||!r.station().equals(station()))return;
        if(!active&&!begin(r))return;
        setChanged();
        if(station().equals("powder_mill")&&nakedFlame(level)){explode(level);return;}
        double work;
        if(r.attended()){
            work=0;int hands=0;
            for(var iterator=workers.iterator();iterator.hasNext();){
                String id=iterator.next();LivingEntity actor=null;
                try{if(level.getEntity(UUID.fromString(id)) instanceof LivingEntity living)actor=living;}catch(IllegalArgumentException malformed){iterator.remove();continue;}
                if(actor==null)continue;
                if(!actor.isAlive()||actor.getPersistentData().getLongOr("survivalWorksPos",Long.MIN_VALUE)!=worldPosition.asLong()){iterator.remove();continue;}
                if(hands>=r.maxWorkers()||actor.distanceToSqr(Vec3.atCenterOf(worldPosition))>64||!SurvivalApi.isHumanoid(actor))continue;
                SurvivalState body=SurvivalApi.state(actor);
                if(body.manipulation()<.1||body.mental()<=.05||r.skill()!=null&&body.skills.level(r.skill())<r.minimumSkill())continue;
                // One pair of hands: the shared manual-labour gate also serves forges, looms and field work.
                if(LaborGuard.worldBusy(actor)||LaborGuard.gridBusy(actor)||LaborGuard.forgeBusy(actor))continue;
                if(level.getGameTime()-actor.getPersistentData().getLongOr("survivalManualWorkAt",Long.MIN_VALUE/2)<20)continue;
                LaborGuard.worked(actor);hands++;work+=20*SkillApi.manualRate(body,r.skill());
                body.hunger=Math.max(0,body.hunger-20*.0005);SurvivalApi.save(actor,body);SurvivalApi.syncFood(actor,body);
                activeTicks.merge(id,20L,Long::sum);
            }
            if(work<=0)return;
            if(r.burnsFuel()){if(fuel<20)return;fuel-=20;}
        }else{
            work=Math.min(dt,64L*r.ticks());
            if(r.burnsFuel()){work=Math.min(work,fuel);fuel-=(long)work;}
            if(work<=0)return;
        }
        progress+=work;
        for(int guard=0;active&&progress>=r.ticks()&&guard<64;guard++){
            progress-=r.ticks();finish(level,r);
            if(!begin(r)){active=false;progress=0;}
        }
    }
    private void finish(ServerLevel level,Industry.Recipe r){
        Ledger.add(products,r.output(),r.amount());r.byproducts().forEach((id,count)->Ledger.add(products,id,count));batches++;
        if(r.attended()&&r.skill()!=null&&r.practice()>0){
            for(var entry:activeTicks.entrySet())try{if(level.getEntity(UUID.fromString(entry.getKey())) instanceof LivingEntity actor)SkillApi.awardWork(actor,r.skill(),entry.getValue(),r.practice()/25d);}catch(IllegalArgumentException ignored){}
        }
        activeTicks.clear();
        if(r.vehicle())releaseVehicles();
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
        recipe=in.getStringOr("recipe","");active=in.getBooleanOr("active",false);progress=in.getDoubleOr("progress",0);fuel=Math.clamp(in.getLongOr("fuel",0),0,FUEL_LIMIT);last=in.getLongOr("last",0);batches=in.getLongOr("batches",0);
        workers.clear();for(String id:in.getStringOr("workers","").split(","))if(!id.isBlank())workers.add(id);
        activeTicks.clear();var ticks=in.read("activeTicks",CompoundTag.CODEC).orElse(new CompoundTag());for(String key:ticks.keySet())activeTicks.put(key,Math.clamp(ticks.getLongOr(key,0),0L,4000000L));
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);
        var items=new TreeMap<String,Long>();var vehicles=new CompoundTag();
        products.forEach((id,count)->{if(id.startsWith("vehicle:"))vehicles.putLong(id.substring(8),count);else items.put(id,count);});
        out.store("stock",CompoundTag.CODEC,Ledger.save(stock));out.store("products",CompoundTag.CODEC,Ledger.save(items));out.store("vehicles",CompoundTag.CODEC,vehicles);
        out.putString("recipe",recipe);out.putBoolean("active",active);out.putDouble("progress",progress);out.putLong("fuel",fuel);out.putLong("last",last);out.putLong("batches",batches);
        out.putString("workers",String.join(",",workers));
        var ticks=new CompoundTag();activeTicks.forEach(ticks::putLong);out.store("activeTicks",CompoundTag.CODEC,ticks);
    }
    /** Real materials survive demolition: stock and finished goods spill; an unfinished batch is lost. */
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){
        if(level!=null){
            for(var ledger:List.of(stock,products))for(String id:new ArrayList<>(ledger.keySet())){
                if(id.startsWith("vehicle:"))continue;
                for(int stacks=0;stacks<54&&ledger.containsKey(id);stacks++){ItemStack stack=Ledger.withdrawStack(ledger,id);if(stack.isEmpty())break;Block.popResource(level,pos,stack);}
            }
            stock.clear();products.clear();
        }
        super.preRemoveSideEffects(pos,state);
    }
}
