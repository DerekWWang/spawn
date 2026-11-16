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
            meta.remove("survivalGridResult");meta.remove("survivalGridWork");meta.remove("survivalGridExposure");return;
        }
        if(dev.qube.spawn_9b5fbb9.content.Production.requiresStation(menu.getResultSlot().getItem())){meta.remove("survivalGridResult");meta.remove("survivalGridWork");return;}
        var result=menu.getResultSlot().getItem();String key=BuiltInRegistries.ITEM.getKey(result.getItem())+"/"+result.getCount();
        if(!key.equals(meta.getStringOr("survivalGridResult",""))){meta.putString("survivalGridResult",key);meta.putDouble("survivalGridWork",0);meta.putLong("survivalGridExposure",0);}
        double work=meta.getDoubleOr("survivalGridWork",0),capacity=SkillApi.manualRate(state,SkillApi.craftingSkill(result));
        if(work<REQUIRED&&capacity>.05){long used=Math.min(20,(long)Math.ceil((REQUIRED-work)/capacity));meta.putDouble("survivalGridWork",Math.min(REQUIRED,work+used*capacity));meta.putLong("survivalGridExposure",Math.min(1000000,meta.getLongOr("survivalGridExposure",0)+used));state.hunger=Math.max(0,state.hunger-used*.0005);LaborGuard.worked(actor);}
    }
    public static boolean mayTake(Player player,Slot slot){
        if(!(player.level() instanceof ServerLevel)||SurvivalApi.exempt(player))return true;
        if(slot instanceof net.minecraft.world.inventory.MerchantResultSlot)return !dev.qube.spawn_9b5fbb9.content.Production.requiresStation(slot.getItem());
        if(!(slot instanceof ResultSlot))return true;
        if(dev.qube.spawn_9b5fbb9.content.Production.requiresStation(slot.getItem())){notice(player,"This product needs its specialist workstation");return false;}
        var body=SurvivalApi.state(player);
        if(body.mental()<=0||body.manipulation()<=.05)return false;
        if(LaborGuard.worldBusy(player)||LaborGuard.forgeBusy(player))return false;
        if(slot.getItem().isEmpty())return true;
        boolean ready=player.getPersistentData().getDoubleOr("survivalGridWork",0)>=REQUIRED;
        if(!ready)notice(player,"Handcrafting "+Math.round(player.getPersistentData().getDoubleOr("survivalGridWork",0))+"/100 — keep ingredients in place; work follows your capacities");
        return ready;
    }
    public static void completed(Player player,net.minecraft.world.item.ItemStack output){if(player.level() instanceof ServerLevel){var meta=player.getPersistentData();long ticks=meta.getDoubleOr("survivalGridWork",0)>=REQUIRED?meta.getLongOr("survivalGridExposure",0):0;meta.putDouble("survivalGridWork",0);meta.remove("survivalGridResult");meta.remove("survivalGridExposure");var skill=SkillApi.craftingSkill(output);if(skill!=null)SkillApi.awardWork(player,skill,ticks,1);}}
    private static void notice(Player player,String text){if(player instanceof net.minecraft.server.level.ServerPlayer server&&server.connection!=null)player.sendOverlayMessage(Component.literal(text));}
}
