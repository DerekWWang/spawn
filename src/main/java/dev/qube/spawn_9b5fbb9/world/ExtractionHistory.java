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

/** Persisted per-section exclusions prevent placing/recovering the same stone for practice. */
public final class ExtractionHistory {
    public static final Identifier KEY=Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"extraction_history");
    private ExtractionHistory(){}
    public static void init(){NeoForge.EVENT_BUS.addListener(ExtractionHistory::placed);}
    public static boolean naturalMaterial(BlockState state){
        String path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(SurvivalContent.LIMESTONE.get())||state.is(Blocks.SANDSTONE)||state.is(Blocks.RED_SANDSTONE)||state.is(Blocks.CALCITE)||state.is(Blocks.OBSIDIAN)||path.endsWith("_ore");
    }
    private static String section(BlockPos pos){return Long.toString(SectionPos.asLong(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4));}
    private static int bit(BlockPos pos){return ((pos.getY()&15)<<8)|((pos.getZ()&15)<<4)|(pos.getX()&15);}
    private static boolean excluded(ServerLevel level,BlockPos pos){
        var root=level.getServer().getCommandStorage().get(KEY);var dimension=root.getCompoundOrEmpty(level.dimension().identifier().toString());
        return BitSet.valueOf(dimension.getLongArray(section(pos)).orElse(new long[0])).get(bit(pos));
    }
    private static void exclude(ServerLevel level,BlockPos pos){
        var storage=level.getServer().getCommandStorage();var root=storage.get(KEY);String dimensionId=level.dimension().identifier().toString();var dimension=root.getCompoundOrEmpty(dimensionId);
        var bits=BitSet.valueOf(dimension.getLongArray(section(pos)).orElse(new long[0]));bits.set(bit(pos));dimension.putLongArray(section(pos),bits.toLongArray());root.put(dimensionId,dimension);storage.set(KEY,root);
    }
    public static boolean claim(ServerLevel level,BlockPos pos,BlockState removed){
        if(!naturalMaterial(removed)||excluded(level,pos))return false;exclude(level,pos);return true;
    }
    private static void placed(BlockEvent.EntityPlaceEvent event){
        if(!event.isCanceled()&&event.getLevel() instanceof ServerLevel level&&naturalMaterial(level.getBlockState(event.getPos())))exclude(level,event.getPos());
    }
}
