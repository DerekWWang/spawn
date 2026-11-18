package dev.qube.spawn_9b5fbb9.transport;

import java.util.*;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.industry.Ledger;
import dev.qube.spawn_9b5fbb9.survival.SkillApi;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi.ActionResult;
import dev.qube.spawn_9b5fbb9.transport.Vehicles.Kind;
import dev.qube.spawn_9b5fbb9.transport.Vehicles.Spec;
import dev.qube.spawn_9b5fbb9.warfare.Siege;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** One entity type for every wheeled thing. Movement is decided on the server from the driver's input or
 * an ordered destination, so players, bots and conscripted drivers are bound by the same team, fuel,
 * load and ground. */
public class VehicleEntity extends Entity {
    private static final EntityDataAccessor<String> KIND=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.STRING);
    public final Map<String,Long> cargo=new TreeMap<>();
    public final List<UUID> team=new ArrayList<>();
    public Vec3 destination;
    public double throttle,velocity,water,fuelBurn;
    /** Boiler pressure 0..1; metres of running left on the last greasing; feeds the team has missed; calendar tick of its last feed. */
    public double steam,grease,fodder;
    public int teamHunger;
    public long fedAt;
    public float damage;
    /** Client only: metres rolled, for wheel and rod animation. */
    public float roll,rollO;
    // Ordnance state (see Siege).
    public String shot="";
    public int charges,shotsFired;
    public double loadProgress;
    public boolean ready;
    public final Set<String> crew=new LinkedHashSet<>();
    public Vec3 fireTarget;
    // Rail state (see Railway). An engine owns the consist; a car only remembers its engine.
    public final List<BlockPos> path=new ArrayList<>();
    public final List<UUID> cars=new ArrayList<>();
    public double headS;
    public Direction facing=Direction.NORTH;
    public String coupledTo="";
    public VehicleEntity(EntityType<?> type,Level level){super(type,level);blocksBuilding=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){data.define(KIND,"cart");}
    public String kind(){return entityData.get(KIND);}
    public void setKind(String kind){entityData.set(KIND,Vehicles.SPECS.containsKey(kind)?kind:"cart");refreshDimensions();}
    public Spec spec(){Spec spec=Vehicles.SPECS.get(kind());return spec==null?Vehicles.SPECS.get("cart"):spec;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(KIND.equals(key))refreshDimensions();}
    @Override public EntityDimensions getDimensions(Pose pose){return spec().dimensions();}
    @Override protected InterpolationHandler createInterpolationHandler(){return LinearInterpolationHandler.create(this,3);}
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean canBeCollidedWith(Entity other){return true;}
    @Override public boolean isPushable(){return false;}
    @Override public float maxUpStep(){return 1.0f;}
    @Override public ItemStack getPickResult(){return new ItemStack(SurvivalContent.item(kind()));}
    @Override protected Component getTypeName(){return Component.translatable("entity.spawn_9b5fbb9.vehicle."+kind());}
    @Override protected boolean canAddPassenger(Entity passenger){return getPassengers().size()<spec().seats();}
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions dimensions,float scale){
        int index=Math.max(0,getPassengers().indexOf(passenger));double side=0,up=.9,forward=0;
        switch(kind()){
            case "cart"->{up=1.0;forward=.75;}
            case "wagon"->{side=index==0?-.4:.4;up=1.7;forward=1.7;}
            case "chariot"->{side=index==0?-.25:.25;up=.7;forward=index==0?.05:-.3;}
            case "motor_truck"->{side=index==0?-.45:.45;up=1.28;forward=.7;}
            case "locomotive"->{side=index==0?-.55:.55;up=1.18;forward=-1.5;}
            case "rail_carriage"->{side=index%2==0?-.6:.6;up=1.1;forward=2.1-1.4*(index/2);}
            case "trebuchet"->{side=1.0;up=.3;forward=-2.0;}
            // The gunner stands at the trail, clear of the recoil.
            default->{side=.75;up=.05;forward=-spec().length()/2+.4;}
        }
        return new Vec3(side,up,forward).yRot(-getYRot()*Mth.DEG_TO_RAD);
    }

    @Override public void tick(){
        super.tick();
        if(level().isClientSide()){
            // Wheels turn by the distance actually covered along the heading.
            Vec3 heading=Vec3.directionFromRotation(0,getYRot());rollO=roll;roll+=(float)((getX()-xo)*heading.x+(getZ()-zo)*heading.z);
            return;
        }
        if(!(level() instanceof ServerLevel level))return;
        Spec spec=spec();
        if(spec.rail()){if(spec.kind()==Kind.ENGINE)Railway.tick(this,level);}
        else roadTick(level,spec);
        if(spec.artillery()!=null)Siege.tick(this,level);
        if(tickCount%20==0){
            feedTeam(level);
            while(getPassengers().size()>spec.seats())getPassengers().getLast().stopRiding();
            for(Entity rider:getPassengers())if(rider instanceof LivingEntity living&&SurvivalApi.isHumanoid(living)&&Math.abs(velocity)>.03)SkillApi.award(living,Skill.RIDING,rider==getFirstPassenger()?3:.5);
        }
    }
    /** Why this vehicle cannot move under its own power right now; empty when it can. */
    public String immobileReason(){
        Spec spec=spec();
        if(spec.kind()==Kind.CAR)return "";
        if(spec.speed()<=0)return "fixed emplacement";
        if(spec.drawn()){
            double pull=0;if(level() instanceof ServerLevel level)for(UUID id:team)pull+=Vehicles.draught(level.getEntity(id));
            if(pull+1e-6<spec.teamMin())return "needs a team of "+spec.teamMin()+" draught animals";
            // A horse starts about 1200 kg gross on a made road.
            double gross=spec.tare()+Ledger.mass(cargo);if(gross>1200*pull+1e-6)return "the team cannot start "+Math.round(gross)+" kg gross: harness "+(int)Math.ceil(gross/1200)+" horses or lighten the load";
        }
        if(spec.kind()==Kind.MOTOR&&fuelBurn<=0&&Ledger.count(cargo,"spawn_9b5fbb9:petrol")<=0&&Ledger.count(cargo,"spawn_9b5fbb9:fuel_alcohol")<=0)return "out of petrol and fuel alcohol";
        if(spec.kind()==Kind.ENGINE){
            if(Railway.fuelAboard(this)<=0&&fuelBurn<=0)return "no coal, coke or charcoal in the tender";
            if(water<=0)return "boiler dry: stop beside water to refill";
            if(steam<.5)return "raising steam ("+Math.round(steam*100)+"%): a cold boiler takes two hours' firing with a crew on the footplate before it will move";
        }
        return "";
    }
    private double traction(ServerLevel level,Spec spec){
        if(!immobileReason().isEmpty())return 0;
        if(!spec.drawn())return 1;
        double pull=0,pace=1;for(UUID id:team){Entity animal=level.getEntity(id);pull+=Vehicles.draught(animal);if(animal!=null)pace=Math.min(pace,Vehicles.pace(animal));}
        return Math.min(1.2,Math.sqrt(pull/spec.teamMin()))*pace*Math.max(.3,1-.05*teamHunger);
    }
    /** Made roads carry wheels at full pace; turf slows them, and sand, snow, mud and plough bog them down. */
    private double ground(ServerLevel level,Spec spec){
        BlockState below=level.getBlockState(getOnPos());double factor;
        if(below.is(Blocks.DIRT_PATH)||below.is(Blocks.GRAVEL)||below.is(Blocks.COBBLESTONE)||below.is(BlockTags.STONE_BRICKS)||below.is(BlockTags.PLANKS)||below.is(BlockTags.SLABS)||below.is(Blocks.BRICKS)||below.is(Blocks.STONE))factor=1;
        else if(below.is(BlockTags.SAND)||below.is(BlockTags.SNOW)||below.is(Blocks.MUD)||below.is(Blocks.FARMLAND)||below.is(Blocks.CLAY)||below.is(Blocks.SOUL_SAND))factor=.4;
        else if(below.is(BlockTags.DIRT))factor=.7;
        else factor=.6;
        if(factor<1&&level.isRainingAt(blockPosition().above()))factor*=.8;
        if(isInWater())factor*=.25;
        // Heavy lorries and racing chariots suffer most off the road.
        if(spec.kind()==Kind.MOTOR||spec.id().equals("chariot"))factor*=factor;
        return factor;
    }
    private void roadTick(ServerLevel level,Spec spec){
        double forward=0,turn=0,arrive=Double.MAX_VALUE;Entity driver=getFirstPassenger();
        boolean capable=driver instanceof LivingEntity living&&SurvivalApi.isHumanoid(living)&&(SurvivalApi.exempt(living)||SurvivalApi.state(living).mental()>.05);
        if(capable&&destination==null&&driver instanceof ServerPlayer player){
            var input=player.getLastClientInput();forward=(input.forward()?1:0)-(input.backward()?.4:0);
            if(spec.artillery()!=null){float aim=Mth.wrapDegrees(player.getYRot()-getYRot());setYRot(getYRot()+Mth.clamp(aim,-spec.turn(),spec.turn()));}
            else turn=(input.right()?1:0)-(input.left()?1:0);
        }else if(capable&&destination!=null){
            double dx=destination.x-getX(),dz=destination.z-getZ(),distance=Math.sqrt(dx*dx+dz*dz);
            if(distance<2.5)destination=null;
            else{arrive=distance-2.5;float wanted=(float)(Mth.atan2(dz,dx)*Mth.RAD_TO_DEG)-90,difference=Mth.wrapDegrees(wanted-getYRot());turn=Mth.clamp(difference/12,-1,1);forward=Math.abs(difference)>70?.35:1;}
        }else if(!capable)destination=null;
        double limit=spec.speed()<=0?0:spec.speed()*traction(level,spec)*ground(level,spec)/(1+.6*Ledger.mass(cargo)/Math.max(1,spec.capacity()));
        if(driver instanceof LivingEntity living&&SurvivalApi.isHumanoid(living))limit*=.8+.2*SkillApi.level(living,Skill.RIDING)/20d;
        // Real rates: a team or an early lorry gains half a metre per second each second, and stops at 1.5 or 3 m/s/s.
        boolean motor=spec.kind()==Kind.MOTOR;double accel=motor?.0015:.00125,brake=motor?.0075:.004;
        // A driver with somewhere to stop slows in time to stop there.
        if(arrive<Double.MAX_VALUE)limit=Math.min(limit,Math.sqrt(2*brake*.8*arrive)+.01);
        double target=forward*limit,rate=forward==0?.0015:target*velocity<0||Math.abs(target)<Math.abs(velocity)?brake:accel;
        velocity+=Mth.clamp(target-velocity,-rate,rate);
        if(Math.abs(velocity)<.003&&forward==0)velocity=0;
        if(Math.abs(velocity)>.01&&turn!=0)setYRot(getYRot()+(float)(turn*spec.turn()*Math.signum(velocity)));
        Vec3 heading=Vec3.directionFromRotation(0,getYRot());
        double fall=onGround()?-.08:Math.max(-3,getDeltaMovement().y-.08);
        Vec3 motion=new Vec3(heading.x*velocity,fall,heading.z*velocity),before=position();
        setDeltaMovement(motion);move(MoverType.SELF,motion);
        if(horizontalCollision)velocity*=.4;
        double travelled=position().subtract(before).horizontalDistance();
        if(motor&&travelled>0){
            // Thirty litres of petrol to the hundred kilometres; alcohol carries two thirds as far.
            fuelBurn-=travelled;
            while(fuelBurn<=0){if(Ledger.take(cargo,"spawn_9b5fbb9:petrol",1)>0)fuelBurn+=3000;else if(Ledger.take(cargo,"spawn_9b5fbb9:fuel_alcohol",1)>0)fuelBurn+=2000;else{fuelBurn=0;velocity=0;break;}}
        }
        if(travelled>0)lubricate(level,travelled);
        positionTeam(level,spec,heading);
    }
    /** Axles and engines run on grease: tallow or oil from the load, a litre every fifty kilometres. Run dry and the bearings burn out. */
    public void lubricate(ServerLevel level,double travelled){
        grease-=travelled;if(grease>0)return;
        if(Ledger.take(cargo,"spawn_9b5fbb9:lubricant",1)>0){grease+=Vehicles.GREASE_INTERVAL;return;}
        grease=0;damage+=(float)(spec().health()*travelled/20000);if(damage>=spec().health())wreck(level);
    }
    /** Animals in harness eat from the load every tenth of a day, or graze if the vehicle is halted on grass. Unfed teams weaken, then starve. */
    private void feedTeam(ServerLevel level){
        if(team.isEmpty()){fedAt=0;return;}
        long now=SurvivalApi.calendar(level);if(fedAt==0||fedAt>now)fedAt=now;
        for(int guard=0;now-fedAt>=2400&&guard<240;guard++){
            fedAt+=2400;int unfed=0;
            // Over a copy: an animal that starves here dies here, and its death takes it out of the team.
            for(UUID id:List.copyOf(team)){
                if(!(level.getEntity(id) instanceof Mob animal))continue;
                // A feed is a kilogram: forage is drawn from the load into the nosebag by weight.
                for(String feed:Vehicles.FODDER)while(fodder<1&&Ledger.take(cargo,feed,1)>0)fodder+=Ledger.mass(feed);
                boolean fed=fodder>=1;if(fed)fodder-=1;
                if(!fed&&Math.abs(velocity)<.003&&level.getBlockState(animal.getOnPos()).is(Blocks.GRASS_BLOCK))fed=true;
                // Four to the missed feed: a horse mends a point of health every forty-five seconds, and one point every two minutes never caught up with it.
                if(!fed){unfed++;if(teamHunger>=40&&!animal.isInvulnerable())animal.hurtServer(level,level.damageSources().starve(),4);}
            }
            teamHunger=unfed>0?Math.min(60,teamHunger+1):Math.max(0,teamHunger-2);
        }
    }
    private void positionTeam(ServerLevel level,Spec spec,Vec3 heading){
        if(team.isEmpty())return;Vec3 right=new Vec3(-heading.z,0,heading.x);int index=0;boolean pairs=team.size()>1;
        for(var iterator=team.iterator();iterator.hasNext();index++){
            Entity found=level.getEntity(iterator.next());if(found==null)continue;
            if(!(found instanceof Mob animal)||!animal.isAlive()){iterator.remove();index--;continue;}
            double side=pairs?(index%2==0?-.8:.8):0,ahead=spec.length()/2+1.5+(index/2)*2.3;
            Vec3 place=position().add(heading.scale(ahead)).add(right.scale(side));double y=groundBelow(level,place.x,getY()+1.2,place.z);
            animal.setPos(place.x,y,place.z);animal.setYRot(getYRot());animal.setYBodyRot(getYRot());animal.setYHeadRot(getYRot());animal.setDeltaMovement(Vec3.ZERO);animal.fallDistance=0;
        }
    }
    private double groundBelow(ServerLevel level,double x,double fromY,double z){
        BlockPos top=BlockPos.containing(x,fromY,z);
        for(int depth=0;depth<5;depth++){BlockPos pos=top.below(depth);var shape=level.getBlockState(pos).getCollisionShape(level,pos);if(!shape.isEmpty())return pos.getY()+shape.max(Direction.Axis.Y);}
        return getY();
    }
    public void hitch(Mob animal){
        team.add(animal.getUUID());var data=animal.getPersistentData();data.putBoolean("survivalHitched",true);data.putBoolean("survivalHitchedNoAi",animal.isNoAi());
        if(animal instanceof Leashable leashed&&leashed.isLeashed())leashed.dropLeash();animal.setNoAi(true);
    }
    public void releaseTeam(){
        if(level() instanceof ServerLevel level)for(UUID id:team)if(level.getEntity(id) instanceof Mob animal){var data=animal.getPersistentData();animal.setNoAi(data.getBooleanOr("survivalHitchedNoAi",false));data.remove("survivalHitched");data.remove("survivalHitchedNoAi");}
        team.clear();
    }

    @Override public InteractionResult interact(Player player,InteractionHand hand,Vec3 location){
        if(level().isClientSide())return InteractionResult.SUCCESS;
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        ItemStack held=player.getItemInHand(hand);ActionResult result;Mob led=null;
        if(spec().drawn())for(Mob candidate:level().getEntitiesOfClass(Mob.class,getBoundingBox().inflate(10),m->m instanceof Leashable leashed&&leashed.getLeashHolder()==player&&Vehicles.draught(m)>0)){led=candidate;break;}
        if(led!=null)result=Vehicles.hitch(player,this,led);
        else if(player.isShiftKeyDown())result=held.isEmpty()?(cargo.isEmpty()&&!team.isEmpty()?Vehicles.unhitch(player,this):Vehicles.unload(player,this,"",64)):Vehicles.load(player,this,hand,false);
        else if(!held.isEmpty()&&spec().artillery()!=null&&Siege.isMunition(held))result=Vehicles.load(player,this,hand,false);
        else result=Vehicles.board(player,this);
        player.sendOverlayMessage(Component.literal(result.message()));return InteractionResult.SUCCESS;
    }
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){
        if(isRemoved()||isInvulnerableToBase(source))return false;
        if(source.getEntity() instanceof Player player&&player.getAbilities().instabuild){releaseTeam();Railway.removed(this,level);discard();return true;}
        if(amount<2)return false; // bare hands do not wreck a wagon
        damage+=amount;markHurt();
        if(damage>=spec().health())wreck(level);
        return true;
    }
    /** Cargo spills, the team bolts free, and any powder aboard goes up. */
    public void wreck(ServerLevel level){
        if(isRemoved())return;
        ejectPassengers();releaseTeam();Railway.removed(this,level);
        long powder=Ledger.count(cargo,"minecraft:gunpowder")+Ledger.count(cargo,"spawn_9b5fbb9:powder_charge")/2+Ledger.count(cargo,"spawn_9b5fbb9:paper_cartridge")/96+Ledger.count(cargo,"spawn_9b5fbb9:explosive_shell")+charges/2;
        for(String volatileItem:List.of("minecraft:gunpowder","spawn_9b5fbb9:powder_charge","spawn_9b5fbb9:paper_cartridge","spawn_9b5fbb9:explosive_shell"))cargo.remove(volatileItem);
        int stacks=0;
        for(String id:new ArrayList<>(cargo.keySet()))while(stacks<16&&cargo.containsKey(id)){ItemStack stack=Ledger.withdrawStack(cargo,id);if(stack.isEmpty())break;spawnAtLocation(level,stack);stacks++;}
        spawnAtLocation(level,new ItemStack(SurvivalContent.item("metal_scrap"),2));
        Vec3 at=position();discard();
        if(powder>0)level.explode(null,at.x,at.y+.5,at.z,(float)Math.min(7,1.5+Math.sqrt(powder)/3),true,Level.ExplosionInteraction.TNT);
    }
    @Override public void remove(RemovalReason reason){
        if(reason.shouldDestroy()&&level() instanceof ServerLevel level){releaseTeam();Railway.removed(this,level);}
        super.remove(reason);
    }

    private static String ids(Collection<UUID> ids){var out=new StringJoiner(",");for(UUID id:ids)out.add(id.toString());return out.toString();}
    private static void ids(String text,Collection<UUID> into){into.clear();for(String part:text.split(","))if(!part.isBlank())try{into.add(UUID.fromString(part));}catch(IllegalArgumentException ignored){}}
    private static void point(ValueOutput out,String name,Vec3 value){out.putBoolean(name,value!=null);if(value!=null){out.putDouble(name+"X",value.x);out.putDouble(name+"Y",value.y);out.putDouble(name+"Z",value.z);}}
    private static Vec3 point(ValueInput in,String name){return in.getBooleanOr(name,false)?new Vec3(in.getDoubleOr(name+"X",0),in.getDoubleOr(name+"Y",0),in.getDoubleOr(name+"Z",0)):null;}
    @Override protected void addAdditionalSaveData(ValueOutput out){
        out.putString("kind",kind());out.store("cargo",CompoundTag.CODEC,Ledger.save(cargo));out.putString("team",ids(team));point(out,"destination",destination);
        out.putDouble("water",water);out.putDouble("fuelBurn",fuelBurn);out.putFloat("damage",damage);out.putDouble("steam",steam);out.putDouble("grease",grease);out.putDouble("fodder",fodder);out.putInt("teamHunger",teamHunger);out.putLong("fedAt",fedAt);
        out.putString("shot",shot);out.putInt("charges",charges);out.putInt("shotsFired",shotsFired);out.putDouble("loadProgress",loadProgress);out.putBoolean("ready",ready);out.putString("crew",String.join(",",crew));point(out,"fireTarget",fireTarget);
        int[] nodes=new int[path.size()*3];for(int i=0;i<path.size();i++){nodes[i*3]=path.get(i).getX();nodes[i*3+1]=path.get(i).getY();nodes[i*3+2]=path.get(i).getZ();}
        out.putIntArray("path",nodes);out.putDouble("headS",headS);out.putString("cars",ids(cars));out.putString("facing",facing.getName());out.putString("coupledTo",coupledTo);out.putDouble("throttle",throttle);
    }
    @Override protected void readAdditionalSaveData(ValueInput in){
        setKind(in.getStringOr("kind","cart"));Ledger.load(cargo,in.read("cargo",CompoundTag.CODEC).orElse(new CompoundTag()));ids(in.getStringOr("team",""),team);destination=point(in,"destination");
        water=Mth.clamp(in.getDoubleOr("water",0),0,3000);fuelBurn=in.getDoubleOr("fuelBurn",0);damage=in.getFloatOr("damage",0);steam=Mth.clamp(in.getDoubleOr("steam",0),0,1);grease=in.getDoubleOr("grease",0);fodder=Math.max(0,in.getDoubleOr("fodder",0));teamHunger=Mth.clamp(in.getIntOr("teamHunger",0),0,60);fedAt=in.getLongOr("fedAt",0);
        shot=in.getStringOr("shot","");charges=in.getIntOr("charges",0);shotsFired=in.getIntOr("shotsFired",0);loadProgress=in.getDoubleOr("loadProgress",0);ready=in.getBooleanOr("ready",false);
        crew.clear();for(String id:in.getStringOr("crew","").split(","))if(!id.isBlank())crew.add(id);fireTarget=point(in,"fireTarget");
        path.clear();int[] nodes=in.getIntArray("path").orElse(new int[0]);for(int i=0;i+2<nodes.length;i+=3)path.add(new BlockPos(nodes[i],nodes[i+1],nodes[i+2]));
        headS=in.getDoubleOr("headS",0);ids(in.getStringOr("cars",""),cars);Direction saved=Direction.byName(in.getStringOr("facing","north"));facing=saved==null||saved.getAxis().isVertical()?Direction.NORTH:saved;
        coupledTo=in.getStringOr("coupledTo","");throttle=in.getDoubleOr("throttle",0);
    }
}
