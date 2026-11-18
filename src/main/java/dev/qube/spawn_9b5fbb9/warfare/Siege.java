package dev.qube.spawn_9b5fbb9.warfare;

import java.util.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.qube.spawn_9b5fbb9.industry.IndustrialContent;
import dev.qube.spawn_9b5fbb9.industry.Ledger;
import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi.ActionResult;
import dev.qube.spawn_9b5fbb9.transport.VehicleEntity;
import dev.qube.spawn_9b5fbb9.transport.Vehicles;
import dev.qube.spawn_9b5fbb9.transport.Vehicles.Artillery;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import static net.minecraft.commands.Commands.*;

/** Siege ordnance. A piece is served by a crew who load real powder and shot from its ammunition
 * store, lay it on a target, and fire. Shot batters walls cumulatively: timber gives way quickly,
 * masonry after repeated hits, and earth banks soak up cannon fire as they did in history. */
public final class Siege {
    private Siege(){}
    /** 9.8 m/s/s at twenty ticks to the second. */
    public static final double GRAVITY=.0245;
    public static final ResourceKey<DamageType> ARTILLERY=ResourceKey.create(Registries.DAMAGE_TYPE,Identifier.fromNamespaceAndPath(IndustrialContent.MODID,"artillery"));
    private static final String M="spawn_9b5fbb9:";
    /** Accumulated structural damage per world, keyed by block position. */
    private static final Map<ResourceKey<Level>,Map<Long,Batter>> BATTERED=new HashMap<>();
    /** The damage, and the block that took it: what is rebuilt in the same place starts whole. */
    private record Batter(double done,BlockState state){}
    public static void init(){
        NeoForge.EVENT_BUS.addListener(Siege::commands);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event)->BATTERED.clear());
    }
    public static boolean isMunition(ItemStack stack){String id=Ledger.id(stack);return id.equals(M+"powder_charge")||id.equals(M+"round_shot")||id.equals(M+"stone_shot")||id.equals(M+"explosive_shell");}
    public static DamageSource source(ServerLevel level,Entity shot,Entity gunner){return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ARTILLERY),shot,gunner);}

    // ---- Terminal effects -----------------------------------------------------------------------------
    /** How much battering a block absorbs before it gives way. Infinite for bedrock-like blocks. */
    public static double resistance(ServerLevel level,BlockPos pos,BlockState state){
        float hardness=state.getDestroySpeed(level,pos);
        if(hardness<0)return Double.POSITIVE_INFINITY;
        if(state.is(Blocks.OBSIDIAN)||state.is(Blocks.CRYING_OBSIDIAN))return 60;
        // Rammed earth and sand deform instead of shattering: the reason forts grew earthen ramparts.
        if(state.is(BlockTags.DIRT)||state.is(BlockTags.SAND)||state.is(Blocks.GRAVEL)||state.is(Blocks.CLAY)||state.is(Blocks.MUD)||state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.FARMLAND)||state.is(Blocks.DIRT_PATH))return 10;
        if(state.is(BlockTags.LOGS))return 4;
        if(state.is(BlockTags.MINEABLE_WITH_AXE))return 3;
        if(hardness<.6)return .5;
        if(hardness>=5)return 14;
        return 6;
    }
    /** @return true when the shot is spent. */
    public static boolean impact(ServerLevel level,ShotEntity shot,BlockPos pos,Vec3 at){
        Military.shock(level,at,7,3);
        if(shot.explosive){burst(level,shot,at);return true;}
        BlockState state=level.getBlockState(pos);double resistance=resistance(level,pos,state);
        // Bounded by letting the oldest marks go, one at a time, rather than by forgetting a whole siege at once.
        var ledger=BATTERED.computeIfAbsent(level.dimension(),key->new LinkedHashMap<Long,Batter>(){@Override protected boolean removeEldestEntry(Map.Entry<Long,Batter> eldest){return size()>20000;}});
        Batter before=ledger.get(pos.asLong());double done=before!=null&&before.state()==state?before.done():0,left=resistance-done;
        level.sendParticles(ParticleTypes.POOF,at.x,at.y,at.z,12,.4,.4,.4,.03);
        level.playSound(null,at.x,at.y,at.z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,1.2f,1.5f);
        if(shot.power>=left){
            shot.power-=left;ledger.remove(pos.asLong());level.destroyBlockProgress(pos.hashCode(),pos,-1);
            level.destroyBlock(pos,level.getRandom().nextFloat()<.3f,shot.ownerEntity());
            return shot.power<.75;
        }
        if(Double.isInfinite(resistance)){shot.power=0;return true;}
        double total=done+shot.power;ledger.put(pos.asLong(),new Batter(total,state));shot.power=0;
        level.destroyBlockProgress(pos.hashCode(),pos,Mth.clamp((int)(total/resistance*9),0,9));
        return true;
    }
    public static double battered(ServerLevel level,BlockPos pos){
        var ledger=BATTERED.get(level.dimension());Batter mark=ledger==null?null:ledger.get(pos.asLong());
        return mark!=null&&mark.state()==level.getBlockState(pos)?mark.done():0;
    }
    public static void burst(ServerLevel level,ShotEntity shot,Vec3 at){
        Military.shock(level,at,10,6);
        level.explode(shot,source(level,shot,shot.ownerEntity()),null,at.x,at.y,at.z,2.8f,false,Level.ExplosionInteraction.TNT);
    }

    // ---- Gunnery --------------------------------------------------------------------------------------
    /** Height above the muzzle after {@code dx} of horizontal flight at the given elevation, with gravity and quadratic air drag,
     * stepped exactly as {@link ShotEntity} flies. NaN when the shot falls short. */
    public static double heightAt(Artillery art,double pitch,double dx){
        double vx=art.velocity()*Math.cos(pitch),vy=art.velocity()*Math.sin(pitch)-GRAVITY/2,x=0,y=0;
        for(int tick=0;tick<1200;tick++){
            double nx=x+vx,ny=y+vy;if(nx>=dx)return y+(ny-y)*(dx-x)/(nx-x);
            x=nx;y=ny;double slowed=1-art.drag()*Math.sqrt(vx*vx+vy*vy);vx*=slowed;vy=vy*slowed-GRAVITY;
            if(y<-512||vx<1e-4)break;
        }
        return Double.NaN;
    }
    /** Level-ground range at the given elevation. */
    public static double range(Artillery art,double pitch){
        double vx=art.velocity()*Math.cos(pitch),vy=art.velocity()*Math.sin(pitch)-GRAVITY/2,x=0,y=0;
        for(int tick=0;tick<4000;tick++){
            double ny=y+vy;if(ny<0&&tick>0)return x+vx*(y/(y-ny));
            x+=vx;y=ny;double slowed=1-art.drag()*Math.sqrt(vx*vx+vy*vy);vx*=slowed;vy=vy*slowed-GRAVITY;
        }
        return x;
    }
    public static double reach(Artillery art){return range(art,Math.toRadians(art.highArc()?45:Math.min(40,art.maxPitch())));}
    /** Launch elevation in radians for a target dx away and dy above, found by ranging shots on paper within the piece's limits of
     * elevation. NaN when the target is beyond reach; negative infinity when it is too close for the mounting. */
    public static double elevation(Artillery art,double dx,double dy){
        if(dx<1e-6)return Double.NaN;
        // A flat-firing piece raises the muzzle to reach further; a trebuchet's high arc shortens as the release steepens.
        double low=Math.toRadians(art.highArc()?45:art.minPitch()),high=Math.toRadians(art.highArc()?art.maxPitch():Math.min(45,art.maxPitch()));
        double far=art.highArc()?low:high,near=art.highArc()?high:low,atFar=heightAt(art,far,dx),atNear=heightAt(art,near,dx);
        if(Double.isNaN(atFar)||atFar<dy)return Double.NaN;
        if(!Double.isNaN(atNear)&&atNear>dy)return Double.NEGATIVE_INFINITY;
        for(int step=0;step<48;step++){double middle=(far+near)/2,height=heightAt(art,middle,dx);if(!Double.isNaN(height)&&height>=dy)far=middle;else near=middle;}
        return far;
    }
    private static ActionResult ok(String text){return new ActionResult(true,text);}
    private static ActionResult fail(String text){return new ActionResult(false,text);}
    private static boolean capable(LivingEntity actor){
        if(!actor.isAlive()||!SurvivalApi.isHumanoid(actor))return false;
        // The dead look on. A creative operator may serve a piece; a ghost may not.
        if(actor instanceof net.minecraft.world.entity.player.Player player&&(player.isSpectator()||SurvivalEvents.dead(player)))return false;
        if(SurvivalApi.exempt(actor))return true;
        var body=SurvivalApi.state(actor);return body.manipulation()>=.15&&body.mental()>.05;
    }
    private static List<LivingEntity> serving(VehicleEntity gun,ServerLevel level){
        var present=new ArrayList<LivingEntity>();
        if(gun.getFirstPassenger() instanceof LivingEntity gunner&&capable(gunner))present.add(gunner);
        for(var iterator=gun.crew.iterator();iterator.hasNext();){
            String id=iterator.next();Entity found;try{found=level.getEntity(UUID.fromString(id));}catch(IllegalArgumentException malformed){iterator.remove();continue;}
            if(found==null)continue;
            if(!(found instanceof LivingEntity actor)||!actor.isAlive()||actor instanceof net.minecraft.world.entity.player.Player player&&SurvivalEvents.dead(player)){iterator.remove();continue;}
            if(!present.contains(actor)&&actor.distanceToSqr(gun)<=25&&capable(actor))present.add(actor);
        }
        return present;
    }
    public static ActionResult man(LivingEntity actor,VehicleEntity gun){
        if(gun==null||gun.spec().artillery()==null)return fail("No siege piece within reach");
        if(!capable(actor))return fail("A capable humanoid is required to serve the piece");
        if(actor.getVehicle()!=gun&&actor.distanceToSqr(gun)>25)return fail("Stand within five blocks of the piece");
        gun.crew.add(actor.getUUID().toString());
        return ok("Serving the "+gun.kind().replace('_',' ')+": "+gun.crew.size()+" in the crew (works best with "+gun.spec().artillery().crew()+")");
    }
    public static ActionResult standDown(LivingEntity actor,VehicleEntity gun){
        if(gun==null||!gun.crew.remove(actor.getUUID().toString()))return fail("Not in a gun crew here");return ok("Stood down");
    }
    /** Lays the piece on a point. A loaded, served piece then fires and keeps firing while ammunition lasts. */
    public static ActionResult lay(LivingEntity actor,VehicleEntity gun,Vec3 target){
        if(gun==null||gun.spec().artillery()==null)return fail("No siege piece within reach");
        if(actor.getVehicle()!=gun&&actor.distanceToSqr(gun)>64)return fail("Stand within eight blocks of the piece");
        Artillery art=gun.spec().artillery();Vec3 muzzle=muzzle(gun);double dx=target.subtract(muzzle).horizontalDistance();
        double pitch=elevation(art,dx,target.y-muzzle.y)*Mth.RAD_TO_DEG;
        if(Double.isNaN(pitch))return fail("Out of range: this piece reaches about "+(int)reach(art)+" blocks on level ground; move it closer");
        if(Double.isInfinite(pitch))return fail("Too close: the mounting cannot be laid on a target at "+(int)dx+" blocks; move it back");
        gun.fireTarget=target;return ok("Laid on "+BlockPos.containing(target).toShortString()+" at "+(int)dx+" blocks, elevation "+Math.round(pitch)+" degrees");
    }
    public static ActionResult cease(VehicleEntity gun){if(gun==null)return fail("No siege piece within reach");gun.fireTarget=null;return ok("Cease fire");}
    private static Vec3 muzzle(VehicleEntity gun){
        Vec3 heading=Vec3.directionFromRotation(0,gun.getYRot());boolean trebuchet=gun.kind().equals("trebuchet");
        return gun.position().add(0,trebuchet?gun.spec().height()+.6:gun.spec().height()*.75,0).add(heading.scale(trebuchet?0:gun.spec().length()/2+.4));
    }
    private static boolean beginLoad(VehicleEntity gun,Artillery art){
        if(Ledger.count(gun.cargo,M+"powder_charge")<art.powderCharges())return false;
        String chosen=null;
        for(String preferred:new String[]{M+"explosive_shell",M+"round_shot",M+"stone_shot"})if(art.ammunition().contains(preferred)&&Ledger.count(gun.cargo,preferred)>0){chosen=preferred;break;}
        if(chosen==null)return false;
        Ledger.take(gun.cargo,chosen,1);Ledger.take(gun.cargo,M+"powder_charge",art.powderCharges());gun.shot=chosen;gun.charges=art.powderCharges();gun.loadProgress=0;return true;
    }
    public static void tick(VehicleEntity gun,ServerLevel level){
        Artillery art=gun.spec().artillery();if(art==null)return;
        if(!gun.ready&&gun.tickCount%20==0){
            var crew=serving(gun,level);if(crew.isEmpty())return;
            if(gun.shot.isEmpty()&&!beginLoad(gun,art))return;
            double work=0;
            for(LivingEntity hand:crew){
                if(LaborGuard.worldBusy(hand)||LaborGuard.gridBusy(hand)||LaborGuard.forgeBusy(hand))continue;
                if(level.getGameTime()-hand.getPersistentData().getLongOr("survivalManualWorkAt",Long.MIN_VALUE/2)<20)continue;
                LaborGuard.worked(hand);
                if(SurvivalApi.exempt(hand)){work+=20;continue;}
                var body=SurvivalApi.state(hand);work+=20*SkillApi.manualRate(body,Skill.GUNNERY);
                body.hunger=Math.max(0,body.hunger-20*.0008);SurvivalApi.save(hand,body);SurvivalApi.syncFood(hand,body);
            }
            // An under-strength crew serves the piece slowly; extra hands beyond the drill add little.
            gun.loadProgress+=Math.min(work/art.crew(),30);
            if(gun.loadProgress>=art.reloadTicks()){
                gun.ready=true;gun.loadProgress=0;
                if(gun.getFirstPassenger() instanceof ServerPlayer player)player.sendOverlayMessage(Component.literal("Loaded. Look along the barrel and jump to fire."));
            }
        }
        if(!gun.ready)return;
        if(gun.getFirstPassenger() instanceof ServerPlayer player&&gun.fireTarget==null){if(player.getLastClientInput().jump()){var result=fire(player,gun,null);player.sendOverlayMessage(Component.literal(result.message()));}}
        else if(gun.fireTarget!=null&&gun.tickCount%10==0){var crew=serving(gun,level);if(!crew.isEmpty()){var result=fire(crew.getFirst(),gun,gun.fireTarget);if(!result.success())gun.fireTarget=null;}}
    }
    private static boolean fireControl(ServerLevel level,BlockPos centre){
        var computer=IndustrialContent.COMPUTER.get();
        for(BlockPos pos:BlockPos.betweenClosed(centre.offset(-8,-3,-8),centre.offset(8,3,8)))if(level.getBlockState(pos).is(computer))return true;return false;
    }
    public static ActionResult fire(LivingEntity gunner,VehicleEntity gun,Vec3 target){
        if(gun==null||gun.spec().artillery()==null||!(gun.level() instanceof ServerLevel level))return fail("No siege piece within reach");
        Artillery art=gun.spec().artillery();if(!gun.ready)return fail(status(gun));
        if(gunner.getVehicle()!=gun&&gunner.distanceToSqr(gun)>25)return fail("Stand at the piece to fire it");
        double yaw,pitch;
        // Traverse first: the muzzle swings with the piece.
        if(target!=null){Vec3 bearing=target.subtract(gun.position());gun.setYRot((float)(Mth.atan2(-bearing.x,bearing.z)*Mth.RAD_TO_DEG));}
        Vec3 muzzle=muzzle(gun);
        if(target!=null){
            Vec3 offset=target.subtract(muzzle);pitch=elevation(art,offset.horizontalDistance(),offset.y)*Mth.RAD_TO_DEG;
            if(Double.isNaN(pitch)||Double.isInfinite(pitch))return fail("The target cannot be reached from here");
            yaw=Mth.atan2(-offset.x,offset.z)*Mth.RAD_TO_DEG;gun.setYRot((float)yaw);
        }else{yaw=gun.getYRot();pitch=Mth.clamp(-gunner.getXRot(),art.minPitch(),art.maxPitch());}
        ItemStack munition=new ItemStack(Ledger.item(gun.shot));boolean shell=gun.shot.equals(M+"explosive_shell");
        gun.ready=false;gun.shot="";gun.charges=0;gun.shotsFired++;
        // Loose powder and priming do not like rain.
        if(art.powderCharges()>0&&level.isRainingAt(BlockPos.containing(muzzle).above())&&level.getRandom().nextFloat()<.25f){
            level.playSound(null,muzzle.x,muzzle.y,muzzle.z,SoundEvents.DISPENSER_FAIL,SoundSource.PLAYERS,1,.7f);return ok("Misfire: the priming was wet. The charge is spoiled.");
        }
        double skill=SurvivalApi.exempt(gunner)?10:SkillApi.level(gunner,Skill.GUNNERY),sigma=art.spreadDegrees()*(1.6-.045*skill);
        if(fireControl(level,gun.blockPosition()))sigma*=.5;
        yaw+=level.getRandom().nextGaussian()*sigma;pitch+=level.getRandom().nextGaussian()*sigma*.6;
        Vec3 direction=Vec3.directionFromRotation((float)-pitch,(float)yaw);
        ShotEntity shot=IndustrialContent.SHOT.get().create(level,EntitySpawnReason.TRIGGERED);if(shot==null)return fail("The shot could not be launched");
        shot.setPos(muzzle.x,muzzle.y,muzzle.z);Vec3 velocity=direction.scale(art.velocity());
        // Compensates the discrete integration so flight matches the laid elevation.
        shot.launch(munition,new Vec3(velocity.x,velocity.y-GRAVITY/2,velocity.z),art.blockPower(),art.entityDamage(),shell,gunner,art.drag());
        level.addFreshEntity(shot);
        if(art.powderCharges()>0){
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,muzzle.x,muzzle.y,muzzle.z,25,.5,.4,.5,.03);level.sendParticles(ParticleTypes.FLAME,muzzle.x,muzzle.y,muzzle.z,10,.2,.2,.2,.05);
            level.playSound(null,muzzle.x,muzzle.y,muzzle.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,5f,.7f);
        }else level.playSound(null,muzzle.x,muzzle.y,muzzle.z,SoundEvents.CROSSBOW_SHOOT,SoundSource.PLAYERS,3f,.4f);
        var hands=serving(gun,level);if(!hands.contains(gunner))hands.add(gunner);
        for(LivingEntity hand:hands)SkillApi.award(hand,Skill.GUNNERY,60);
        if(art.burstChance()>0&&level.getRandom().nextDouble()<art.burstChance()*(1+gun.shotsFired/40d)){
            Vec3 at=gun.position();gun.wreck(level);level.explode(null,at.x,at.y+.5,at.z,3f,false,Level.ExplosionInteraction.NONE);
            return ok("The barrel burst!");
        }
        return ok("Fired");
    }
    public static String status(VehicleEntity gun){
        Artillery art=gun.spec().artillery();if(art==null)return "";
        if(gun.ready)return "loaded with "+Identifier.parse(gun.shot).getPath().replace('_',' ')+(gun.fireTarget!=null?", laid on "+BlockPos.containing(gun.fireTarget).toShortString():", ready to fire");
        if(!gun.shot.isEmpty())return "loading "+Math.round(100*gun.loadProgress/art.reloadTicks())+"% (crew "+gun.crew.size()+"/"+art.crew()+")";
        boolean stocked=Ledger.count(gun.cargo,M+"powder_charge")>=art.powderCharges();if(stocked){stocked=false;for(String ammo:art.ammunition())stocked|=Ledger.count(gun.cargo,ammo)>0;}
        if(stocked)return "unloaded: ammunition is in the store"+(gun.crew.isEmpty()&&gun.getFirstPassenger()==null?"; man the piece to load it":"; the crew will load the next round");
        var need=new StringJoiner(" or ");for(String ammo:art.ammunition())need.add(Identifier.parse(ammo).getPath().replace('_',' '));
        return "unloaded: put "+(art.powderCharges()>0?art.powderCharges()+" powder charge"+(art.powderCharges()>1?"s":"")+" and ":"")+need+" in its ammunition store, then man it";
    }

    @FunctionalInterface private interface ActorLookup{LivingEntity get(CommandContext<CommandSourceStack> context)throws CommandSyntaxException;}
    private static void commands(RegisterCommandsEvent event){
        var own=literal("siege");tree(own,c->c.getSource().getPlayerOrException());event.getDispatcher().register(literal("survival").then(own));
        var controlled=literal("siege");tree(controlled,c->{if(EntityArgument.getEntity(c,"actor") instanceof LivingEntity living&&SurvivalApi.isHumanoid(living))return living;throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Living humanoid required")).create();});
        event.getDispatcher().register(literal("survival").then(literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("actor",EntityArgument.entity()).then(controlled))));
    }
    private static VehicleEntity piece(LivingEntity actor){
        if(actor.getVehicle() instanceof VehicleEntity ridden&&ridden.spec().artillery()!=null)return ridden;
        VehicleEntity found=null;double best=64.01;
        for(VehicleEntity vehicle:actor.level().getEntitiesOfClass(VehicleEntity.class,actor.getBoundingBox().inflate(8),v->v.spec().artillery()!=null)){double distance=actor.distanceToSqr(vehicle);if(distance<best){best=distance;found=vehicle;}}
        return found;
    }
    private static void tree(ArgumentBuilder<CommandSourceStack,?> node,ActorLookup actor){
        node.then(literal("man").executes(c->{var a=actor.get(c);return say(c.getSource(),man(a,piece(a)));}));
        node.then(literal("leave").executes(c->{var a=actor.get(c);return say(c.getSource(),standDown(a,piece(a)));}));
        node.then(literal("fire").executes(c->{var a=actor.get(c);return say(c.getSource(),fire(a,piece(a),null));})
            .then(argument("pos",BlockPosArgument.blockPos()).executes(c->{var a=actor.get(c);var gun=piece(a);var laid=lay(a,gun,Vec3.atCenterOf(BlockPosArgument.getBlockPos(c,"pos")));return say(c.getSource(),laid);})));
        node.then(literal("cease").executes(c->say(c.getSource(),cease(piece(actor.get(c))))));
        node.then(literal("status").executes(c->{var gun=piece(actor.get(c));return say(c.getSource(),gun==null?fail("No siege piece within eight blocks"):ok(Vehicles.status(gun)));}));
    }
    private static int say(CommandSourceStack source,ActionResult result){
        if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;
    }
}
