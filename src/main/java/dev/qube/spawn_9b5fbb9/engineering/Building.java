package dev.qube.spawn_9b5fbb9.engineering;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A registered structure: where it is, how far it extends from its cornerstone, who owns it and
 * what it is for. The blocks themselves are read from the world whenever the building is assessed.
 *
 * <p>Extents are measured from the cornerstone in its own frame: left/right across its front,
 * front/back along its depth (back is behind the face the builder was looking at), down/up.</p>
 */
public final class Building {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int DOWN = 2;
    public static final int UP = 3;
    public static final int FRONT = 4;
    public static final int BACK = 5;
    public static final int MAX_SPAN = 64;

    public static final Codec<Building> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(b -> b.id),
            Codec.STRING.fieldOf("society").forGetter(b -> b.society),
            Codec.STRING.fieldOf("name").forGetter(b -> b.name),
            Codec.STRING.fieldOf("use").forGetter(b -> b.use.id),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(b -> b.dimension),
            BlockPos.CODEC.fieldOf("cornerstone").forGetter(b -> b.cornerstone),
            Direction.CODEC.fieldOf("facing").forGetter(b -> b.facing),
            Codec.INT.listOf().fieldOf("extents").forGetter(b -> List.of(b.extents[0], b.extents[1], b.extents[2], b.extents[3],
                    b.extents[4], b.extents[5])),
            Codec.STRING.optionalFieldOf("schema", "").forGetter(b -> b.schema),
            Codec.LONG.optionalFieldOf("last_decay", 0L).forGetter(b -> b.lastDecay)
    ).apply(i, Building::new));

    public final String id;
    public String society;
    public String name;
    public BuildingUse use;
    public final ResourceKey<Level> dimension;
    public final BlockPos cornerstone;
    public final Direction facing;
    public final int[] extents = new int[6];
    /** Name of the schema this building is being built to, or "" if it was surveyed as built. */
    public String schema;
    public long lastDecay;

    // not saved
    public boolean dirty = true;
    public @Nullable Assessment assessment;
    public long assessedAt = Long.MIN_VALUE;

    public Building(String id, String society, String name, BuildingUse use, ResourceKey<Level> dimension, BlockPos cornerstone,
                    Direction facing) {
        this.id = id;
        this.society = society;
        this.name = name;
        this.use = use;
        this.dimension = dimension;
        this.cornerstone = cornerstone.immutable();
        this.facing = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        this.schema = "";
        setExtents(3, 3, 0, 4, 0, 6);
    }

    private Building(String id, String society, String name, String use, ResourceKey<Level> dimension, BlockPos cornerstone,
                     Direction facing, List<Integer> extents, String schema, long lastDecay) {
        this(id, society, name, BuildingUse.byId(use) == null ? BuildingUse.GENERIC : BuildingUse.byId(use), dimension, cornerstone, facing);
        if (extents.size() == 6) {
            setExtents(extents.get(0), extents.get(1), extents.get(2), extents.get(3), extents.get(4), extents.get(5));
        }
        this.schema = schema;
        this.lastDecay = lastDecay;
    }

    /** Sets the extents, clamped so no side of the box exceeds MAX_SPAN. */
    public void setExtents(int left, int right, int down, int up, int front, int back) {
        int[] v = {left, right, down, up, front, back};
        for (int axis = 0; axis < 3; axis++) {
            int a = Math.max(0, v[axis * 2]);
            int b = Math.max(0, v[axis * 2 + 1]);
            a = Math.min(a, MAX_SPAN - 1);
            b = Math.min(b, MAX_SPAN - 1 - a);
            extents[axis * 2] = a;
            extents[axis * 2 + 1] = b;
        }
        dirty = true;
    }

    public Direction right() {
        return facing.getClockWise();
    }

    public Direction back() {
        return facing.getOpposite();
    }

    public int sizeX() {
        return extents[LEFT] + extents[RIGHT] + 1;
    }

    public int sizeY() {
        return extents[DOWN] + extents[UP] + 1;
    }

    public int sizeZ() {
        return extents[FRONT] + extents[BACK] + 1;
    }

    public int volume() {
        return sizeX() * sizeY() * sizeZ();
    }

    /** World position of local cell (lx, ly, lz), where (0,0,0) is the left, bottom, front corner. */
    public BlockPos world(int lx, int ly, int lz) {
        Direction r = right();
        Direction b = back();
        int dr = lx - extents[LEFT];
        int db = lz - extents[FRONT];
        return new BlockPos(cornerstone.getX() + r.getStepX() * dr + b.getStepX() * db, cornerstone.getY() + ly - extents[DOWN],
                cornerstone.getZ() + r.getStepZ() * dr + b.getStepZ() * db);
    }

    public BlockPos min() {
        BlockPos a = world(0, 0, 0);
        BlockPos c = world(sizeX() - 1, sizeY() - 1, sizeZ() - 1);
        return new BlockPos(Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()));
    }

    public BlockPos max() {
        BlockPos a = world(0, 0, 0);
        BlockPos c = world(sizeX() - 1, sizeY() - 1, sizeZ() - 1);
        return new BlockPos(Math.max(a.getX(), c.getX()), Math.max(a.getY(), c.getY()), Math.max(a.getZ(), c.getZ()));
    }

    public AABB bounds() {
        BlockPos lo = min();
        BlockPos hi = max();
        return new AABB(lo.getX(), lo.getY(), lo.getZ(), hi.getX() + 1, hi.getY() + 1, hi.getZ() + 1);
    }

    public boolean contains(BlockPos pos) {
        return contains(pos, 0);
    }

    public boolean contains(BlockPos pos, int margin) {
        BlockPos lo = min();
        BlockPos hi = max();
        return pos.getX() >= lo.getX() - margin && pos.getX() <= hi.getX() + margin && pos.getY() >= lo.getY() - margin
                && pos.getY() <= hi.getY() + margin && pos.getZ() >= lo.getZ() - margin && pos.getZ() <= hi.getZ() + margin;
    }

    public boolean planned() {
        return !schema.isEmpty();
    }
}
