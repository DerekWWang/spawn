package dev.qube.spawn_9b5fbb9.survival;

import dev.qube.spawn_9b5fbb9.content.WorkstationEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.phys.Vec3;

/** One pair of hands: a pending forge claim excludes other manual jobs while its worker is present. */
public final class LaborGuard {
    private LaborGuard(){}
    public static boolean worldBusy(LivingEntity actor){return actor.getPersistentData().contains("survivalWorldJob");}
    public static boolean gridBusy(LivingEntity actor){return actor instanceof Player player&&player.containerMenu instanceof AbstractCraftingMenu menu&&!menu.getResultSlot().getItem().isEmpty()&&actor.getPersistentData().getDoubleOr("survivalGridWork",0)>0;}
    private static WorkstationEntity claimed(LivingEntity actor){
        if(!(actor.level() instanceof ServerLevel level)||!actor.isAlive())return null;
        var meta=actor.getPersistentData();if(!meta.contains("survivalForgePos")||!meta.getStringOr("survivalForgeDimension","").equals(level.dimension().identifier().toString()))return null;
        BlockPos pos=BlockPos.of(meta.getLongOr("survivalForgePos",0));
        if(!level.hasChunkAt(pos)||actor.distanceToSqr(Vec3.atCenterOf(pos))>36)return null;
        if(level.getBlockEntity(pos) instanceof WorkstationEntity station&&station.worker.equals(actor.getUUID().toString())&&!station.input.isEmpty()&&station.output.isEmpty()&&station.required>station.elapsed&&station.hasUsableHeat())return station;
        return null;
    }
    public static boolean forgeBusy(LivingEntity actor){return claimed(actor)!=null;}
    public static boolean mayStartForge(LivingEntity actor,WorkstationEntity station){return actor.isAlive()&&!worldBusy(actor)&&!gridBusy(actor)&&!forgeBusy(actor);}
    public static void claimForge(LivingEntity actor,WorkstationEntity station){
        actor.getPersistentData().putLong("survivalForgePos",station.getBlockPos().asLong());
        actor.getPersistentData().putString("survivalForgeDimension",actor.level().dimension().identifier().toString());
    }
    public static boolean mayAdvanceForge(LivingEntity actor,WorkstationEntity station){
        if(worldBusy(actor)||gridBusy(actor))return false;
        var existing=claimed(actor);if(existing!=null&&existing!=station)return false;
        long last=actor.getPersistentData().getLongOr("survivalManualWorkAt",Long.MIN_VALUE/2);
        if(actor.level().getGameTime()-last<20)return false;
        claimForge(actor,station);return true;
    }
    public static void worked(LivingEntity actor){actor.getPersistentData().putLong("survivalManualWorkAt",actor.level().getGameTime());}
}
