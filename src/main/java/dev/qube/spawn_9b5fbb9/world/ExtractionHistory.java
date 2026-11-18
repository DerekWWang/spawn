package dev.qube.spawn_9b5fbb9.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import java.util.BitSet;

/** Which natural stone has been taken or set down once already, so that placing and recovering the same block is not
 * practice. A bit to the block, kept by sixteen-block layer in the chunk the block is in. */
public final class ExtractionHistory {
    public static final Identifier KEY=Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"extraction_history");
    private ExtractionHistory(){}
    public static void init(){NeoForge.EVENT_BUS.addListener(ExtractionHistory::placed);}
    public static boolean naturalMaterial(BlockState state){
        String path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(SurvivalContent.LIMESTONE.get())||state.is(Blocks.SANDSTONE)||state.is(Blocks.RED_SANDSTONE)||state.is(Blocks.CALCITE)||state.is(Blocks.OBSIDIAN)||path.endsWith("_ore");
    }
    private static String section(BlockPos pos){return Long.toString(SectionPos.asLong(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4));}
    private static String layer(BlockPos pos){return Integer.toString(pos.getY()>>4);}
    private static int bit(BlockPos pos){return ((pos.getY()&15)<<8)|((pos.getZ()&15)<<4)|(pos.getX()&15);}
    /** The layer's marks. An older world kept every layer of every chunk in one store: a layer found there is moved to its chunk. */
    private static BitSet marks(ServerLevel level,BlockPos pos){
        var held=ChunkRecords.peek(level,pos,"mined");var kept=held==null?java.util.Optional.<long[]>empty():held.getLongArray(layer(pos));
        if(kept.isPresent())return BitSet.valueOf(kept.get());
        var storage=level.getServer().getCommandStorage();var root=storage.get(KEY);if(root.isEmpty())return new BitSet();
        String dimensionId=level.dimension().identifier().toString();var dimension=root.getCompoundOrEmpty(dimensionId);var old=dimension.getLongArray(section(pos));
        if(old.isEmpty())return new BitSet();
        ChunkRecords.edit(level,pos,"mined").putLongArray(layer(pos),old.get());
        dimension.remove(section(pos));if(dimension.isEmpty())root.remove(dimensionId);else root.put(dimensionId,dimension);storage.set(KEY,root);
        return BitSet.valueOf(old.get());
    }
    private static boolean excluded(ServerLevel level,BlockPos pos){return marks(level,pos).get(bit(pos));}
    private static void exclude(ServerLevel level,BlockPos pos){
        var bits=marks(level,pos);if(bits.get(bit(pos)))return;
        bits.set(bit(pos));ChunkRecords.edit(level,pos,"mined").putLongArray(layer(pos),bits.toLongArray());
    }
    public static boolean claim(ServerLevel level,BlockPos pos,BlockState removed){
        if(!naturalMaterial(removed)||excluded(level,pos))return false;exclude(level,pos);return true;
    }
    private static void placed(BlockEvent.EntityPlaceEvent event){
        if(!event.isCanceled()&&event.getLevel() instanceof ServerLevel level&&naturalMaterial(level.getBlockState(event.getPos())))exclude(level,event.getPos());
    }
}
