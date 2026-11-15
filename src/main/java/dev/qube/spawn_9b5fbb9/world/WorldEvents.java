package dev.qube.spawn_9b5fbb9.world;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.command.TechTreeCommand;
import dev.qube.spawn_9b5fbb9.knowledge.Discovery;
import dev.qube.spawn_9b5fbb9.knowledge.Environment;
import dev.qube.spawn_9b5fbb9.knowledge.HandWork;
import dev.qube.spawn_9b5fbb9.knowledge.Journal;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;
import dev.qube.spawn_9b5fbb9.knowledge.Prospecting;
import dev.qube.spawn_9b5fbb9.knowledge.Requirements;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.station.StationBlockEntity;
import dev.qube.spawn_9b5fbb9.station.StationIndex;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Gameplay hooks into the vanilla world. */
@EventBusSubscriber(modid = spawn_9b5fbb9.MODID)
public final class WorldEvents {
    private static final TagKey<Block> REQUIRES_SHAFT = blockTag("requires/shaft_mine");
    private static final TagKey<Block> REQUIRES_DEEP = blockTag("requires/deep_mine");
    private static final TagKey<Block> REQUIRES_RIG = blockTag("requires/drilling_rig");
    private static final TagKey<Block> FIBER_PLANTS = blockTag("fiber_plants");
    private static final TagKey<Block> LATEX_TREES = blockTag("latex_trees");
    private static final TagKey<Item> HUNTING_WEAPONS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "hunting_weapons"));
    /** theory id -> blocks that look like plain rock until that theory is known */
    private static final Map<String, TagKey<Block>> RECOGNITION = new LinkedHashMap<>();

    static {
        for (TechTree.ResourceDef r : TechTree.get().resources) {
            if (r.recognisedBy() != null) {
                RECOGNITION.computeIfAbsent(r.recognisedBy(),
                        t -> blockTag("unrecognised_until/" + t.toLowerCase(java.util.Locale.ROOT).replace('-', '_')));
            }
        }
    }

    private WorldEvents() {
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, path));
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        resetTransientState();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        resetTransientState();
    }

    private static void resetTransientState() {
        StationIndex.clear();
        dev.qube.spawn_9b5fbb9.physics.Collapse.clear();
        dev.qube.spawn_9b5fbb9.engineering.Engineering.clear();
        Discovery.clear();
        HandWork.clear();
        Journal.clear();
        Prospecting.clear();
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        TechTreeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Society.checkMembership(player);
            Society.sync(player);
            if (KnowledgeData.get(player.level().getServer()).markWelcomed(player.getUUID().toString())) {
                player.sendSystemMessage(Component.literal("Every technology is earned. Press J to open your journal: "
                        + "gather what a function needs, then explain how it works to discover it.").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Discovery.forget(event.getEntity().getUUID());
        HandWork.forget(event.getEntity().getUUID());
        Journal.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HandWork.cancel(player);
            Society.sync(player);
        }
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HandWork.cancel(player);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HandWork.tick(player);
            if (player.tickCount % 40 == 0) {
                Society.checkMembership(player);
            }
        }
    }

    // ------------------------------------------------------------------ resources

    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        ServerPlayer player = event.getBreaker() instanceof ServerPlayer p ? p : null;

        if (gated(event, level, state, pos, player)) {
            return;
        }
        if (player == null || player.isCreative()) {
            return;
        }
        if (state.is(FIBER_PLANTS) && level.getRandom().nextDouble() < Config.dbl(Config.FIBER_CHANCE)) {
            drop(event, ModContent.stack("plant_fiber"));
        }
        if (state.is(LATEX_TREES) && level.getRandom().nextDouble() < Config.dbl(Config.LATEX_CHANCE)) {
            drop(event, ModContent.stack("rubber_latex"));
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            harvest(event, level, pos, player);
        }
    }

    /** Applies the access and recognition rules. @return true if the drops were replaced */
    private static boolean gated(BlockDropsEvent event, ServerLevel level, BlockState state, BlockPos pos, ServerPlayer player) {
        if (event.getDrops().isEmpty()) {
            // wrong tool, or rock that never drops anything: nothing to replace, and no free rubble
            if (player != null && state.is(REQUIRES_RIG)) {
                player.sendOverlayMessage(Component.literal("Only a Drilling rig can bring this up.").withStyle(ChatFormatting.GRAY));
            }
            return false;
        }
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        boolean vanilla = "minecraft".equals(key.getNamespace());
        if (vanilla && !Config.bool(Config.GATE_VANILLA_ORES)) {
            return false;
        }
        if (Config.bool(Config.GATE_ACCESS)) {
            String need = state.is(REQUIRES_RIG) ? "drilling_rig" : state.is(REQUIRES_DEEP) ? "deep_mine"
                    : state.is(REQUIRES_SHAFT) ? "shaft_mine" : null;
            if (need != null && !accessible(level, pos, need)) {
                rubble(event, level, state, pos, !"drilling_rig".equals(need));
                if (player != null) {
                    player.sendOverlayMessage(Component.literal(switch (need) {
                        case "drilling_rig" -> "Only a Drilling rig can bring this up.";
                        case "deep_mine" -> "This deep deposit can only be worked from a Deep mine (a shaft within 64 blocks).";
                        default -> "Without a Shaft mine nearby (within 48 blocks, above you) this deposit cannot be worked.";
                    }).withStyle(ChatFormatting.GRAY));
                }
                return true;
            }
        }
        if (Config.bool(Config.GATE_RECOGNITION)) {
            for (Map.Entry<String, TagKey<Block>> e : RECOGNITION.entrySet()) {
                if (state.is(e.getValue()) && (player == null || !Society.knows(player, e.getKey()))) {
                    rubble(event, level, state, pos, true);
                    if (player != null) {
                        player.sendOverlayMessage(Component.literal("Just a coloured rock to you. (Recognised once "
                                + theoryName(e.getKey()) + " is known.)").withStyle(ChatFormatting.GRAY));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static String theoryName(String id) {
        TechTree.FunctionDef f = TechTree.get().functionsById.get(id);
        return f == null ? id : f.name();
    }

    /** Shaft-mine deposits: below a formed shaft (or deep) mine's headframe, within its working radius. */
    public static boolean accessible(Level level, BlockPos pos, String need) {
        if ("drilling_rig".equals(need)) {
            return false;
        }
        String[] types = "deep_mine".equals(need) ? new String[]{"deep_mine"} : new String[]{"shaft_mine", "deep_mine"};
        for (String type : types) {
            TechTree.StationDef def = TechTree.get().stationsById.get(type);
            int r = def == null ? 48 : def.accessRadius();
            for (StationBlockEntity mine : StationIndex.formed(level, type)) {
                BlockPos head = mine.getBlockPos();
                double dx = head.getX() - pos.getX();
                double dz = head.getZ() - pos.getZ();
                if (pos.getY() < head.getY() && dx * dx + dz * dz <= (double) r * r) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void rubble(BlockDropsEvent event, ServerLevel level, BlockState state, BlockPos pos, boolean leaveRubble) {
        event.getDrops().clear();
        event.setDroppedExperience(0);
        if (!leaveRubble) {
            return;
        }
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        TechTree.OreDef ore = spawn_9b5fbb9.MODID.equals(key.getNamespace()) ? TechTree.get().oresByBlock.get(key.getPath()) : null;
        Item rock;
        if (ore != null && "sand".equals(ore.host())) {
            rock = Items.SAND;
        } else if (ore != null && "dirt".equals(ore.host())) {
            rock = Items.DIRT;
        } else if (level.dimension() == Level.NETHER) {
            rock = Items.NETHERRACK;
        } else if (pos.getY() < 0 || key.getPath().contains("deepslate")) {
            rock = Items.COBBLED_DEEPSLATE;
        } else {
            rock = Items.COBBLESTONE;
        }
        drop(event, new ItemStack(rock));
    }

    private static void drop(BlockDropsEvent event, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        ItemEntity e = new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        e.setDefaultPickUpDelay();
        event.getDrops().add(e);
    }

    /** Harvesting (P-028): mature crops in a formed Grain field, cut with a sickle, give Grain. */
    private static void harvest(BlockDropsEvent event, ServerLevel level, BlockPos pos, ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        Item sickle = ModContent.item("sickle");
        if (sickle == null || !held.is(sickle) || !Society.knows(player, "P-028")) {
            return;
        }
        StationBlockEntity field = null;
        Vec3 at = Vec3.atCenterOf(pos);
        for (StationBlockEntity f : StationIndex.formed(level, "grain_field")) {
            if (f.bounds().expandTowards(0, 2, 0).contains(at)) {
                field = f;
                break;
            }
        }
        if (field == null) {
            return;
        }
        int grain = 1;
        if (Society.knows(player, "T-80")) {
            grain++; // crop rotation ends wasted fallow years
        }
        Item seedDrill = ModContent.item("seed_drill");
        if (seedDrill != null && Requirements.count(player.getInventory().getNonEquipmentItems(), s -> s.is(seedDrill)) > 0) {
            grain++;
        }
        AABB fieldBounds = field.bounds();
        if (!StationIndex.formedNear(level, "irrigation_canal", fieldBounds, 12).isEmpty()) {
            grain *= 2; // irrigation multiplies yield and frees it from rainfall
        }
        drop(event, new ItemStack(ModContent.item("grain"), grain));
        held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

    /** Hunting (P-006): a wild animal killed with a hunting weapon leaves a carcass. */
    @SubscribeEvent
    static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        var victim = event.getEntity();
        if (!victim.is(Environment.WILD_GAME) || victim.isBaby()) {
            return;
        }
        if (!player.getMainHandItem().is(HUNTING_WEAPONS) || !Society.knows(player, "P-006")) {
            return;
        }
        ItemStack carcass = ModContent.stack("carcass");
        if (!carcass.isEmpty()) {
            ItemEntity e = new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(), carcass);
            e.setDefaultPickUpDelay();
            event.getDrops().add(e);
        }
    }
}
