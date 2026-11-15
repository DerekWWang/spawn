package dev.qube.spawn_9b5fbb9.engineering;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A reusable design: the blocks of a building in its own frame (as if its cornerstone faced
 * north), where the cornerstone sits within it, what it is for and what it takes to build.
 *
 * @param cells     one int per block: palette index, then x, y, z (6 bits each) from the left, bottom, front corner
 * @param materials item id -> count: the bill of materials
 */
public record Schema(String name, String use, int sx, int sy, int sz, int anchorX, int anchorY, int anchorZ, List<BlockState> palette,
                     List<Integer> cells, Map<String, Integer> materials) {
    public static final Codec<Schema> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Schema::name),
            Codec.STRING.fieldOf("use").forGetter(Schema::use),
            Codec.INT.fieldOf("sx").forGetter(Schema::sx),
            Codec.INT.fieldOf("sy").forGetter(Schema::sy),
            Codec.INT.fieldOf("sz").forGetter(Schema::sz),
            Codec.INT.fieldOf("anchor_x").forGetter(Schema::anchorX),
            Codec.INT.fieldOf("anchor_y").forGetter(Schema::anchorY),
            Codec.INT.fieldOf("anchor_z").forGetter(Schema::anchorZ),
            BlockState.CODEC.listOf().fieldOf("palette").forGetter(Schema::palette),
            Codec.INT_STREAM.xmap(s -> s.boxed().toList(), l -> l.stream().mapToInt(Integer::intValue)).fieldOf("cells")
                    .forGetter(Schema::cells),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("materials").forGetter(Schema::materials)
    ).apply(i, Schema::new));

    public static int pack(int palette, int x, int y, int z) {
        return (palette << 18) | (x << 12) | (y << 6) | z;
    }

    public static int paletteOf(int cell) {
        return cell >>> 18;
    }

    public static int xOf(int cell) {
        return (cell >> 12) & 63;
    }

    public static int yOf(int cell) {
        return (cell >> 6) & 63;
    }

    public static int zOf(int cell) {
        return cell & 63;
    }

    public int blocks() {
        return cells.size();
    }
}
