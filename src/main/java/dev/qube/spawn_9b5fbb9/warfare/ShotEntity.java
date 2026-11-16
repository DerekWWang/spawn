package dev.qube.spawn_9b5fbb9.warfare;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A cannonball, bombard stone, trebuchet stone or shell in flight. It follows a plain ballistic arc,
 * ploughs through anything alive in its path, and spends its energy breaking what it strikes. */
public class ShotEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM=SynchedEntityData.defineId(ShotEntity.class,EntityDataSerializers.ITEM_STACK);
    public double power;
    public float entityDamage;
    public boolean explosive;
    public UUID owner;
    private final Set<Integer> struck=new HashSet<>();
    public ShotEntity(EntityType<?> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){data.define(ITEM,ItemStack.EMPTY);}
    @Override public ItemStack getItem(){return entityData.get(ITEM);}
    public void launch(ItemStack munition,Vec3 velocity,double power,float entityDamage,boolean explosive,Entity owner){
        entityData.set(ITEM,munition.copyWithCount(1));setDeltaMovement(velocity);this.power=power;this.entityDamage=entityDamage;this.explosive=explosive;this.owner=owner==null?null:owner.getUUID();
    }
    @Override protected InterpolationHandler createInterpolationHandler(){return LinearInterpolationHandler.create(this,2);}
    @Override public boolean isPickable(){return false;}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return false;}
    public Entity ownerEntity(){return owner!=null&&level() instanceof ServerLevel level?level.getEntity(owner):null;}
    @Override public void tick(){
        super.tick();
        if(!(level() instanceof ServerLevel level))return;
        Vec3 from=position(),motion=getDeltaMovement(),to=from.add(motion);
        if(tickCount>900||!level.hasChunkAt(BlockPos.containing(to))||to.y<level.getMinY()-8){discard();return;}
        var hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        Vec3 end=hit.getType()==HitResult.Type.BLOCK?hit.getLocation():to;Entity shooter=ownerEntity();
        for(Entity target:level.getEntities(this,new AABB(from,end).inflate(.8),e->e.isAlive()&&e.isPickable()&&!struck.contains(e.getId()))){
            // The crew and the piece that fired are behind the muzzle for the first instants of flight.
            if(tickCount<3&&(target==shooter||shooter!=null&&target.distanceToSqr(shooter)<16))continue;
            if(target.getBoundingBox().inflate(.35).clip(from,end).isEmpty()&&!target.getBoundingBox().inflate(.35).contains(from))continue;
            struck.add(target.getId());
            if(explosive){Siege.burst(level,this,target.position().add(0,.5,0));discard();return;}
            target.hurtServer(level,Siege.source(level,this,shooter),entityDamage);
            Vec3 push=motion.normalize().scale(.9);target.push(push.x,.25,push.z);
            entityDamage*=.7f;power*=.85;
        }
        if(hit.getType()==HitResult.Type.BLOCK){
            setPos(end.x,end.y,end.z);
            if(Siege.impact(level,this,hit.getBlockPos(),end)){discard();return;}
            setDeltaMovement(motion.scale(.55));return;
        }
        setPos(to.x,to.y,to.z);setDeltaMovement(motion.x,motion.y-Siege.GRAVITY,motion.z);
        if(tickCount%2==0)level.sendParticles(ParticleTypes.SMOKE,to.x,to.y,to.z,1,0,0,0,0);
    }
    @Override protected void readAdditionalSaveData(ValueInput input){}
    @Override protected void addAdditionalSaveData(ValueOutput output){}
}
