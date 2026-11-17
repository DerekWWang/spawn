package dev.qube.spawn_9b5fbb9.wildlife;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** A male lion: about 190 kg, 1.2 m at the shoulder. Behaviour is in {@link Predators}. */
public class LionEntity extends PathfinderMob {
    public LionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 28.0).add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 8.0).add(Attributes.FOLLOW_RANGE, 24.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        Predators.goals(this, Predators.SPECIES.get("spawn_9b5fbb9:lion"), true);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean struck = super.doHurtTarget(level, target);
        if (struck) this.playAnimation("attack");
        return struck;
    }

    /** Wild animals stay where they live; they are not conjured and dismissed around the player. */
    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.OCELOT_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.OCELOT_DEATH; }

    private static final String[] ANIMATIONS = {"idle", "attack"};
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();
    private int attackAnimationStateTicks;

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            if (this.attackAnimationStateTicks > 0 && --this.attackAnimationStateTicks == 0) {
                this.attackAnimationState.stop();
            }
        }
    }

    /// Play one of the model's animations by name ("idle", "attack"), on every client. Call it on the server.
    public void playAnimation(String name) {
        int index = java.util.Arrays.asList(ANIMATIONS).indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("No animation " + name + "; this mob has " + String.join(", ", ANIMATIONS));
        }
        this.level().broadcastEntityEvent(this, (byte) (100 + index));
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case 100 -> { this.idleAnimationState.start(this.tickCount); }
            case 101 -> { this.attackAnimationState.start(this.tickCount); this.attackAnimationStateTicks = 10; }
            default -> super.handleEntityEvent(id);
        }
    }
}
