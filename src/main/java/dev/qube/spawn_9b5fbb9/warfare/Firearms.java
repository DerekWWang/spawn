package dev.qube.spawn_9b5fbb9.warfare;

import dev.qube.spawn_9b5fbb9.content.ItemAging;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.industry.IndustrialContent;
import dev.qube.spawn_9b5fbb9.survival.SkillApi;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.transport.VehicleEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The flintlock musket: slow to load, inaccurate beyond a volley's distance, unreliable in rain,
 * and lethal through armour when it does hit. */
public final class Firearms {
    private Firearms(){}
    public static final ResourceKey<DamageType> GUNSHOT=ResourceKey.create(Registries.DAMAGE_TYPE,Identifier.fromNamespaceAndPath(IndustrialContent.MODID,"gunshot"));
    /** Loading drill at full skill and capacity; a raw recruit takes two thirds longer. */
    public static final double RELOAD_WORK=240;
    public static final double RANGE=120;
    public record Result(boolean fired,Entity hit,String message){}
    public static void init(){}
    public static boolean loaded(ItemStack musket){return ItemAging.data(musket).getBooleanOr("loaded",false);}
    public static void setLoaded(ItemStack musket,boolean loaded){var data=ItemAging.data(musket);if(loaded)data.putBoolean("loaded",true);else data.remove("loaded");ItemAging.data(musket,data);}
    public static boolean isMusket(ItemStack stack){return stack.is(SurvivalContent.item("musket"));}
    public static double reloadRate(LivingEntity actor){
        if(SurvivalApi.exempt(actor))return 1;var body=SurvivalApi.state(actor);
        return Math.max(.05,(.6+.4*body.skills.level(Skill.MARKSMANSHIP)/20d)*Math.min(body.manipulation(),Math.max(.05,body.mental())));
    }
    public static boolean takeCartridge(Player player){
        if(player.getAbilities().instabuild)return true;var cartridge=SurvivalContent.item("paper_cartridge");
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++){ItemStack stack=player.getInventory().getItem(slot);if(stack.is(cartridge)){stack.shrink(1);return true;}}
        return false;
    }
    public static boolean hasCartridge(Player player){
        if(player.getAbilities().instabuild)return true;var cartridge=SurvivalContent.item("paper_cartridge");
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot).is(cartridge))return true;return false;
    }
    public static DamageSource source(ServerLevel level,Entity shooter){return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(GUNSHOT),shooter);}
    /** A ball loses energy quickly: murderous inside twenty blocks, a third as dangerous at a hundred. */
    public static float damageAt(double distance){return (float)(distance<=20?18:Mth.lerp(Math.min(1,(distance-20)/80),18,6));}
    /** Standard deviation of aim error in degrees. A smoothbore is a volley weapon, not a marksman's. */
    public static double spread(LivingEntity shooter){
        double skill=SurvivalApi.exempt(shooter)?10:SkillApi.level(shooter,Skill.MARKSMANSHIP),sigma=1.6-.055*skill;
        if(shooter.isPassenger())sigma*=1.8;
        return sigma;
    }
    public static Result fire(LivingEntity shooter,ItemStack musket,ServerLevel level,Vec3 direction){
        if(!loaded(musket))return new Result(false,null,"The musket is not loaded");
        setLoaded(musket,false);musket.hurtAndBreak(1,level,shooter,item->{});
        Vec3 eye=shooter.getEyePosition(),muzzle=eye.add(direction.scale(1.1));
        boolean wet=level.isRainingAt(shooter.blockPosition().above());
        if(level.getRandom().nextFloat()<(wet?.5f:.1f)){
            level.playSound(null,eye.x,eye.y,eye.z,SoundEvents.FLINTANDSTEEL_USE,SoundSource.PLAYERS,1,.8f);
            return new Result(false,null,wet?"Misfire: the priming is wet":"Misfire: a flash in the pan");
        }
        double sigma=spread(shooter);
        Vec3 aim=direction.normalize().yRot((float)(level.getRandom().nextGaussian()*sigma*Mth.DEG_TO_RAD)).add(0,Math.tan(level.getRandom().nextGaussian()*sigma*.7*Mth.DEG_TO_RAD),0).normalize();
        Vec3 end=eye.add(aim.scale(RANGE));
        var block=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shooter));
        if(block.getType()==HitResult.Type.BLOCK)end=block.getLocation();
        Entity struck=null;double nearest=eye.distanceToSqr(end);Entity mount=shooter.getVehicle();
        for(Entity candidate:level.getEntities(shooter,new AABB(eye,end).inflate(1),e->e.isAlive()&&e.isPickable()&&(e instanceof LivingEntity||e instanceof VehicleEntity))){
            if(candidate==mount||candidate.getVehicle()==mount&&mount!=null)continue;
            var clip=candidate.getBoundingBox().inflate(.15).clip(eye,end);if(clip.isEmpty())continue;
            double distance=eye.distanceToSqr(clip.get());if(distance<nearest){nearest=distance;struck=candidate;}
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,muzzle.x,muzzle.y,muzzle.z,8,.15,.15,.15,.02);level.sendParticles(ParticleTypes.FLAME,muzzle.x,muzzle.y,muzzle.z,3,.05,.05,.05,.02);
        level.playSound(null,eye.x,eye.y,eye.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,2.5f,1.7f);
        // Gunfire stampedes loose animals and shakes men who are shot at.
        for(Animal animal:level.getEntitiesOfClass(Animal.class,shooter.getBoundingBox().inflate(10),a->!a.isVehicle()&&!a.isNoAi())){Vec3 away=animal.position().subtract(shooter.position()).normalize().scale(10);animal.getNavigation().moveTo(animal.getX()+away.x,animal.getY(),animal.getZ()+away.z,1.6);}
        if(struck==null){Military.shock(level,end,4,1);return new Result(true,null,"Fired");}
        double range=Math.sqrt(nearest);float damage=damageAt(range);
        struck.hurtServer(level,source(level,shooter),damage);Military.shock(level,struck.position(),5,2);
        SkillApi.award(shooter,Skill.MARKSMANSHIP,damage*12);
        return new Result(true,struck,"Hit at "+Math.round(range)+" blocks");
    }
}
