package dev.qube.spawn_9b5fbb9.physics;

import java.util.IdentityHashMap;
import java.util.Map;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a class of building material behaves. One block is one cubic metre.
 *
 * <ul>
 * <li>mass: tonnes per block.</li>
 * <li>span: bending strength, in tonnes one joint can carry sideways. Each block of a cantilever
 * carries everything beyond it, so its reach n satisfies mass * n(n+1)/2 &lt;= span: four blocks for
 * timber, two for stone and brick, five for reinforced concrete, eight for steel.</li>
 * <li>comp: how many tonnes one block can carry in compression before it crushes or buckles.</li>
 * <li>tensile: whether it holds together in tension (timber, reinforced concrete, steel). Such
 * materials can hang from above and, driven into the ground, resist water by bending.</li>
 * <li>wind: how readily a storm strips it from an exposed face.</li>
 * <li>life*: expected life in game days when dry, rained on, or under water (0 = lasts indefinitely).</li>
 * </ul>
 *
 * Blocks are assigned through the block tags physics/material/&lt;name&gt;; anything untagged is masonry.
 */
public enum StructuralMaterial {
    THATCH("thatch", 0.3F, 1.8F, 8F, false, 0.90F, 60, 20, 8),
    TIMBER("timber", 0.6F, 6.0F, 60F, true, 0.30F, 400, 60, 25),
    GLASS("glass", 2.5F, 2.6F, 40F, false, 0.55F, 0, 0, 0),
    BRICK("brick", 1.9F, 6.0F, 200F, false, 0.12F, 0, 1500, 800),
    CONCRETE("concrete", 2.3F, 7.0F, 500F, false, 0.05F, 0, 3000, 1500),
    REINFORCED("reinforced_concrete", 2.4F, 36F, 800F, true, 0.03F, 0, 2000, 1000),
    IRON("iron", 7.8F, 300F, 3000F, true, 0.05F, 2000, 300, 150),
    STAINLESS("stainless", 7.9F, 300F, 3000F, true, 0.05F, 0, 4000, 2000),
    EARTH("earth", 1.6F, 1.2F, 15F, false, 0.40F, 0, 200, 30),
    MASONRY("masonry", 2.5F, 7.5F, 400F, false, 0.06F, 0, 3000, 1500);

    private static final StructuralMaterial[] VALUES = values();
    private static final Map<Block, StructuralMaterial> CACHE = new IdentityHashMap<>();

    public final String id;
    public final TagKey<Block> tag;
    public final float mass;
    public final float span;
    public final float comp;
    public final boolean tensile;
    public final float wind;
    public final int lifeDry;
    public final int lifeWet;
    public final int lifeSubmerged;

    StructuralMaterial(String id, float mass, float span, float comp, boolean tensile, float wind, int lifeDry, int lifeWet,
                       int lifeSubmerged) {
        this.id = id;
        this.tag = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "physics/material/" + id));
        this.mass = mass;
        this.span = span;
        this.comp = comp;
        this.tensile = tensile;
        this.wind = wind;
        this.lifeDry = lifeDry;
        this.lifeWet = lifeWet;
        this.lifeSubmerged = lifeSubmerged;
    }

    public static StructuralMaterial byOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    /** Server thread only (the cache is not synchronised). */
    public static StructuralMaterial of(BlockState state) {
        Block block = state.getBlock();
        StructuralMaterial m = CACHE.get(block);
        if (m == null) {
            m = MASONRY;
            for (StructuralMaterial candidate : VALUES) {
                if (state.is(candidate.tag)) {
                    m = candidate;
                    break;
                }
            }
            CACHE.put(block, m);
        }
        return m;
    }

    /** Tags can change on /reload. */
    public static void clearCache() {
        CACHE.clear();
    }

    /** Expected life in game days for the exposure, or 0 if the material does not decay there. */
    public int life(Exposure exposure) {
        return switch (exposure) {
            case DRY -> lifeDry;
            case WET -> lifeWet;
            case SUBMERGED -> lifeSubmerged;
        };
    }

    public enum Exposure { DRY, WET, SUBMERGED }
}
