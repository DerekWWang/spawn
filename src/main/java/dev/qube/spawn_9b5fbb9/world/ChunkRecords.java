package dev.qube.spawn_9b5fbb9.world;

import java.util.function.Supplier;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** What the mod remembers about a place is kept with the place: crop and soil records, which stone has already been
 * taken once, and what a wall has stood up to are saved in the chunk they belong to. Kept in one world-wide store they
 * grew with every chunk anyone had ever walked through, and the whole of it was written out at every autosave. */
public final class ChunkRecords {
    private ChunkRecords(){}
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,SurvivalContent.MODID);
    public static final Supplier<AttachmentType<CompoundTag>> RECORDS=TYPES.register("records",()->AttachmentType.builder(()->new CompoundTag()).serialize(CompoundTag.CODEC.fieldOf("records"),tag->!tag.isEmpty()).build());
    public static void register(IEventBus bus){TYPES.register(bus);}
    /** The records of one kind held by the chunk at this place, or null. For reading: nothing is created or marked for saving. */
    public static CompoundTag peek(ServerLevel level,BlockPos pos,String kind){
        var root=level.getChunkAt(pos).getExistingDataOrNull(RECORDS.get());
        return root==null?null:root.getCompound(kind).orElse(null);
    }
    /** The same, made if it is not there, with the chunk marked to be saved: for writing. */
    public static CompoundTag edit(ServerLevel level,BlockPos pos,String kind){
        var chunk=level.getChunkAt(pos);var root=chunk.getData(RECORDS.get());
        var held=root.getCompound(kind).orElse(null);if(held==null){held=new CompoundTag();root.put(kind,held);}
        chunk.markUnsaved();return held;
    }
    public static void put(ServerLevel level,BlockPos pos,String kind,String key,Tag value){edit(level,pos,kind).put(key,value);}
    public static void remove(ServerLevel level,BlockPos pos,String kind,String key){
        var chunk=level.getChunkAt(pos);var root=chunk.getExistingDataOrNull(RECORDS.get());if(root==null)return;
        var held=root.getCompound(kind).orElse(null);if(held==null||!held.contains(key))return;
        held.remove(key);if(held.isEmpty())root.remove(kind);chunk.markUnsaved();
    }
}
