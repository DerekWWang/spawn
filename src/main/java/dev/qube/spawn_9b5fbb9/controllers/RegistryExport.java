package dev.qube.spawn_9b5fbb9.controllers;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/**
 * Writes what a client without the mod cannot know: every non-vanilla block with its states, every non-vanilla
 * item and entity type, with the ids this server gave them. Field names follow the minecraft-data tables that
 * protocol bots (mineflayer) read, so the file can be merged into theirs.
 */
public final class RegistryExport {
    public static final String FILE="spawn-registry.json";
    private RegistryExport(){}
    private static boolean vanilla(net.minecraft.resources.Identifier id){return id.getNamespace().equals("minecraft");}
    public static JsonObject build(){
        JsonObject root=new JsonObject();root.addProperty("schema",1);
        root.addProperty("minecraft",net.minecraft.SharedConstants.getCurrentVersion().name());
        int vanillaStates=0;
        JsonArray blocks=new JsonArray();
        for(Block block:BuiltInRegistries.BLOCK){
            var id=BuiltInRegistries.BLOCK.getKey(block);var states=block.getStateDefinition().getPossibleStates();
            if(vanilla(id)){for(BlockState state:states)vanillaStates=Math.max(vanillaStates,Block.getId(state)+1);continue;}
            BlockState first=states.getFirst(),normal=block.defaultBlockState();
            JsonObject json=new JsonObject();json.addProperty("id",BuiltInRegistries.BLOCK.getId(block));json.addProperty("name",id.toString());
            json.addProperty("displayName",block.getName().getString());
            json.addProperty("hardness",normal.getDestroySpeed(EmptyBlockGetter.INSTANCE,BlockPos.ZERO));json.addProperty("resistance",block.getExplosionResistance());
            json.addProperty("stackSize",new ItemStack(block).isEmpty()?64:new ItemStack(block).getMaxStackSize());
            json.addProperty("diggable",normal.getDestroySpeed(EmptyBlockGetter.INSTANCE,BlockPos.ZERO)>=0);
            String tool=normal.is(BlockTags.MINEABLE_WITH_PICKAXE)?"mineable/pickaxe":normal.is(BlockTags.MINEABLE_WITH_AXE)?"mineable/axe":normal.is(BlockTags.MINEABLE_WITH_SHOVEL)?"mineable/shovel":normal.is(BlockTags.MINEABLE_WITH_HOE)?"mineable/hoe":"default";
            json.addProperty("material",tool);json.addProperty("needsTool",normal.requiresCorrectToolForDrops());
            json.addProperty("transparent",!normal.canOcclude());json.addProperty("emitLight",normal.getLightEmission());json.addProperty("filterLight",normal.getLightDampening());
            json.addProperty("minStateId",Block.getId(first));json.addProperty("maxStateId",Block.getId(states.getLast()));json.addProperty("defaultState",Block.getId(normal));
            JsonArray properties=new JsonArray();
            for(Property<?> property:block.getStateDefinition().getProperties()){
                JsonObject p=new JsonObject();p.addProperty("name",property.getName());
                p.addProperty("type",property instanceof BooleanProperty?"bool":property instanceof IntegerProperty?"int":"enum");
                p.addProperty("num_values",property.getPossibleValues().size());
                if(!(property instanceof BooleanProperty)){JsonArray values=new JsonArray();for(String value:names(property))values.add(value);p.add("values",values);}
                properties.add(p);
            }
            json.add("states",properties);json.add("drops",new JsonArray());
            // One list of boxes per state, in state order: what a body collides with.
            JsonArray shapes=new JsonArray();boolean solid=false;
            for(BlockState state:states){
                JsonArray boxes=new JsonArray();
                for(var box:state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).toAabbs()){JsonArray b=new JsonArray();for(double v:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})b.add(v);boxes.add(b);}
                solid|=!boxes.isEmpty();shapes.add(boxes);
            }
            json.addProperty("boundingBox",solid?"block":"empty");json.add("shapes",shapes);
            blocks.add(json);
        }
        root.addProperty("vanillaStates",vanillaStates);root.add("blocks",blocks);
        JsonArray items=new JsonArray();
        for(var item:BuiltInRegistries.ITEM){
            var id=BuiltInRegistries.ITEM.getKey(item);if(vanilla(id))continue;ItemStack stack=new ItemStack(item);
            JsonObject json=new JsonObject();json.addProperty("id",BuiltInRegistries.ITEM.getId(item));json.addProperty("name",id.toString());
            json.addProperty("displayName",stack.getHoverName().getString());json.addProperty("stackSize",stack.getMaxStackSize());
            if(stack.isDamageableItem())json.addProperty("maxDurability",stack.getMaxDamage());
            var food=stack.get(net.minecraft.core.component.DataComponents.FOOD);if(food!=null)json.addProperty("foodPoints",food.nutrition());
            items.add(json);
        }
        root.add("items",items);
        JsonArray entities=new JsonArray();
        for(var type:BuiltInRegistries.ENTITY_TYPE){
            var id=BuiltInRegistries.ENTITY_TYPE.getKey(type);if(vanilla(id))continue;
            JsonObject json=new JsonObject();int number=BuiltInRegistries.ENTITY_TYPE.getId(type);
            json.addProperty("id",number);json.addProperty("internalId",number);json.addProperty("name",id.toString());json.addProperty("displayName",type.getDescription().getString());
            json.addProperty("width",type.getWidth());json.addProperty("height",type.getHeight());
            String category=type.getCategory().getName();json.addProperty("category",category);
            json.addProperty("type",category.equals("creature")?"animal":category.equals("monster")?"hostile":category.equals("misc")?"other":"mob");
            entities.add(json);
        }
        root.add("entities",entities);
        return root;
    }
    private static <T extends Comparable<T>> java.util.List<String> names(Property<T> property){return property.getPossibleValues().stream().map(property::getName).toList();}
    public static Path write(MinecraftServer server) throws IOException {
        Path file=server.getServerDirectory().resolve(FILE),temporary=file.resolveSibling(FILE+".tmp");
        Files.writeString(temporary,new GsonBuilder().create().toJson(build()));Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
        return file.toAbsolutePath();
    }
}
