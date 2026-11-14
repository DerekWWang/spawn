package dev.qube.spawn_9b5fbb9.item;

import java.util.function.Consumer;

import dev.qube.spawn_9b5fbb9.knowledge.Treatises;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A written-down theory. Reading it teaches the theory to a literate society that can follow it. */
public class TreatiseItem extends Item {
    public TreatiseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp && Treatises.read(sp, stack) && !sp.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = stack.get(ModContent.TREATISE_SUBJECT.get());
        TechTree.FunctionDef f = id == null ? null : TechTree.get().functionsById.get(id);
        return f == null ? super.getName(stack) : Component.literal("Treatise on " + f.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String author = stack.get(ModContent.TREATISE_AUTHOR.get());
        if (author != null) {
            builder.accept(Component.literal("Written by " + author).withStyle(ChatFormatting.GRAY));
        }
        builder.accept(Component.literal("Use to study it. Your society must know Writing and the theory's prerequisites.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
