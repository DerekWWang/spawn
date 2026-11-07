package dev.qube.spawn_9b5fbb9.survival.mixin;
import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(LivingEntity.class)
public abstract class SurvivalViewMixin implements SurvivalView {
    @Unique private SurvivalState spawn$body(){var actor=(LivingEntity)(Object)this;return actor.level() instanceof ServerLevel?(SurvivalApi.isHumanoid(actor)?SurvivalApi.state(actor):null):SurvivalNetwork.entityStates.get(actor.getId());}
    public double getSpawnSurvivalHunger(){var s=spawn$body();return s==null?Double.NaN:s.hunger;}
    public double getSpawnSurvivalThirst(){var s=spawn$body();return s==null?Double.NaN:s.thirst;}
    public double getSpawnSurvivalTemperature(){var s=spawn$body();return s==null?Double.NaN:s.temperature;}
    public double getSpawnSurvivalMental(){var s=spawn$body();return s==null?Double.NaN:s.mental();}
    public double getSpawnSurvivalMovement(){var s=spawn$body();return s==null?Double.NaN:s.movement();}
}
