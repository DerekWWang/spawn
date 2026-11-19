package dev.qube.spawn_9b5fbb9.logistics;

import java.util.Map;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.engineering.Assessment;
import dev.qube.spawn_9b5fbb9.engineering.Building;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.station.StationIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Food goes off. Each perishable has a half-life (fresh meat two days, bread and cooked food six,
 * grain and roots thirty); salted and canned food keeps. A dry, enclosed store halves the rate and a
 * cold store cuts it to a tenth. Spoilage is worked out lazily, from the time since a container
 * was last looked at, so stores in unloaded chunks catch up when they are next seen.
 */
public final class Spoilage {
    public static final TagKey<Item> FAST = tag("spoilage/fast");
    public static final TagKey<Item> MEDIUM = tag("spoilage/medium");
    public static final TagKey<Item> SLOW = tag("spoilage/slow");
    private static final String STAMP = "spawn_9b5fbb9_spoilage";
    private static final long MIN_INTERVAL = 600;
    private static final long UNSEEN = Long.MIN_VALUE;
    private static final double MAX_DAYS = 1000;

    private Spoilage() {
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, path));
    }

    /**
     * Half-life in game days, or 0 for things that keep. Food is the survival rules' alone: they age every
     * edible thing by its own shelf life, so nothing spoils twice.
     */
    public static double halfLife(ItemStack stack) {
        if (dev.qube.spawn_9b5fbb9.content.ItemAging.isFood(stack) || stack.is(net.minecraft.world.item.Items.WHEAT)) {
            return 0;
        }
        if (stack.is(FAST)) {
            return 2;
        }
        if (stack.is(MEDIUM)) {
            return 6;
        }
        return stack.is(SLOW) ? 30 : 0;
    }

    /** Rate multiplier for goods kept in this building. */
    public static double quality(ServerLevel level, Building b, Assessment a) {
        if (!StationIndex.formedNear(level, "cold_store", b.bounds(), 8).isEmpty()) {
            return 0.1;
        }
        return a.complete && a.sound && a.enclosed ? 0.5 : 1.0;
    }

    /** How many of n items are lost over the given number of days. */
    public static int lost(RandomSource random, int n, double days, double halfLife) {
        if (halfLife <= 0 || days <= 0 || n <= 0) {
            return 0;
        }
        double p = 1 - Math.pow(0.5, days / halfLife);
        int lost = 0;
        for (int i = 0; i < n; i++) {
            if (random.nextDouble() < p) {
                lost++;
            }
        }
        return lost;
    }

    /**
     * Ages the contents of a container by the time since it was last looked at.
     *
     * @param spoiled receives item id -> number lost (may be null)
     */
    public static void apply(ServerLevel level, BlockEntity be, Container c, double quality, Map<String, Integer> spoiled) {
        CompoundTag data = be.getPersistentData();
        long now = level.getGameTime();
        long last = data.getLongOr(STAMP, UNSEEN);
        if (last == UNSEEN || last > now) {
            data.putLong(STAMP, now); // first look: the clock starts here
            be.setChanged();
            return;
        }
        if (now - last < MIN_INTERVAL) {
            return;
        }
        data.putLong(STAMP, now);
        be.setChanged();
        double days = Math.min(MAX_DAYS, (now - last) / 24000.0) * Config.dbl(Config.SPOILAGE_RATE) * quality;
        age(level.getRandom(), c, days, spoiled);
    }

    /** Makes a container's contents older by the given time, as if it had been left that long (for tests and operators). */
    public static void backdate(ServerLevel level, BlockEntity be, long ticks) {
        CompoundTag data = be.getPersistentData();
        data.putLong(STAMP, data.getLongOr(STAMP, level.getGameTime()) - Math.max(0, ticks));
        be.setChanged();
    }

    /** Ages a container's contents by the given number of days at the full rate. */
    public static void age(RandomSource random, Container c, double days, Map<String, Integer> spoiled) {
        if (days <= 0) {
            return;
        }
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            double hl = stack.isEmpty() ? 0 : halfLife(stack);
            if (hl <= 0) {
                continue;
            }
            int lost = lost(random, stack.getCount(), days, hl);
            if (lost > 0) {
                if (spoiled != null) {
                    spoiled.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), lost, Integer::sum);
                }
                c.removeItem(slot, lost);
            }
        }
    }

    /**
     * Food carried about spoils at the full rate.
     *
     * @return item id -> number lost
     */
    public static Map<String, Integer> applyToPlayer(ServerPlayer player) {
        Map<String, Integer> spoiled = new java.util.HashMap<>();
        if (player.isCreative() || player.isSpectator()) {
            return spoiled;
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        long last = data.getLongOr(STAMP, UNSEEN);
        data.putLong(STAMP, now);
        if (last == UNSEEN || last > now) {
            return spoiled;
        }
        age(player.getRandom(), player.getInventory(), Math.min(MAX_DAYS, (now - last) / 24000.0) * Config.dbl(Config.SPOILAGE_RATE), spoiled);
        return spoiled;
    }

    /** Time spent logged out, or in another dimension's clock, does not count against carried food. */
    public static void resetClock(ServerPlayer player) {
        player.getPersistentData().putLong(STAMP, player.level().getGameTime());
    }
}
