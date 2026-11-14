package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.ArrayList;
import java.util.List;

import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Writing lets theories be taught or traded: a society that knows Writing can set a theory down
 * in a treatise, and a literate society that already holds the theory's prerequisites can learn
 * it by reading.
 */
public final class Treatises {
    public static final String WRITING = "T-11";

    private Treatises() {
    }

    public static void write(ServerPlayer player, String functionId) {
        FunctionDef f = TechTree.get().functionsById.get(functionId);
        if (f == null || !f.theoretical()) {
            return;
        }
        if (!Society.knows(player, WRITING)) {
            player.sendOverlayMessage(Component.literal("Your society must know Writing to record a theory."));
            return;
        }
        if (!Society.knows(player, f.id())) {
            player.sendOverlayMessage(Component.literal("You can only write down what your society knows."));
            return;
        }
        var inv = player.getInventory().getNonEquipmentItems();
        java.util.function.Predicate<ItemStack> tablet = s -> s.is(ModContent.item("clay_tablet"));
        java.util.function.Predicate<ItemStack> paper = s -> s.is(ModContent.item("paper"));
        java.util.function.Predicate<ItemStack> ink = s -> s.is(ModContent.item("ink"));
        if (Requirements.count(inv, paper) >= 1 && Requirements.count(inv, ink) >= 1) {
            take(inv, paper);
            take(inv, ink);
        } else if (Requirements.count(inv, tablet) >= 1) {
            take(inv, tablet);
        } else {
            player.sendOverlayMessage(Component.literal("You need a Clay tablet, or Paper and Ink, to write a treatise."));
            return;
        }
        ItemStack book = ModContent.stack("treatise");
        book.set(ModContent.TREATISE_SUBJECT.get(), f.id());
        book.set(ModContent.TREATISE_AUTHOR.get(), Society.displayName(player));
        if (!player.getInventory().add(book)) {
            player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendOverlayMessage(Component.literal("You wrote a treatise on " + f.name() + "."));
        Journal.request(player);
    }

    private static void take(List<ItemStack> inv, java.util.function.Predicate<ItemStack> match) {
        for (ItemStack s : inv) {
            if (match.test(s)) {
                s.shrink(1);
                return;
            }
        }
    }

    /** @return true if the treatise was used up */
    public static boolean read(ServerPlayer player, ItemStack stack) {
        String id = stack.get(ModContent.TREATISE_SUBJECT.get());
        FunctionDef f = id == null ? null : TechTree.get().functionsById.get(id);
        if (f == null) {
            player.sendOverlayMessage(Component.literal("The pages are blank."));
            return false;
        }
        if (Society.knows(player, f.id())) {
            player.sendOverlayMessage(Component.literal("Your society already understands " + f.name() + "."));
            return false;
        }
        if (!Society.knows(player, WRITING)) {
            player.sendOverlayMessage(Component.literal("Nobody in your society can read. Discover Writing first."));
            return false;
        }
        List<String> lacking = new ArrayList<>();
        for (String t : f.theory()) {
            if (!Society.knows(player, t)) {
                FunctionDef td = TechTree.get().functionsById.get(t);
                lacking.add(td == null ? t : td.name());
            }
        }
        if (!lacking.isEmpty()) {
            player.sendSystemMessage(Component.literal("You cannot follow the argument without: " + String.join(", ", lacking))
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }
        Society.grant(player.level().getServer(), Society.keyOf(player), f.id(), player);
        return true;
    }
}
