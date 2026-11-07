package dev.qube.spawn_9b5fbb9.survival;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;

/** Real loaded manual work, including vanilla grid crafting and shift-click transactions. */
public final class CraftingGuard {
    public static final double REQUIRED=100;
    private CraftingGuard(){}
    public static void tick(Player actor,SurvivalState state){
        var meta=actor.getPersistentData();
        if(LaborGuard.worldBusy(actor)||LaborGuard.forgeBusy(actor)||actor.level().getGameTime()-meta.getLongOr("survivalManualWorkAt",Long.MIN_VALUE/2)<20)return;
        if(!(actor.containerMenu instanceof AbstractCraftingMenu menu)||menu.getResultSlot().getItem().isEmpty()){
            meta.remove("survivalGridResult");meta.remove("survivalGridWork");return;
        }
        var result=menu.getResultSlot().getItem();String key=BuiltInRegistries.ITEM.getKey(result.getItem())+"/"+result.getCount();
        if(!key.equals(meta.getStringOr("survivalGridResult",""))){meta.putString("survivalGridResult",key);meta.putDouble("survivalGridWork",0);}
        double work=meta.getDoubleOr("survivalGridWork",0),capacity=Math.min(state.mental(),state.manipulation());
        if(work<REQUIRED&&capacity>.05){meta.putDouble("survivalGridWork",Math.min(REQUIRED,work+20*capacity));state.hunger=Math.max(0,state.hunger-.01);LaborGuard.worked(actor);}
    }
    public static boolean mayTake(Player player,Slot slot){
        if(!(slot instanceof ResultSlot)||!(player.level() instanceof ServerLevel)||SurvivalApi.exempt(player))return true;
        var body=SurvivalApi.state(player);
        if(body.mental()<=0||body.manipulation()<=.05)return false;
        if(LaborGuard.worldBusy(player)||LaborGuard.forgeBusy(player))return false;
        if(slot.getItem().isEmpty())return true;
        boolean ready=player.getPersistentData().getDoubleOr("survivalGridWork",0)>=REQUIRED;
        if(!ready)player.sendOverlayMessage(Component.literal("Handcrafting "+Math.round(player.getPersistentData().getDoubleOr("survivalGridWork",0))+"/100 — keep ingredients in place; work follows your capacities"));
        return ready;
    }
    public static void completed(Player player){if(player.level() instanceof ServerLevel){player.getPersistentData().putDouble("survivalGridWork",0);player.getPersistentData().remove("survivalGridResult");}}
}
