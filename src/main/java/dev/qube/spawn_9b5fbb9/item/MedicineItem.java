package dev.qube.spawn_9b5fbb9.item;

import java.util.function.Consumer;

import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A remedy or drug from the medicine route: use it to apply its effect to yourself. */
public class MedicineItem extends Item {
    private final TechTree.Medicine medicine;

    public MedicineItem(Properties properties, TechTree.Medicine medicine) {
        super(properties);
        this.medicine = medicine;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            Identifier id = Identifier.tryParse(medicine.effect());
            if (id != null) {
                BuiltInRegistries.MOB_EFFECT.get(id).ifPresent(effect ->
                        player.addEffect(new MobEffectInstance(effect, Math.max(1, medicine.seconds()) * 20, medicine.amplifier())));
            }
            if (medicine.heal() > 0) {
                player.heal(medicine.heal());
            }
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.6F, 1.1F);
            stack.consume(1, player);
            player.getCooldowns().addCooldown(stack, 40);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String what = medicine.effect().substring(medicine.effect().indexOf(':') + 1).replace('_', ' ');
        builder.accept(Component.literal("Use: " + what + " for " + medicine.seconds() + "s"
                + (medicine.heal() > 0 ? ", heals " + medicine.heal() / 2.0 + " hearts" : "")).withStyle(ChatFormatting.BLUE));
    }
}
