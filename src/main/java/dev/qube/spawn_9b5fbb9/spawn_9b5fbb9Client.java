package dev.qube.spawn_9b5fbb9;

import java.util.ArrayList;
import java.util.List;

import dev.qube.spawn_9b5fbb9.client.ClientNetwork;
import dev.qube.spawn_9b5fbb9.client.ClientState;
import dev.qube.spawn_9b5fbb9.client.JournalScreen;
import dev.qube.spawn_9b5fbb9.client.StationScreen;
import dev.qube.spawn_9b5fbb9.knowledge.Requirements;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.station.StationBlock;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = spawn_9b5fbb9.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = spawn_9b5fbb9.MODID, value = Dist.CLIENT)
public class spawn_9b5fbb9Client {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "techtree"));
    public static final KeyMapping JOURNAL_KEY = new KeyMapping("key.spawn_9b5fbb9.journal", InputConstants.KEY_J, CATEGORY);

    public spawn_9b5fbb9Client(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(JOURNAL_KEY);
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.STATION_MENU.get(), StationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        ClientNetwork.register(event);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (JOURNAL_KEY.consumeClick()) {
            if (mc.player != null && mc.gui.screen() == null) {
                mc.gui.setScreen(new JournalScreen());
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }

    /** Shows where each item sits in the tree: what makes it, and whether your society can. */
    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        List<Component> tip = event.getToolTip();
        TechTree tree = TechTree.get();
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (spawn_9b5fbb9.MODID.equals(key.getNamespace())) {
            TechTree.ItemDef def = tree.itemsById.get(key.getPath());
            if (def != null) {
                addItemLines(tree, def, tip);
                return;
            }
            TechTree.OreDef ore = tree.oresByBlock.get(key.getPath());
            if (ore != null) {
                TechTree.ResourceDef r = tree.resourcesById.get(ore.resource());
                if (r != null) {
                    tip.add(Component.literal("Deposit of " + r.name()).withStyle(ChatFormatting.GRAY));
                    addAccess(tree, r, tip);
                }
            }
            return;
        }
        // vanilla items that count as a natural resource of the tree
        List<String> names = new ArrayList<>();
        for (TechTree.ResourceDef r : tree.resources) {
            if ("tag".equals(r.source()) && stack.is(Requirements.resourceTag(r.id()))) {
                names.add(r.name());
            }
        }
        if (!names.isEmpty()) {
            tip.add(Component.literal("Tech tree resource: " + String.join(", ", names)).withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addItemLines(TechTree tree, TechTree.ItemDef def, List<Component> tip) {
        if ("resource".equals(def.category())) {
            TechTree.ResourceDef r = tree.resourcesById.get(def.resource());
            tip.add(Component.literal("Natural resource").withStyle(ChatFormatting.DARK_GREEN));
            if (r != null) {
                if (r.how() != null) {
                    tip.add(Component.literal(r.how()).withStyle(ChatFormatting.GRAY));
                }
                addAccess(tree, r, tip);
            }
            return;
        }
        if (def.era() > 0) {
            tip.add(Component.literal("Era " + def.era() + " - " + def.sheetCategory()).withStyle(ChatFormatting.DARK_GRAY));
        }
        List<String> makers = new ArrayList<>();
        boolean anyKnown = false;
        for (String fid : def.madeBy()) {
            TechTree.FunctionDef f = tree.functionsById.get(fid);
            if (f != null) {
                makers.add(f.name());
                anyKnown |= ClientState.knows(fid);
            }
        }
        if (!makers.isEmpty()) {
            tip.add(Component.literal("Made by: " + String.join(", ", makers))
                    .withStyle(anyKnown ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        if (def.usedBy() > 0) {
            tip.add(Component.literal("Used by " + def.usedBy() + " function" + (def.usedBy() == 1 ? "" : "s"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (def.station()) {
            TechTree.StationDef s = tree.stationsById.get(def.id());
            if (s != null) {
                StationBlock.describe(s, line -> tip.add(Component.literal(line).withStyle(ChatFormatting.GOLD)));
            }
        } else if (def.capstone()) {
            tip.add(Component.literal("Capstone: a mark of what your society has built.").withStyle(ChatFormatting.GOLD));
        }
    }

    private static void addAccess(TechTree tree, TechTree.ResourceDef r, List<Component> tip) {
        if (r.recognisedBy() != null) {
            TechTree.FunctionDef t = tree.functionsById.get(r.recognisedBy());
            boolean known = ClientState.knows(r.recognisedBy());
            tip.add(Component.literal("Recognised once " + (t == null ? r.recognisedBy() : t.name()) + " is known")
                    .withStyle(known ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (r.access() != null) {
            tip.add(Component.literal("Worked only from a " + r.access().replace('_', ' ')).withStyle(ChatFormatting.YELLOW));
        }
    }
}
