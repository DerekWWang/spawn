package dev.qube.spawn_9b5fbb9.transport;

import java.util.*;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.controllers.WorldActions;
import dev.qube.spawn_9b5fbb9.industry.IndustrialContent;
import dev.qube.spawn_9b5fbb9.industry.Ledger;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi.ActionResult;
import dev.qube.spawn_9b5fbb9.transport.RailTrackBlock.Part;
import dev.qube.spawn_9b5fbb9.transport.Vehicles.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Railways: laid a metre at a time by labour from rails, sleepers, ballast and spikes; worked by a
 * steam engine that needs coal and water, hauls coupled stock, slows on grades and leaves the rails
 * if it takes a curve too fast. */
public final class Railway {
    private Railway(){}
    /** A right-angle curve is taken at a crawl: 11 km/h. */
    public static final double CURVE_LIMIT=.15;
    /** Half a metre per second each second on the brakes; a fifth of that from the regulator; one block of climb in thirty. */
    public static final double BRAKE=.0012,ACCELERATION=.0005;
    public static final int GRADE_RUN=30;
    public static final double TENDER_WATER=3000;
    /** Centre-to-centre distance of two coupled vehicles: half of each plus buffers and coupling. */
    public static double coupling(double first,double second){return (first+second)/2+.35;}
    private static double length(ServerLevel level,VehicleEntity engine,int index){
        return index==0?engine.spec().length():level.getEntity(engine.cars.get(index-1)) instanceof VehicleEntity car?car.spec().length():5;
    }
    /** How far behind the engine's centre the given vehicle of its train rides. */
    private static double offset(ServerLevel level,VehicleEntity engine,int index){
        double behind=0;for(int i=1;i<=index;i++)behind+=coupling(length(level,engine,i-1),length(level,engine,i));return behind;
    }
    /** Two and a half man-hours to bed, sleeper, rail and spike a metre of line. */
    public static final long LAY_TICKS=2500;
    /** Materials for one metre of line. */
    public static final Map<String,Integer> METRE;
    static {var bill=new LinkedHashMap<String,Integer>();bill.put("spawn_9b5fbb9:steel_rail",2);bill.put("spawn_9b5fbb9:sleeper",2);bill.put("minecraft:gravel",2);bill.put("spawn_9b5fbb9:metal_rivet",8);METRE=Collections.unmodifiableMap(bill);}
    public static void init(){NeoForge.EVENT_BUS.addListener(Railway::useRail);}

    // ---- Track ----------------------------------------------------------------------------------------
    public static boolean centre(Level level,BlockPos pos){BlockState state=level.getBlockState(pos);return state.getBlock() instanceof RailTrackBlock&&state.getValue(RailTrackBlock.PART)==Part.CENTER;}
    public static Vec3 node(BlockPos pos){return new Vec3(pos.getX()+.5,pos.getY()+.25,pos.getZ()+.5);}
    public static BlockPos trackAt(ServerLevel level,BlockPos near){
        for(int dy:new int[]{0,-1,1})if(centre(level,near.above(dy)))return near.above(dy);
        for(int dy:new int[]{0,-1,1})for(Direction side:Direction.Plane.HORIZONTAL)if(centre(level,near.relative(side).above(dy)))return near.relative(side).above(dy);
        return null;
    }
    public static Vec3 freeTrackNear(ServerLevel level,BlockPos origin,int radius){
        BlockPos best=null;double distance=Double.MAX_VALUE;
        for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-radius,-2,-radius),origin.offset(radius,2,radius))){
            if(!level.hasChunkAt(pos)||!centre(level,pos)||pos.distSqr(origin)>=distance)continue;
            if(!level.getEntitiesOfClass(VehicleEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(1.6)).isEmpty())continue;
            best=pos.immutable();distance=pos.distSqr(origin);
        }
        return best==null?null:node(best);
    }
    /** The next centre block when travelling with horizontal heading (dx,dz): straight on, else a curve; grades of one block are followed. */
    private static BlockPos next(ServerLevel level,BlockPos current,int dx,int dz){
        int[][] headings={{dx,dz},{-dz,dx},{dz,-dx}};
        for(int[] heading:headings)for(int dy:new int[]{0,1,-1}){BlockPos candidate=current.offset(heading[0],dy,heading[1]);if(level.hasChunkAt(candidate)&&centre(level,candidate))return candidate;}
        return null;
    }
    public static Direction.Axis axis(LivingEntity actor){
        String saved=actor.getPersistentData().getStringOr("survivalRailAxis","");
        return saved.equals("x")?Direction.Axis.X:saved.equals("z")?Direction.Axis.Z:actor.getDirection().getAxis()==Direction.Axis.X?Direction.Axis.X:Direction.Axis.Z;
    }
    public static String validateLay(LivingEntity actor,BlockPos pos){
        if(!(actor.level() instanceof ServerLevel level))return "Server world required";
        Direction.Axis axis=axis(actor);BlockState here=level.getBlockState(pos);
        if(centre(level,pos))return "Track is already laid here";
        if(!here.canBeReplaced()&&!(here.getBlock() instanceof RailTrackBlock))return "The track bed must be clear";
        if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return "Track needs a firm, level bed";
        Direction side=axis==Direction.Axis.X?Direction.SOUTH:Direction.EAST;
        for(Direction direction:new Direction[]{side,side.getOpposite()}){
            BlockPos shoulder=pos.relative(direction);BlockState state=level.getBlockState(shoulder);
            if(!state.canBeReplaced()&&!(state.getBlock() instanceof RailTrackBlock))return "A line needs a clear bed three blocks wide";
            if(!level.getBlockState(shoulder.below()).isFaceSturdy(level,shoulder.below(),Direction.UP))return "The bed must be firm across all three blocks";
        }
        Direction along=axis==Direction.Axis.X?Direction.EAST:Direction.SOUTH;
        for(Direction direction:new Direction[]{along,along.getOpposite()})for(int dy:new int[]{-1,1}){
            BlockPos neighbour=pos.relative(direction).above(dy);if(!centre(level,neighbour))continue;
            // Adhesion will not take a loaded train up more than one in thirty.
            for(int run=1;run<GRADE_RUN;run++){BlockPos beyond=neighbour.relative(direction,run);if(centre(level,beyond))continue;if(centre(level,beyond.above())||centre(level,beyond.below()))return "Too steep: a railway climbs one block in thirty; this step is only "+run+" beyond the last";break;}
        }
        String missing=Logistics.missing(actor,METRE);
        return missing.isEmpty()?"":"Each metre of line takes 2 metres of steel rail, 2 sleepers, 2 cubic metres of gravel ballast and 8 spikes within reach (carried, in a container, or in a wagon alongside); short by "+missing;
    }
    /** Only the timed world job may commit a metre of line. */
    public static boolean completeLay(LivingEntity actor,BlockPos pos){
        if(!WorldActions.completing(actor)||!(actor.level() instanceof ServerLevel level)||!validateLay(actor,pos).isEmpty()||!Logistics.consume(actor,METRE))return false;
        Direction.Axis axis=axis(actor);var block=IndustrialContent.RAIL_TRACK.get();
        level.setBlock(pos,block.defaultBlockState().setValue(RailTrackBlock.AXIS,axis).setValue(RailTrackBlock.PART,Part.CENTER),3);
        Direction positive=axis==Direction.Axis.X?Direction.SOUTH:Direction.EAST;
        for(Part part:new Part[]{Part.NEG,Part.POS}){
            BlockPos shoulder=pos.relative(part==Part.POS?positive:positive.getOpposite());
            if(level.getBlockState(shoulder).canBeReplaced())level.setBlock(shoulder,block.defaultBlockState().setValue(RailTrackBlock.AXIS,axis).setValue(RailTrackBlock.PART,part),3);
        }
        actor.getPersistentData().remove("survivalRailAxis");return true;
    }
    private static void useRail(PlayerInteractEvent.RightClickBlock event){if(event.getEntity().isSpectator())return;
        if(!(event.getLevel() instanceof ServerLevel level)||event.getHand()!=InteractionHand.MAIN_HAND||!event.getItemStack().is(SurvivalContent.item("steel_rail")))return;
        if(level.getBlockEntity(event.getPos())!=null)return;
        BlockPos target=level.getBlockState(event.getPos()).canBeReplaced()?event.getPos():event.getPos().relative(event.getFace()==null?Direction.UP:event.getFace());
        event.setCanceled(true);event.getEntity().sendOverlayMessage(Component.literal(WorldActions.start(event.getEntity(),"lay_track",target).message()));
    }

    // ---- Trains ---------------------------------------------------------------------------------------
    public static void place(VehicleEntity vehicle,ServerLevel level,BlockPos centre){
        Direction.Axis axis=level.getBlockState(centre).getValue(RailTrackBlock.AXIS);Direction facing=Direction.fromYRot(vehicle.getYRot());
        if(facing.getAxis()!=axis)facing=axis==Direction.Axis.X?Direction.EAST:Direction.SOUTH;
        vehicle.facing=facing;vehicle.path.clear();vehicle.path.add(centre.immutable());vehicle.headS=0;vehicle.velocity=0;
        Vec3 at=node(centre);vehicle.setPos(at.x,at.y,at.z);vehicle.setYRot(facing.toYRot());
    }
    private static double segment(BlockPos a,BlockPos b){return Math.sqrt(a.distSqr(b));}
    private static double length(List<BlockPos> path){double total=0;for(int i=0;i+1<path.size();i++)total+=segment(path.get(i),path.get(i+1));return total;}
    private static void extendTail(VehicleEntity engine,ServerLevel level,double need){
        var path=engine.path;
        for(int guard=0;guard<256&&length(path)<need;guard++){
            BlockPos last=path.getLast();int dx,dz;
            if(path.size()>1){BlockPos before=path.get(path.size()-2);dx=Integer.signum(last.getX()-before.getX());dz=Integer.signum(last.getZ()-before.getZ());}
            else{dx=-engine.facing.getStepX();dz=-engine.facing.getStepZ();}
            BlockPos more=next(level,last,dx,dz);if(more==null||path.contains(more))return;path.add(more);
        }
    }
    private static void advance(VehicleEntity engine,ServerLevel level,double distance){
        var path=engine.path;engine.headS-=distance;
        while(engine.headS<0){
            BlockPos head=path.getFirst();int dx,dz;
            if(path.size()>1){dx=Integer.signum(head.getX()-path.get(1).getX());dz=Integer.signum(head.getZ()-path.get(1).getZ());}else{dx=engine.facing.getStepX();dz=engine.facing.getStepZ();}
            BlockPos more=next(level,head,dx,dz);
            if(more==null){
                // Nothing stops a train at the end of the line but the ground.
                boolean fast=Math.abs(engine.velocity)>.08;engine.headS=0;
                // A driver working to a named place has the line ahead in view and stops at the last rail; only a hand on the regulator runs off it.
                if(engine.destination!=null){engine.velocity=0;engine.destination=null;for(Entity rider:engine.getPassengers())if(rider instanceof ServerPlayer player)player.sendOverlayMessage(Component.literal("End of the line: the place named is not on it"));break;}
                if(fast){derail(engine,level,"Ran off the end of the line");return;}
                engine.velocity=0;break;
            }
            boolean curve=more.getX()-head.getX()!=dx||more.getZ()-head.getZ()!=dz;
            if(curve&&Math.abs(engine.velocity)>CURVE_LIMIT){derail(engine,level,"Derailed: the train took the curve too fast");return;}
            path.addFirst(more);engine.headS+=segment(more,head);
            int stepX=more.getX()-head.getX(),stepZ=more.getZ()-head.getZ();engine.facing=stepX>0?Direction.EAST:stepX<0?Direction.WEST:stepZ>0?Direction.SOUTH:Direction.NORTH;
        }
        double need=engine.headS+offset(level,engine,engine.cars.size())+1.5;
        while(path.size()>2&&length(path.subList(0,path.size()-1))>=need)path.removeLast();
    }
    private static void reverse(VehicleEntity engine,ServerLevel level,double distance){
        var path=engine.path;engine.headS+=distance;double need=engine.headS+offset(level,engine,engine.cars.size());
        extendTail(engine,level,need);double total=length(path);
        if(total<need){engine.headS=Math.max(0,total-offset(level,engine,engine.cars.size()));engine.velocity=0;}
        while(path.size()>2&&engine.headS>=segment(path.get(0),path.get(1))){engine.headS-=segment(path.get(0),path.get(1));path.removeFirst();}
    }
    private static void derail(VehicleEntity engine,ServerLevel level,String what){
        engine.velocity=0;engine.throttle=0;engine.destination=null;engine.headS=0;engine.damage+=25;
        for(Entity rider:engine.getPassengers()){rider.hurtServer(level,level.damageSources().flyIntoWall(),4);if(rider instanceof ServerPlayer player)player.sendOverlayMessage(Component.literal(what));}
        for(UUID id:engine.cars)if(level.getEntity(id) instanceof VehicleEntity car)car.damage+=10;
        level.sendParticles(ParticleTypes.LARGE_SMOKE,engine.getX(),engine.getY()+1,engine.getZ(),30,1,1,1,.02);
        if(engine.damage>=engine.spec().health())engine.wreck(level);
    }
    /** Positions the engine and every loaded car along the consist's path. */
    private static void layout(VehicleEntity engine,ServerLevel level){
        var path=engine.path;extendTail(engine,level,engine.headS+offset(level,engine,engine.cars.size()));
        for(int index=0;index<=engine.cars.size();index++){
            VehicleEntity vehicle=index==0?engine:level.getEntity(engine.cars.get(index-1)) instanceof VehicleEntity car?car:null;if(vehicle==null)continue;
            double s=engine.headS+offset(level,engine,index),walked=0;Vec3 at=node(path.getLast());Vec3 ahead=null;
            for(int i=0;i+1<path.size();i++){
                double part=segment(path.get(i),path.get(i+1));
                if(s<=walked+part){Vec3 a=node(path.get(i)),b=node(path.get(i+1));at=a.lerp(b,(s-walked)/part);ahead=a.subtract(b);break;}
                walked+=part;
            }
            if(ahead==null&&path.size()>1)ahead=node(path.get(path.size()-2)).subtract(node(path.getLast()));
            float yaw=ahead==null||ahead.horizontalDistanceSqr()<1e-6?engine.facing.toYRot():(float)(Mth.atan2(-ahead.x,ahead.z)*Mth.RAD_TO_DEG);
            vehicle.setPos(at.x,at.y,at.z);vehicle.setYRot(yaw);vehicle.setDeltaMovement(Vec3.ZERO);
        }
    }
    public static long fuelAboard(VehicleEntity engine){return Ledger.count(engine.cargo,"minecraft:coal")+Ledger.count(engine.cargo,"minecraft:charcoal")+Ledger.count(engine.cargo,"spawn_9b5fbb9:coke");}
    /** A 25 kg sack of fuel takes a light engine about 1.8 km: fourteen kilograms to the kilometre, fifty pounds a mile. */
    private static boolean stoke(VehicleEntity engine){
        if(Ledger.take(engine.cargo,"spawn_9b5fbb9:coke",1)>0){engine.fuelBurn+=1950;return true;}
        if(Ledger.take(engine.cargo,"minecraft:coal",1)>0){engine.fuelBurn+=1800;return true;}
        if(Ledger.take(engine.cargo,"minecraft:charcoal",1)>0){engine.fuelBurn+=1650;return true;}
        return false;
    }
    /** Gross weight of the train in tonnes. */
    public static double tonnes(VehicleEntity engine,ServerLevel level){
        double kilograms=engine.spec().tare()+Ledger.mass(engine.cargo);
        for(UUID id:engine.cars)if(level.getEntity(id) instanceof VehicleEntity car)kilograms+=car.spec().tare()+Ledger.mass(car.cargo);
        return kilograms/1000;
    }
    private static boolean waterBeside(ServerLevel level,BlockPos centre){
        for(BlockPos pos:BlockPos.betweenClosed(centre.offset(-4,-3,-4),centre.offset(4,1,4)))if(level.getFluidState(pos).is(FluidTags.WATER))return true;return false;
    }
    public static void tick(VehicleEntity engine,ServerLevel level){
        if(engine.path.isEmpty()||!centre(level,engine.path.getFirst())){
            BlockPos track=trackAt(level,engine.blockPosition());if(track==null){engine.velocity=0;return;}
            float yaw=engine.getYRot();place(engine,level,track);engine.setYRot(yaw);
            Direction facing=Direction.fromYRot(yaw);if(facing.getAxis()==level.getBlockState(track).getValue(RailTrackBlock.AXIS))engine.facing=facing;
        }
        var spec=engine.spec();Entity driver=engine.getFirstPassenger();double command=0;
        boolean capable=driver instanceof LivingEntity living&&SurvivalApi.isHumanoid(living)&&(SurvivalApi.exempt(living)||SurvivalApi.state(living).mental()>.05);
        // Steam: a fire tended from the footplate brings a cold boiler to working pressure in two hours and to full pressure in four,
        // and burns ten kilograms an hour standing; untended, it dies back.
        if(capable&&engine.water>0&&(engine.fuelBurn>0||fuelAboard(engine)>0)){
            engine.steam=Math.min(1,engine.steam+.00025);if(Math.abs(engine.velocity)<.002)engine.fuelBurn-=.72;
            while(engine.fuelBurn<=0)if(!stoke(engine)){engine.fuelBurn=0;break;}
        }else engine.steam=Math.max(0,engine.steam-.0002);
        double tonnes=tonnes(engine,level),limit=engine.immobileReason().isEmpty()?spec.speed()*Math.min(1,150/tonnes):0;
        if(engine.path.size()>1&&engine.path.get(0).getY()!=engine.path.get(1).getY())limit*=.6*Math.min(1,60/tonnes);
        if(capable&&engine.destination==null&&driver instanceof ServerPlayer player){var input=player.getLastClientInput();command=(input.forward()?1:0)-(input.backward()?1:0);}
        else if(capable&&engine.destination!=null){
            // Measured along the track, not as the crow flies: a place beside the line is reached when the engine is
            // abreast of it. By the straight distance it was never within reach, and the train shuttled past it for good.
            Vec3 offset=engine.destination.subtract(engine.position()),heading=Vec3.directionFromRotation(0,engine.getYRot());
            double along=offset.x*heading.x+offset.z*heading.z,distance=Math.abs(along);
            if(distance<3&&Math.abs(engine.velocity)<.02)engine.destination=null;
            else{
                // The driver shuts off and brakes with the stopping distance in hand.
                double allowed=Math.sqrt(2*BRAKE*.8*Math.max(0,distance-3))+.008;
                command=(along>=0?1:-1)*(limit<=0?1:Math.min(1,allowed/limit));
            }
        }else if(!capable)engine.destination=null;
        engine.throttle=command;
        double target=command*limit,rate=command==0?.0001:target*engine.velocity<0||Math.abs(target)<Math.abs(engine.velocity)?BRAKE:ACCELERATION*Math.min(1,80/tonnes);
        engine.velocity+=Mth.clamp(target-engine.velocity,-rate,rate);
        if(Math.abs(engine.velocity)<.002&&command==0)engine.velocity=0;
        double step=engine.velocity;
        if(step>0)advance(engine,level,step);else if(step<0)reverse(engine,level,-step);
        if(engine.isRemoved())return;
        layout(engine,level);
        if(step!=0){
            // Seven litres of water boiled for every kilogram of coal burned.
            double work=Math.abs(step)*(1+.15*engine.cars.size());
            engine.water=Math.max(0,engine.water-.1*work);engine.fuelBurn-=work;engine.lubricate(level,Math.abs(step));
            if(engine.isRemoved())return;
            while(engine.fuelBurn<=0)if(!stoke(engine)){engine.fuelBurn=0;engine.velocity=0;break;}
            if(engine.tickCount%4==0){Vec3 funnel=engine.position().add(Vec3.directionFromRotation(0,engine.getYRot()).scale(2.76));level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,funnel.x,engine.getY()+3.7,funnel.z,1,.08,.1,.08,.01);}
        }else if(engine.tickCount%20==0&&engine.water<TENDER_WATER&&waterBeside(level,engine.blockPosition()))engine.water=Math.min(TENDER_WATER,engine.water+60);
    }
    private static ActionResult ok(String text){return new ActionResult(true,text);}
    private static ActionResult fail(String text){return new ActionResult(false,text);}
    private static Vec3 tail(VehicleEntity engine){
        if(engine.cars.isEmpty()||!(engine.level() instanceof ServerLevel level))return engine.position();
        return level.getEntity(engine.cars.getLast()) instanceof VehicleEntity car?car.position():engine.position();
    }
    public static ActionResult couple(LivingEntity actor,VehicleEntity vehicle){
        if(vehicle==null||!vehicle.spec().rail()||!(vehicle.level() instanceof ServerLevel level))return fail("Stand by rolling stock on the track");
        if(actor.getVehicle()!=vehicle&&actor.distanceToSqr(vehicle)>36)return fail("Stand within six blocks of the coupling");
        VehicleEntity engine=null,car=null;
        if(vehicle.spec().kind()==Kind.ENGINE){
            engine=vehicle;Vec3 end=tail(engine);double best=81;
            for(VehicleEntity other:level.getEntitiesOfClass(VehicleEntity.class,new net.minecraft.world.phys.AABB(end,end).inflate(10)))
                if(other.spec().kind()==Kind.CAR&&other.coupledTo.isEmpty()&&other.distanceToSqr(end)<best){best=other.distanceToSqr(end);car=other;}
        }else{
            car=vehicle;if(!car.coupledTo.isEmpty())return fail("Already coupled");double best=81;
            for(VehicleEntity other:level.getEntitiesOfClass(VehicleEntity.class,car.getBoundingBox().inflate(120)))
                if(other.spec().kind()==Kind.ENGINE&&tail(other).distanceToSqr(car.position())<best){best=tail(other).distanceToSqr(car.position());engine=other;}
        }
        if(engine==null||car==null)return fail("Bring the engine (or the end of its train) within a wagon's length of the uncoupled car");
        if(engine.cars.size()>=12)return fail("Twelve cars is all one engine can start");
        if(Math.abs(engine.velocity)>.01)return fail("Stop the train before coupling");
        engine.cars.add(car.getUUID());car.coupledTo=engine.getUUID().toString();car.path.clear();layout(engine,level);
        return ok("Coupled: "+engine.cars.size()+" cars behind the engine");
    }
    public static ActionResult uncouple(LivingEntity actor,VehicleEntity vehicle){
        if(vehicle==null||!vehicle.spec().rail()||!(vehicle.level() instanceof ServerLevel level))return fail("Stand by rolling stock on the track");
        if(actor.getVehicle()!=vehicle&&actor.distanceToSqr(vehicle)>36)return fail("Stand within six blocks of the coupling");
        VehicleEntity engine=vehicle.spec().kind()==Kind.ENGINE?vehicle:engineOf(vehicle,level);
        if(engine==null||engine.cars.isEmpty())return fail("Nothing is coupled");
        if(Math.abs(engine.velocity)>.01)return fail("Stop the train before uncoupling");
        int from=vehicle==engine?engine.cars.size()-1:engine.cars.indexOf(vehicle.getUUID());if(from<0)return fail("Nothing is coupled");
        int released=0;
        while(engine.cars.size()>from){UUID id=engine.cars.removeLast();released++;if(level.getEntity(id) instanceof VehicleEntity car){car.coupledTo="";BlockPos track=trackAt(level,car.blockPosition());if(track!=null){float yaw=car.getYRot();Vec3 at=car.position();place(car,level,track);car.setPos(at.x,at.y,at.z);car.setYRot(yaw);}}}
        return ok("Uncoupled "+released+"; "+engine.cars.size()+" cars remain");
    }
    private static VehicleEntity engineOf(VehicleEntity car,ServerLevel level){
        try{return car.coupledTo.isEmpty()?null:level.getEntity(UUID.fromString(car.coupledTo)) instanceof VehicleEntity engine?engine:null;}catch(IllegalArgumentException malformed){return null;}
    }
    public static void removed(VehicleEntity vehicle,ServerLevel level){
        if(vehicle.spec().kind()==Kind.ENGINE){for(UUID id:vehicle.cars)if(level.getEntity(id) instanceof VehicleEntity car)car.coupledTo="";vehicle.cars.clear();}
        else if(vehicle.spec().kind()==Kind.CAR){VehicleEntity engine=engineOf(vehicle,level);if(engine!=null)engine.cars.remove(vehicle.getUUID());vehicle.coupledTo="";}
    }
}
