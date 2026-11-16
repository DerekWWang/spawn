package dev.qube.spawn_9b5fbb9.warfare;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Use while loaded to fire. Use while empty to load: hold through the whole drill (bite the
 * cartridge, prime, pour, ram), which consumes one paper cartridge; letting go abandons it. */
public class MusketItem extends Item {
    public MusketItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        ItemStack musket=player.getItemInHand(hand);
        if(Firearms.loaded(musket)){
            if(level instanceof ServerLevel server){
                var result=Firearms.fire(player,musket,server,player.getLookAngle());player.sendOverlayMessage(Component.literal(result.message()));
                player.getCooldowns().addCooldown(musket,10);
            }
            return InteractionResult.SUCCESS;
        }
        if(!Firearms.hasCartridge(player)){if(!level.isClientSide())player.sendOverlayMessage(Component.literal("No paper cartridges"));return InteractionResult.FAIL;}
        player.getPersistentData().putDouble("survivalReload",0);player.startUsingItem(hand);return InteractionResult.CONSUME;
    }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack){return Firearms.loaded(stack)?ItemUseAnimation.NONE:ItemUseAnimation.CROSSBOW;}
    @Override public int getUseDuration(ItemStack stack,LivingEntity user){return 72000;}
    @Override public void onUseTick(Level level,LivingEntity user,ItemStack musket,int remaining){
        if(!(level instanceof ServerLevel server)||!(user instanceof Player player))return;
        double progress=player.getPersistentData().getDoubleOr("survivalReload",0)+Firearms.reloadRate(player);
        if(progress<Firearms.RELOAD_WORK){
            player.getPersistentData().putDouble("survivalReload",progress);
            if(player.tickCount%20==0)player.sendOverlayMessage(Component.literal("Loading "+Math.round(100*progress/Firearms.RELOAD_WORK)+"%"));
            return;
        }
        player.getPersistentData().remove("survivalReload");player.stopUsingItem();
        if(!Firearms.takeCartridge(player)){player.sendOverlayMessage(Component.literal("No paper cartridges"));return;}
        Firearms.setLoaded(musket,true);player.sendOverlayMessage(Component.literal("Loaded"));
        server.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.CROSSBOW_LOADING_END.value(),SoundSource.PLAYERS,1,.7f);
    }
    @Override public boolean releaseUsing(ItemStack stack,Level level,LivingEntity user,int remaining){user.getPersistentData().remove("survivalReload");return false;}
}
