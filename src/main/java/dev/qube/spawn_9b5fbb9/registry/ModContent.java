package dev.qube.spawn_9b5fbb9.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

import dev.qube.spawn_9b5fbb9.engineering.CornerstoneBlock;
import dev.qube.spawn_9b5fbb9.item.FertilizerItem;
import dev.qube.spawn_9b5fbb9.item.MedicineItem;
import dev.qube.spawn_9b5fbb9.item.TreatiseItem;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.station.StationBlock;
import dev.qube.spawn_9b5fbb9.station.StationBlockEntity;
import dev.qube.spawn_9b5fbb9.station.StationMenu;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers every item, block and structure controller described by the tech tree. */
public final class ModContent {
    private static final String MODID = spawn_9b5fbb9.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    /** item id -> registered item (plain items, block items and station items) */
    public static final Map<String, DeferredItem<? extends Item>> ITEM_BY_ID = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<Block>> SIMPLE_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<StationBlock>> STATION_BLOCKS = new LinkedHashMap<>();

    /** Marks a structure as a building; see the engineering engine. */
    public static final DeferredBlock<CornerstoneBlock> CORNERSTONE = BLOCKS.registerBlock("cornerstone", CornerstoneBlock::new,
            p -> p.strength(2.0F, 6.0F).sound(SoundType.STONE).mapColor(MapColor.STONE));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CORNERSTONE_ITEM = ITEMS.registerSimpleBlockItem("cornerstone", CORNERSTONE);

    /** The function id a treatise teaches. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TREATISE_SUBJECT =
            COMPONENTS.registerComponentType("treatise_subject",
                    b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** The society that wrote a treatise (for the tooltip). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TREATISE_AUTHOR =
            COMPONENTS.registerComponentType("treatise_author",
                    b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    static {
        TechTree tree = TechTree.get();
        // The survival rules register the ids they share with the tree; the tree uses theirs.
        for (String id : tree.survival) {
            ITEM_BY_ID.put(id, DeferredItem.createItem(Identifier.fromNamespaceAndPath(MODID, id)));
        }
        for (TechTree.BlockDef def : tree.blocks) {
            if (tree.survival.contains(def.id())) {
                continue;
            }
            DeferredBlock<Block> block = BLOCKS.registerBlock(def.id(), Block::new, p -> blockProperties(p, def));
            SIMPLE_BLOCKS.put(def.id(), block);
            ITEM_BY_ID.put(def.id(), ITEMS.registerSimpleBlockItem(def.id(), block));
        }
        for (TechTree.StationDef def : tree.stations) {
            String id = def.id();
            if (tree.survival.contains(id)) {
                continue;
            }
            DeferredBlock<StationBlock> block = BLOCKS.registerBlock(id, p -> new StationBlock(p, id),
                    p -> p.strength(2.5F, 6.0F).sound(stationSound(def.family())).mapColor(MapColor.METAL));
            STATION_BLOCKS.put(id, block);
            ITEM_BY_ID.put(id, ITEMS.registerSimpleBlockItem(id, block, p -> p.stacksTo(1)));
        }
        for (TechTree.ItemDef def : tree.items) {
            if (def.station() || tree.survival.contains(def.id())) {
                continue;
            }
            DeferredItem<? extends Item> item;
            if (def.treatise()) {
                item = ITEMS.registerItem(def.id(), TreatiseItem::new, p -> p.stacksTo(1));
            } else if (def.medicine() != null) {
                item = ITEMS.registerItem(def.id(), p -> new MedicineItem(p, def.medicine()), p -> itemProperties(p, def));
            } else if (def.fertilizer()) {
                item = ITEMS.registerItem(def.id(), FertilizerItem::new, p -> itemProperties(p, def));
            } else {
                item = ITEMS.registerItem(def.id(), Item::new, p -> itemProperties(p, def));
            }
            ITEM_BY_ID.put(def.id(), item);
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationBlockEntity>> STATION_BE =
            BLOCK_ENTITIES.register("station", () -> {
                Set<Block> blocks = new HashSet<>();
                STATION_BLOCKS.values().forEach(b -> blocks.add(b.get()));
                return new BlockEntityType<>(StationBlockEntity::new, Set.copyOf(blocks));
            });

    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> STATION_MENU =
            MENUS.register("station", () -> IMenuTypeExtension.create(StationMenu::fromNetwork));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RESOURCES_TAB = TABS.register("resources", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.spawn_9b5fbb9.resources"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> stack("malachite"))
            .displayItems((params, output) -> {
                TechTree tree = TechTree.get();
                tree.items.stream().filter(i -> "resource".equals(i.category())).forEach(i -> output.accept(stack(i.id())));
                tree.blocks.forEach(b -> output.accept(stack(b.id())));
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> STATIONS_TAB = TABS.register("stations", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.spawn_9b5fbb9.stations"))
            .withTabsBefore(RESOURCES_TAB.key())
            .icon(() -> stack("forge"))
            .displayItems((params, output) -> {
                output.accept(CORNERSTONE_ITEM.get());
                sortedItems(true).forEach(i -> output.accept(stack(i.id())));
            })
            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS_TAB = TABS.register("items", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.spawn_9b5fbb9.items"))
            .withTabsBefore(STATIONS_TAB.key())
            .icon(() -> stack("hand_axe"))
            .displayItems((params, output) -> sortedItems(false).forEach(i -> output.accept(stack(i.id()))))
            .build());

    private ModContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
    }

    /** The registered item for a tech-tree id, or null if there is none. */
    public static Item item(String id) {
        DeferredItem<? extends Item> holder = ITEM_BY_ID.get(id);
        return holder == null ? null : holder.get();
    }

    public static ItemStack stack(String id) {
        Item item = item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static List<TechTree.ItemDef> sortedItems(boolean stations) {
        List<TechTree.ItemDef> list = new ArrayList<>();
        for (TechTree.ItemDef i : TechTree.get().items) {
            if ("resource".equals(i.category())) {
                continue;
            }
            boolean isStation = i.station() || "station".equals(i.category()) || "infrastructure".equals(i.category());
            if (isStation == stations) {
                list.add(i);
            }
        }
        list.sort(Comparator.comparingInt(TechTree.ItemDef::era).thenComparingInt(TechTree.ItemDef::depth)
                .thenComparing(TechTree.ItemDef::name));
        return list;
    }

    private static Item.Properties itemProperties(Item.Properties p, TechTree.ItemDef def) {
        String tool = def.tool();
        if ("axe".equals(tool)) {
            p.axe(ToolMaterial.STONE, 7.0F, -3.2F);
        } else if ("hoe".equals(tool)) {
            p.hoe(ToolMaterial.STONE, -1.0F, -2.0F);
        } else if ("spear".equals(tool)) {
            // same handling as vanilla's wooden spear
            p.spear(ToolMaterial.WOOD, 0.65F, 0.7F, 0.75F, 5.0F, 14.0F, 10.0F, 5.1F, 15.0F, 4.6F);
        } else if (def.durability() > 0) {
            p.durability(def.durability());
        } else {
            p.stacksTo(Math.max(1, Math.min(64, def.stack())));
        }
        if (def.food() != null) {
            p.food(new FoodProperties.Builder().nutrition(def.food().nutrition()).saturationModifier(def.food().saturation()).build());
        }
        return p;
    }

    private static BlockBehaviour.Properties blockProperties(BlockBehaviour.Properties p, TechTree.BlockDef def) {
        p.strength(def.hardness(), def.resistance()).sound(sound(def.sound()));
        p.mapColor(switch (def.sound()) {
            case "metal" -> MapColor.METAL;
            case "wood" -> MapColor.WOOD;
            case "sand" -> MapColor.SAND;
            case "gravel" -> MapColor.DIRT;
            case "deepslate" -> MapColor.DEEPSLATE;
            default -> MapColor.STONE;
        });
        if ("pickaxe".equals(def.tool())) {
            p.requiresCorrectToolForDrops();
        }
        return p;
    }

    private static SoundType sound(String s) {
        return switch (s == null ? "" : s) {
            case "metal" -> SoundType.METAL;
            case "wood" -> SoundType.WOOD;
            case "sand" -> SoundType.SAND;
            case "gravel" -> SoundType.GRAVEL;
            case "deepslate" -> SoundType.DEEPSLATE;
            default -> SoundType.STONE;
        };
    }

    private static SoundType stationSound(String family) {
        return switch (family == null ? "" : family) {
            case "timber", "farm" -> SoundType.WOOD;
            case "stone", "clay", "concrete" -> SoundType.STONE;
            default -> SoundType.METAL;
        };
    }
}
