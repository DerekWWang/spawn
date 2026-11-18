package dev.qube.spawn_9b5fbb9.transport;

import java.util.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.qube.spawn_9b5fbb9.industry.IndustrialContent;
import dev.qube.spawn_9b5fbb9.industry.Industry;
import dev.qube.spawn_9b5fbb9.industry.Ledger;
import dev.qube.spawn_9b5fbb9.industry.WorksEntity;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi.ActionResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import static net.minecraft.commands.Commands.*;

/** Vehicle definitions and the controller-neutral logistics API. Draught vehicles move only as fast as
 * their team, load and the ground allow; engines burn real fuel; rolling stock stays on laid track. */
public final class Vehicles {
    private Vehicles(){}
    public enum Kind {DRAFT,MOTOR,ENGINE,CAR,ARTILLERY}
    /** @param velocity muzzle or release speed in blocks per tick (20 m/s each); @param reloadTicks crew-drill ticks per round;
     *  @param crew the full detachment; @param blockPower masonry-breaking budget per shot; @param powderCharges kilogram bags per round;
     *  @param drag quadratic air-drag coefficient per metre, from the shot's mass and frontal area. */
    public record Artillery(double velocity,int reloadTicks,int crew,double spreadDegrees,float entityDamage,double blockPower,boolean highArc,
                            int powderCharges,Set<String> ammunition,double burstChance,float minPitch,float maxPitch,double drag){}
    /** @param capacity payload in kilograms; @param tare unladen weight in kilograms; @param teamMin horse-equivalents needed to move at all;
     *  @param speed top speed in blocks per tick on a made road (0.1 is 7.2 km/h). */
    public record Spec(String id,Kind kind,float width,float height,float length,int seats,long capacity,long tare,int teamMin,int teamMax,double speed,float turn,float health,Artillery artillery){
        public boolean rail(){return kind==Kind.ENGINE||kind==Kind.CAR;}
        public boolean drawn(){return teamMax>0;}
        public EntityDimensions dimensions(){return EntityDimensions.fixed(width,height);}
    }
    public static final Map<String,Spec> SPECS=new LinkedHashMap<>();
    private static void spec(Spec spec){SPECS.put(spec.id,spec);}
    static {
        String M="spawn_9b5fbb9:";
        // One horse, half a tonne, a brisk walk; trotting only when light.
        spec(new Spec("cart",Kind.DRAFT,1.9f,1.5f,3.0f,1,500,250,1,1,.14,3.2f,30,null));
        // Two tonnes behind a four-horse team at walking pace: the unit of pre-railway freight.
        spec(new Spec("wagon",Kind.DRAFT,2f,1.9f,4.2f,2,2000,700,2,4,.12,2.2f,50,null));
        // A light two-horse car for a driver and one fighter: a gallop on open level ground, useless in rough country.
        spec(new Spec("chariot",Kind.DRAFT,1.7f,1.4f,2.4f,2,100,60,2,2,.42,4.5f,24,null));
        // A three-ton lorry of the 1910s: 25 km/h on a made road.
        spec(new Spec("motor_truck",Kind.MOTOR,2.3f,2.5f,5.5f,2,3000,2900,0,0,.35,2.4f,80,null));
        // Twenty-four tonnes of tank engine; the bunker carries its coal.
        spec(new Spec("locomotive",Kind.ENGINE,2.7f,3.6f,6.4f,2,5000,23900,0,0,.6,0,200,null));
        spec(new Spec("rail_wagon",Kind.CAR,2.5f,2.3f,5.0f,0,10000,4750,0,0,0,0,100,null));
        spec(new Spec("rail_carriage",Kind.CAR,2.7f,3.3f,6.0f,8,1000,7200,0,0,0,0,100,null));
        // Bronze six-pounder: 440 m/s from a half-kilogram charge, two rounds a minute from a drilled detachment of eight.
        spec(new Spec("cannon",Kind.ARTILLERY,1.9f,1.5f,3.0f,1,300,730,4,6,.10,2f,60,new Artillery(22,600,8,.5,36,9,false,1,Set.of(M+"round_shot"),.002,-5,15,.00076)));
        // Wrought-iron siege bombard on its timber bed: a 40 kg stone over ten kilograms of powder, a few shots a day, and it may burst.
        spec(new Spec("bombard",Kind.ARTILLERY,1.8f,1.6f,3.4f,1,600,5000,6,10,.03,1f,120,new Artillery(11,4800,8,1.2,60,26,false,20,Set.of(M+"stone_shot"),.03,0,20,.00053)));
        // Counterweight trebuchet: built where it will shoot, a dozen men winding down the arm for each stone.
        spec(new Spec("trebuchet",Kind.ARTILLERY,3.4f,3.4f,5.0f,1,600,18000,0,0,0,1.5f,160,new Artillery(2.5,1000,12,1.0,40,13,true,0,Set.of(M+"stone_shot"),0,45,78,.00053)));
        // Rifled steel breech-loader of the 1870s: an 8 kg shell at 455 m/s over two kilograms of black powder, relaid after every round.
        spec(new Spec("field_gun",Kind.ARTILLERY,1.9f,1.7f,3.4f,1,300,1200,4,6,.12,2.5f,80,new Artillery(22.75,600,6,.15,30,8,false,4,Set.of(M+"explosive_shell"),0,-5,18,.000143)));
    }
    public static void init(){NeoForge.EVENT_BUS.addListener(Vehicles::commands);NeoForge.EVENT_BUS.addListener(Vehicles::died);}
    /** The dead leave the harness and the crew of the vehicles around them, so the place can be filled again once the body is gone. */
    private static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event){
        var dead=event.getEntity();if(!(dead.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        for(VehicleEntity vehicle:level.getEntitiesOfClass(VehicleEntity.class,dead.getBoundingBox().inflate(24))){vehicle.team.remove(dead.getUUID());vehicle.crew.remove(dead.getUUID().toString());}
        dead.getPersistentData().remove("survivalHitched");
    }

    public static VehicleEntity spawn(ServerLevel level,String kind,Vec3 pos,float yaw){
        Spec spec=SPECS.get(kind);if(spec==null)return null;
        VehicleEntity vehicle=IndustrialContent.VEHICLE.get().create(level,EntitySpawnReason.TRIGGERED);if(vehicle==null)return null;
        vehicle.setKind(kind);vehicle.setPos(pos.x,pos.y,pos.z);vehicle.setYRot(yaw);vehicle.yRotO=yaw;
        vehicle.grease=GREASE_INTERVAL;
        if(spec.rail()){BlockPos track=Railway.trackAt(level,BlockPos.containing(pos));if(track==null)return null;Railway.place(vehicle,level,track);}
        level.addFreshEntity(vehicle);return vehicle;
    }
    /** Metres a vehicle runs on one litre of grease or oil before its bearings want more. */
    public static final double GREASE_INTERVAL=50000;
    /** What a team in harness will eat from the load, by weight: a working horse needs ten kilograms a day. */
    public static final List<String> FODDER=List.of("minecraft:hay_block","minecraft:wheat","spawn_9b5fbb9:maize","minecraft:carrot","minecraft:potato","minecraft:beetroot","minecraft:apple","minecraft:wheat_seeds");
    /** Draught power of one animal relative to a horse; zero means it cannot be harnessed. */
    public static double draught(Entity animal){
        if(!(animal instanceof Mob mob)||!mob.isAlive()||mob.isBaby())return 0;
        if(animal instanceof Camel)return 1;
        if(animal instanceof AbstractHorse horse)return !horse.isTamed()?0:animal instanceof Donkey?.7:1;
        if(animal instanceof AbstractCow)return 1.4;
        return 0;
    }
    /** Oxen pull more but walk slower than horses, mules and camels. */
    public static double pace(Entity animal){return animal instanceof AbstractCow?.6:1;}

    private static ActionResult ok(String text){return new ActionResult(true,text);}
    private static ActionResult fail(String text){return new ActionResult(false,text);}
    /** Stops a vehicle under way. A train does not stop for being told to: the driver brakes it to a stand, and nobody on the lineside can. */
    public static ActionResult halt(LivingEntity actor,VehicleEntity vehicle){
        if(vehicle==null)return fail("No vehicle within six blocks");
        if(vehicle.getFirstPassenger()!=null&&actor.getVehicle()!=vehicle)return fail("Only those aboard can stop it");
        boolean rolling=vehicle.spec().rail()&&Math.abs(vehicle.velocity)>.02&&vehicle.getFirstPassenger()!=null;
        vehicle.destination=rolling?vehicle.position():null;vehicle.throttle=0;
        return ok(rolling?"Braking to a stand":"Halted");
    }
    private static String reach(LivingEntity actor,VehicleEntity vehicle){
        if(vehicle==null||!vehicle.isAlive())return "No vehicle within six blocks";
        if(!(actor.level() instanceof ServerLevel)||vehicle.level()!=actor.level()||!actor.isAlive()||!SurvivalApi.isHumanoid(actor))return "A living humanoid in the same world is required";
        if(actor.getVehicle()!=vehicle&&actor.distanceToSqr(vehicle)>36)return "Stand within six blocks of the vehicle";
        if(SurvivalApi.state(actor).manipulation()<=.05)return "Your body cannot handle cargo";
        return "";
    }
    public static VehicleEntity nearest(LivingEntity actor){
        if(actor.getVehicle() instanceof VehicleEntity ridden)return ridden;
        VehicleEntity found=null;double best=36.01;
        for(VehicleEntity vehicle:actor.level().getEntitiesOfClass(VehicleEntity.class,actor.getBoundingBox().inflate(6))){double distance=actor.distanceToSqr(vehicle);if(distance<best){best=distance;found=vehicle;}}
        return found;
    }
    public static ActionResult load(LivingEntity actor,VehicleEntity vehicle,InteractionHand hand,boolean everything){
        String invalid=reach(actor,vehicle);if(!invalid.isEmpty())return fail(invalid);
        ItemStack held=actor.getItemInHand(hand);if(held.isEmpty())return fail("Hold the goods to load");
        if(held.isDamaged()||held.getMaxStackSize()==1&&held.isDamageableItem())return fail("Bulk cargo is for goods and materials, not worn equipment");
        // A load is counted by kind and comes off as new stock of that kind, so nothing goes aboard that is more than its kind.
        String unfit=Ledger.unfit(held);if(!unfit.isEmpty())return fail(unfit);
        String id=Ledger.id(held);double each=Ledger.mass(id),room=vehicle.spec().capacity()-Ledger.mass(vehicle.cargo);
        if(room<each)return fail("The "+vehicle.kind().replace('_',' ')+" cannot carry more ("+Math.round(Ledger.mass(vehicle.cargo))+" of "+vehicle.spec().capacity()+" kg aboard)");
        long moved=(long)Math.min(Math.floor(room/each),held.getCount());Ledger.add(vehicle.cargo,id,moved);held.shrink((int)moved);room-=moved*each;
        if(everything&&actor instanceof Player player)for(int slot=0;slot<player.getInventory().getContainerSize()&&room>=each;slot++){
            ItemStack other=player.getInventory().getItem(slot);if(other.isEmpty()||other.isDamaged()||!Ledger.id(other).equals(id)||!Ledger.unfit(other).isEmpty())continue;
            long part=(long)Math.min(Math.floor(room/each),other.getCount());Ledger.add(vehicle.cargo,id,part);other.shrink((int)part);moved+=part;room-=part*each;
        }
        return ok("Loaded "+moved+"; cargo "+Math.round(Ledger.mass(vehicle.cargo))+"/"+vehicle.spec().capacity()+" kg");
    }
    public static ActionResult unload(LivingEntity actor,VehicleEntity vehicle,String itemId,int count){
        String invalid=reach(actor,vehicle);if(!invalid.isEmpty())return fail(invalid);
        if(vehicle.cargo.isEmpty())return fail("The cargo bed is empty");
        String id=itemId==null||itemId.isEmpty()?vehicle.cargo.keySet().iterator().next():itemId.indexOf(':')<0?IndustrialContent.MODID+":"+itemId:itemId;
        if(!vehicle.cargo.containsKey(id)&&itemId!=null&&itemId.indexOf(':')<0&&vehicle.cargo.containsKey("minecraft:"+itemId))id="minecraft:"+itemId;
        if(!vehicle.cargo.containsKey(id))return fail("No such cargo aboard: "+Ledger.describe(vehicle.cargo));
        long moved=0;int limit=actor instanceof Player?Math.max(1,count):Math.min(Math.max(1,count),256);
        while(moved<limit&&vehicle.cargo.containsKey(id)){
            ItemStack stack=Ledger.withdrawStack(vehicle.cargo,id);if(stack.isEmpty())break;
            if(stack.getCount()>limit-moved){Ledger.add(vehicle.cargo,id,stack.getCount()-(limit-moved));stack.setCount((int)(limit-moved));}
            moved+=stack.getCount();SurvivalApi.give(actor,stack);
        }
        return ok("Unloaded "+moved+" "+Identifier.parse(id).getPath());
    }
    /** Hands the cargo a works can use straight across from the vehicle bed. */
    public static ActionResult deliver(LivingEntity actor,VehicleEntity vehicle){
        String invalid=reach(actor,vehicle);if(!invalid.isEmpty())return fail(invalid);
        WorksEntity works=worksBeside(vehicle);if(works==null)return fail("Draw up within six blocks of a works");
        long moved=Industry.depositFrom(works,vehicle.cargo);
        return moved>0?ok("Delivered "+moved+" units to the "+works.station().replace('_',' ')+"; "+Industry.status(works)):fail("Nothing aboard is used by that works' selected recipe");
    }
    /** Takes finished goods from a works onto the vehicle. */
    public static ActionResult pickup(LivingEntity actor,VehicleEntity vehicle){
        String invalid=reach(actor,vehicle);if(!invalid.isEmpty())return fail(invalid);
        WorksEntity works=worksBeside(vehicle);if(works==null)return fail("Draw up within six blocks of a works");
        double room=vehicle.spec().capacity()-Ledger.mass(vehicle.cargo);long moved=0;
        for(String id:new ArrayList<>(works.products.keySet())){
            double each=Ledger.mass(id);if(id.startsWith("vehicle:")||room<each)continue;long part=(long)Math.min(Math.floor(room/each),works.products.get(id));
            Ledger.take(works.products,id,part);Ledger.add(vehicle.cargo,id,part);room-=part*each;moved+=part;
        }
        if(moved>0)works.setChanged();
        return moved>0?ok("Took on "+moved+" finished units; cargo "+Math.round(Ledger.mass(vehicle.cargo))+"/"+vehicle.spec().capacity()+" kg"):fail("No finished goods to take on, or no room");
    }
    private static WorksEntity worksBeside(VehicleEntity vehicle){
        WorksEntity found=null;double best=36.01;var level=vehicle.level();
        for(var pos:BlockPos.betweenClosed(vehicle.blockPosition().offset(-6,-3,-6),vehicle.blockPosition().offset(6,3,6))){
            if(!level.hasChunkAt(pos))continue;double distance=vehicle.distanceToSqr(Vec3.atCenterOf(pos));
            if(distance<best&&level.getBlockEntity(pos) instanceof WorksEntity works){found=works;best=distance;}
        }
        return found;
    }
    public static ActionResult hitch(LivingEntity actor,VehicleEntity vehicle,Entity animal){
        String invalid=reach(actor,vehicle);if(!invalid.isEmpty())return fail(invalid);
        Spec spec=vehicle.spec();if(!spec.drawn())return fail("A "+spec.id().replace('_',' ')+" is not drawn by animals");
        if(animal==null||animal.level()!=vehicle.level()||animal.distanceToSqr(vehicle)>64)return fail("Bring the animal within eight blocks");
        if(draught(animal)<=0)return fail("Only tamed adult horses, mules, donkeys, camels or oxen can be harnessed");
        if(animal.isVehicle())return fail("A ridden animal cannot be put in harness");
        if(vehicle.team.contains(animal.getUUID()))return fail("Already in this team");
        if(animal.getPersistentData().contains("survivalHitched"))return fail("That animal is harnessed elsewhere");
        if(vehicle.team.size()>=spec.teamMax())return fail("The harness takes at most "+spec.teamMax());
        vehicle.hitch((Mob)animal);
        return ok("Harnessed ("+vehicle.team.size()+"/"+spec.teamMax()+"; needs "+spec.teamMin()+" to move)");
    }
    public static ActionResult unhitch(LivingEntity actor,VehicleEntity vehicle){
        String invalid=reach(actor,vehicle);if(!invalid.isEmpty())return fail(invalid);
        if(vehicle.team.isEmpty())return fail("No team in harness");
        int released=vehicle.team.size();vehicle.releaseTeam();return ok("Released "+released+" animals");
    }
    public static ActionResult board(LivingEntity actor,VehicleEntity vehicle){
        String invalid=reach(actor,vehicle);if(!invalid.isEmpty())return fail(invalid);
        if(vehicle.getPassengers().size()>=vehicle.spec().seats())return fail("No free seat");
        return actor.startRiding(vehicle,true,true)?ok("Aboard the "+vehicle.kind().replace('_',' ')):fail("Could not board");
    }
    /** Sets a destination for the driver. Rail engines run along their track towards it; road vehicles steer straight for it. */
    public static ActionResult drive(LivingEntity actor,VehicleEntity vehicle,Vec3 destination){
        String invalid=reach(actor,vehicle);if(!invalid.isEmpty())return fail(invalid);
        if(vehicle.spec().kind()==Kind.CAR||vehicle.spec().speed()<=0)return fail("That cannot be driven");
        if(vehicle.getFirstPassenger()!=actor){ActionResult aboard=vehicle.getFirstPassenger()==null?board(actor,vehicle):fail("Someone else has the reins");if(!aboard.success())return aboard;}
        if(vehicle.getFirstPassenger()!=actor)return fail("Take the driver's seat first");
        String blocked=vehicle.immobileReason();if(!blocked.isEmpty())return fail(blocked);
        vehicle.destination=destination;return ok("Driving to "+BlockPos.containing(destination).toShortString());
    }
    public static String status(VehicleEntity vehicle){
        Spec spec=vehicle.spec();var out=new StringBuilder(spec.id().replace('_',' '));
        out.append(" | cargo ").append(Math.round(Ledger.mass(vehicle.cargo))).append('/').append(spec.capacity()).append(" kg: ").append(Ledger.describe(vehicle.cargo));
        if(spec.drawn())out.append(" | team ").append(vehicle.team.size()).append('/').append(spec.teamMax()).append(" (needs ").append(spec.teamMin()).append(')').append(vehicle.teamHunger>0?", unfed for "+vehicle.teamHunger+" feeds":"");
        if(spec.kind()==Kind.MOTOR)out.append(" | petrol ").append(Ledger.count(vehicle.cargo,"spawn_9b5fbb9:petrol")).append(" L, alcohol ").append(Ledger.count(vehicle.cargo,"spawn_9b5fbb9:fuel_alcohol")).append(" L");
        if(spec.kind()==Kind.ENGINE)out.append(" | steam ").append(Math.round(vehicle.steam*100)).append("% | water ").append((long)vehicle.water).append("/3000 L | cars ").append(vehicle.cars.size());
        String blocked=vehicle.immobileReason();if(!blocked.isEmpty())out.append(" | ").append(blocked);
        if(spec.artillery()!=null)out.append(" | ").append(dev.qube.spawn_9b5fbb9.warfare.Siege.status(vehicle));
        out.append(" | condition ").append(Math.round(100*(1-vehicle.damage/spec.health()))).append('%');
        return out.toString();
    }

    @FunctionalInterface private interface ActorLookup{LivingEntity get(CommandContext<CommandSourceStack> context)throws CommandSyntaxException;}
    private static void commands(RegisterCommandsEvent event){
        var own=literal("vehicle");tree(own,c->c.getSource().getPlayerOrException());event.getDispatcher().register(literal("survival").then(own));
        var controlled=literal("vehicle");tree(controlled,c->living(EntityArgument.getEntity(c,"actor")));
        event.getDispatcher().register(literal("survival").then(literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("actor",EntityArgument.entity()).then(controlled))));
        event.getDispatcher().register(literal("survival").then(literal("deploy").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("kind",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(SPECS.keySet(),b)).executes(c->{
            var source=c.getSource();VehicleEntity vehicle=spawn(source.getLevel(),StringArgumentType.getString(c,"kind"),source.getPosition(),source.getRotation().y);
            return say(source,vehicle==null?fail("Unknown vehicle, or rolling stock deployed away from track"):ok("Deployed "+vehicle.kind()));
        }))));
    }
    private static LivingEntity living(Entity entity)throws CommandSyntaxException{
        if(entity instanceof LivingEntity living&&SurvivalApi.isHumanoid(living))return living;
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Living humanoid required")).create();
    }
    private static void tree(ArgumentBuilder<CommandSourceStack,?> node,ActorLookup actor){
        node.then(literal("status").executes(c->{var v=nearest(actor.get(c));return say(c.getSource(),v==null?fail("No vehicle within six blocks"):ok(status(v)));}));
        node.then(literal("load").executes(c->{var a=actor.get(c);return say(c.getSource(),load(a,nearest(a),InteractionHand.MAIN_HAND,true));}));
        node.then(literal("unload").executes(c->{var a=actor.get(c);return say(c.getSource(),unload(a,nearest(a),"",64));})
            .then(argument("item",StringArgumentType.string()).executes(c->{var a=actor.get(c);return say(c.getSource(),unload(a,nearest(a),StringArgumentType.getString(c,"item"),64));})
                .then(argument("count",IntegerArgumentType.integer(1,6912)).executes(c->{var a=actor.get(c);return say(c.getSource(),unload(a,nearest(a),StringArgumentType.getString(c,"item"),IntegerArgumentType.getInteger(c,"count")));}))));
        node.then(literal("deliver").executes(c->{var a=actor.get(c);return say(c.getSource(),deliver(a,nearest(a)));}));
        node.then(literal("pickup").executes(c->{var a=actor.get(c);return say(c.getSource(),pickup(a,nearest(a)));}));
        node.then(literal("hitch").then(argument("animal",EntityArgument.entity()).executes(c->{var a=actor.get(c);return say(c.getSource(),hitch(a,nearest(a),EntityArgument.getEntity(c,"animal")));})));
        node.then(literal("unhitch").executes(c->{var a=actor.get(c);return say(c.getSource(),unhitch(a,nearest(a)));}));
        node.then(literal("board").executes(c->{var a=actor.get(c);return say(c.getSource(),board(a,nearest(a)));}));
        node.then(literal("leave").executes(c->{var a=actor.get(c);a.stopRiding();return say(c.getSource(),ok("Dismounted"));}));
        node.then(literal("drive").then(argument("pos",BlockPosArgument.blockPos()).executes(c->{var a=actor.get(c);return say(c.getSource(),drive(a,nearest(a),Vec3.atBottomCenterOf(BlockPosArgument.getBlockPos(c,"pos"))));})));
        node.then(literal("halt").executes(c->{var a=actor.get(c);return say(c.getSource(),halt(a,nearest(a)));}));
        node.then(literal("couple").executes(c->{var a=actor.get(c);return say(c.getSource(),Railway.couple(a,nearest(a)));}));
        node.then(literal("uncouple").executes(c->{var a=actor.get(c);return say(c.getSource(),Railway.uncouple(a,nearest(a)));}));
    }
    private static int say(CommandSourceStack source,ActionResult result){
        if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;
    }
}
