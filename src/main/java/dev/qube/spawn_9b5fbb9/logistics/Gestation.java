package dev.qube.spawn_9b5fbb9.logistics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.engineering.Building;
import dev.qube.spawn_9b5fbb9.engineering.BuildingUse;
import dev.qube.spawn_9b5fbb9.engineering.Engineering;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

/**
 * Animals carry their young before they bear them. Mating starts a pregnancy; the birth comes
 * gestationDays later, at the mother, wherever she then is. A pregnant animal cannot be bred
 * again, and one that dies takes her young with her.
 *
 * <p>The pregnancy is stored on the animal, so it survives the chunk unloading and the server
 * restarting; the time she spends unloaded still counts.</p>
 */
public final class Gestation {
    private static final String DUE = "spawn_9b5fbb9_due";
    private static final String SIRE = "spawn_9b5fbb9_sire";
    private static final String CAUSE = "spawn_9b5fbb9_breeder";

    /** Pregnant animals currently loaded, per dimension. Rebuilt as entities load. */
    private static final Map<ResourceKey<Level>, Set<UUID>> TRACKED = new HashMap<>();

    private Gestation() {
    }

    public static void clear() {
        TRACKED.clear();
    }

    public static boolean enabled() {
        return Config.integer(Config.GESTATION_DAYS) > 0;
    }

    public static boolean pregnant(Entity entity) {
        return entity.getPersistentData().getLongOr(DUE, 0L) > 0;
    }

    /** Game time at which the animal gives birth, or 0. */
    public static long due(Entity entity) {
        return entity.getPersistentData().getLongOr(DUE, 0L);
    }

    /**
     * Two animals have mated. Returns true if a pregnancy now stands in for the immediate birth.
     */
    public static boolean conceive(ServerLevel level, Animal a, Animal b, ServerPlayer cause) {
        if (!enabled()) {
            return false;
        }
        Animal mother = !pregnant(a) ? a : !pregnant(b) ? b : null;
        if (mother == null) {
            return true; // both already carrying: nothing new, and no instant birth either
        }
        Animal sire = mother == a ? b : a;
        begin(level, mother, sire.getUUID(), cause == null ? null : cause.getUUID(),
                level.getGameTime() + Config.integer(Config.GESTATION_DAYS) * 24000L);
        level.broadcastEntityEvent(mother, (byte) 18); // hearts
        return true;
    }

    /** Starts (or, for tests and commands, forces) a pregnancy. */
    public static void begin(ServerLevel level, Animal mother, UUID sire, UUID breeder, long due) {
        CompoundTag data = mother.getPersistentData();
        data.putLong(DUE, Math.max(1, due));
        if (sire != null) {
            data.putString(SIRE, sire.toString());
        }
        if (breeder != null) {
            data.putString(CAUSE, breeder.toString());
        }
        track(level, mother);
    }

    public static void track(ServerLevel level, Entity entity) {
        TRACKED.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(entity.getUUID());
    }

    private static UUID uuid(CompoundTag data, String key) {
        String s = data.getStringOr(key, "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void end(Entity mother) {
        CompoundTag data = mother.getPersistentData();
        data.remove(DUE);
        data.remove(SIRE);
        data.remove(CAUSE);
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 3) {
            return;
        }
        Set<UUID> tracked = TRACKED.get(level.dimension());
        if (tracked == null || tracked.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<UUID> it = tracked.iterator(); it.hasNext();) {
            Entity e = level.getEntity(it.next());
            if (!(e instanceof Animal mother) || !mother.isAlive() || !pregnant(mother)) {
                it.remove(); // unloaded (she is tracked again when she loads), dead or no longer carrying
                continue;
            }
            long remaining = due(mother) - now;
            if (remaining > 0) {
                if (mother.getAge() >= 0 && mother.getAge() < remaining) {
                    // keeps her out of breeding condition until the birth, then the usual rest after it
                    mother.setAge((int) Math.min(Integer.MAX_VALUE / 2, remaining + 6000));
                }
                continue;
            }
            it.remove();
            birth(level, mother);
        }
    }

    private static void birth(ServerLevel level, Animal mother) {
        CompoundTag data = mother.getPersistentData();
        UUID sireId = uuid(data, SIRE);
        UUID breederId = uuid(data, CAUSE);
        end(mother);
        Animal sire = sireId != null && level.getEntity(sireId) instanceof Animal s && s.isAlive() && s.getClass() == mother.getClass()
                ? s : mother;
        AgeableMob child = mother.getBreedOffspring(level, sire);
        if (child == null) {
            return;
        }
        child.setBaby(true);
        child.snapTo(mother.getX(), mother.getY(), mother.getZ(), 0.0F, 0.0F);
        level.addFreshEntityWithPassengers(child);
        level.broadcastEntityEvent(mother, (byte) 18);
        mother.setAge(6000);
        if (breederId != null && level.getServer().getPlayerList().getPlayer(breederId) instanceof ServerPlayer breeder) {
            breeder.awardStat(Stats.ANIMALS_BRED);
            CriteriaTriggers.BRED_ANIMALS.trigger(breeder, mother, sire, child);
        }
        for (Building b : Engineering.in(level)) {
            if (b.use == BuildingUse.PEN && b.contains(mother.blockPosition())) {
                Ledger.born(level, b);
                break;
            }
        }
    }
}
