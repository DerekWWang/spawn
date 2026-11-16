package dev.qube.spawn_9b5fbb9.content;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Portable fired vessels use vanilla container components, so saving and transport are lossless. */
public final class Pottery {
    public static boolean isPot(ItemStack stack){return stack.is(SurvivalContent.item("fired_pot"));}
    public static String quality(ItemStack stack){String quality=ItemAging.data(stack).getStringOr("potQuality","fine");return quality.equals("fine")||quality.equals("standard")?quality:"rough";}
    public static int capacity(ItemStack stack){return switch(quality(stack)){case "rough"->2;case "standard"->3;default->4;};}
    public static double preservationRate(ItemStack stack){return Math.min(1,ItemAging.sealedRate()*(quality(stack).equals("rough")?3:quality(stack).equals("standard")?1.5:1));}
    public static int waterUnits(ItemStack stack){return isPot(stack)?Math.clamp(ItemAging.data(stack).getIntOr("water",0),0,capacity(stack)):0;}
    public static boolean safeWater(ItemStack stack){return ItemAging.data(stack).getBooleanOr("waterSafe",false);}
    public static boolean consumeWater(ItemStack stack){int amount=waterUnits(stack);var tag=ItemAging.data(stack);if(amount<=0||tag.getBooleanOr("sealed",false))return false;tag.putInt("water",amount-1);ItemAging.data(stack,tag);wear(stack,1);return true;}
    private static void wear(ItemStack stack,int amount){wear(stack,amount,null);}
    private static void wear(ItemStack stack,int amount,LivingEntity actor){stack.setDamageValue(stack.getDamageValue()+amount);if(stack.getDamageValue()>=stack.getMaxDamage()){
        if(actor!=null&&actor.level() instanceof ServerLevel level){for(var content:stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).itemCopies().toList())if(!content.isEmpty())actor.spawnAtLocation(level,content);if(ItemAging.data(stack).getBooleanOr("sealed",false))actor.spawnAtLocation(level,new ItemStack(SurvivalContent.item("pot_lid")));actor.spawnAtLocation(level,new ItemStack(SurvivalContent.item("ceramic_shard"),2));}
        stack.set(DataComponents.CONTAINER,ItemContainerContents.EMPTY);stack.setCount(0);
    }}
    public static void init(){NeoForge.EVENT_BUS.addListener(Pottery::use);NeoForge.EVENT_BUS.addListener(Pottery::tick);NeoForge.EVENT_BUS.addListener(Pottery::tooltip);}
    public static boolean heat(ServerLevel level,BlockPos pos){var state=level.getBlockState(pos);return (state.is(Blocks.CAMPFIRE)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))||state.is(Blocks.FIRE)||state.is(Blocks.LAVA);}
    private static boolean contentsEmpty(ItemStack pot){return pot.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).itemCopies().allMatch(ItemStack::isEmpty);}
    public static String action(LivingEntity actor,String operation){
        if(!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||!SurvivalApi.isHumanoid(actor))return "A living humanoid must perform vessel work.";
        if(SurvivalApi.state(actor).manipulation()<=.05||SurvivalApi.state(actor).mental()<=.05)return "Your body cannot perform vessel work.";
        ItemStack pot=actor.getMainHandItem();if(!isPot(pot))return "Hold a fired pot in your main hand.";
        ItemAging.update(pot,level,1);var tag=ItemAging.data(pot);boolean sealed=tag.getBooleanOr("sealed",false);
        switch(operation){
            case "fill" -> {
                if(sealed)return "Open the pot first.";if(!contentsEmpty(pot))return "Remove stored food first.";
                boolean found=false;for(BlockPos pos:BlockPos.betweenClosed(actor.blockPosition().offset(-3,-2,-3),actor.blockPosition().offset(3,2,3)))if(level.getFluidState(pos).is(FluidTags.WATER)){found=true;break;}
                if(!found)return "Stand beside surface water.";tag.putInt("water",capacity(pot));tag.putBoolean("waterSafe",false);tag.putLong("boilProgress",0);tag.putBoolean("boiling",false);ItemAging.data(pot,tag);return "Collected "+capacity(pot)+" untreated water portions.";
            }
            case "boil" -> {
                if(sealed)return "Open the pot before boiling.";if(waterUnits(pot)<=0)return "Collect water first.";
                BlockPos fire=null;for(BlockPos pos:BlockPos.betweenClosed(actor.blockPosition().offset(-3,-2,-3),actor.blockPosition().offset(3,2,3)))if(actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))<=16&&heat(level,pos)){fire=pos.immutable();break;}
                if(fire==null)return "Stand beside a lit fire.";tag.putLong("boilAt",fire.asLong());tag.putBoolean("boiling",true);ItemAging.data(pot,tag);return "Hold the open pot near the fire for 1000 ticks.";
            }
            case "seal" -> {
                if(sealed){tag.putBoolean("sealed",false);ItemAging.data(pot,tag);SurvivalApi.give(actor,new ItemStack(SurvivalContent.item("pot_lid")));return "Opened pot and recovered its lid.";}
                ItemStack lid=actor.getOffhandItem();if(!lid.is(SurvivalContent.item("pot_lid")))return "Hold a pottery lid in your offhand.";lid.shrink(1);tag.putBoolean("sealed",true);tag.putBoolean("boiling",false);ItemAging.data(pot,tag);return "Pot sealed; food lasts "+String.format(java.util.Locale.ROOT,"%.1f",1/preservationRate(pot))+" times longer.";
            }
            case "store","retrieve" -> {
                if(waterUnits(pot)>0)return "Empty the water before storing food.";
                if(sealed){tag.putBoolean("sealed",false);ItemAging.data(pot,tag);SurvivalApi.give(actor,new ItemStack(SurvivalContent.item("pot_lid")));}
                List<ItemStack> items=new ArrayList<>(pot.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).itemCopies().toList());
                if(operation.equals("store")){ItemStack food=actor.getOffhandItem();if(!ItemAging.isFood(food)||food.has(DataComponents.CONTAINER))return "Hold unpackaged food in your offhand.";if(items.size()>=capacity(pot))return "Pot is full.";ItemAging.update(food,level,1);items.add(food.copy());food.setCount(0);pot.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));wear(pot,1,actor);return "Food stored; use a lid to preserve it.";}
                if(items.isEmpty())return "Pot is empty.";ItemStack out=items.removeLast();pot.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));SurvivalApi.give(actor,out);wear(pot,1,actor);return "Retrieved food; pot remains open.";
            }
            default -> {return "Vessel actions: fill, boil, seal, store, retrieve.";}
        }
    }
    private static void use(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level)||!isPot(event.getItemStack())||event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        Player player=event.getEntity();ItemStack pot=event.getItemStack();var tag=ItemAging.data(pot);
        if(player.isShiftKeyDown()){
            event.setCanceled(true);String operation=player.getOffhandItem().is(SurvivalContent.item("pot_lid"))?"seal":player.getOffhandItem().isEmpty()?"retrieve":"store";player.sendOverlayMessage(Component.literal(action(player,operation)));return;
        }
        if(!pot.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).itemCopies().allMatch(ItemStack::isEmpty))return;
        if(dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.irrigable(level,event.getPos())&&waterUnits(pot)>0){
            event.setCanceled(true);player.sendOverlayMessage(Component.literal(dev.qube.spawn_9b5fbb9.controllers.WorldActions.start(player,"irrigate",event.getPos()).message()));return;
        }
        if(level.getFluidState(event.getPos()).is(FluidTags.WATER)||level.getFluidState(event.getPos().relative(event.getFace()==null?net.minecraft.core.Direction.UP:event.getFace())).is(FluidTags.WATER)){
            event.setCanceled(true);player.sendOverlayMessage(Component.literal(action(player,"fill")));
        }else if(heat(level,event.getPos())&&waterUnits(pot)>0&&!tag.getBooleanOr("sealed",false)){
            event.setCanceled(true);player.sendOverlayMessage(Component.literal(action(player,"boil")));
        }
    }
    private static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity player)||!(player.level() instanceof ServerLevel level)||player.tickCount%20!=0)return;
        ItemStack pot=player.getMainHandItem();if(!isPot(pot))return;var tag=ItemAging.data(pot);if(!tag.getBooleanOr("boiling",false))return;
        BlockPos at=BlockPos.of(tag.getLongOr("boilAt",0));
        if(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at))>16||!heat(level,at)||tag.getBooleanOr("sealed",false)){tag.putBoolean("boiling",false);tag.putLong("boilProgress",0);}
        else{long progress=tag.getLongOr("boilProgress",0)+20;tag.putLong("boilProgress",progress);if(progress>=1000){tag.putBoolean("waterSafe",true);tag.putBoolean("boiling",false);wear(pot,1);if(player instanceof Player human)human.sendOverlayMessage(Component.literal("Water boiled: safe to drink."));}}
        if(!pot.isEmpty())ItemAging.data(pot,tag);
    }
    private static void tooltip(ItemTooltipEvent event){
        ItemStack stack=event.getItemStack();if(isPot(stack)){var tag=ItemAging.data(stack);event.getToolTip().add(Component.literal(quality(stack)+" jar: "+capacity(stack)+" food stacks / water portions"));event.getToolTip().add(Component.literal(tag.getBooleanOr("sealed",false)?"Sealed: food lasts "+String.format(java.util.Locale.ROOT,"%.1f",1/preservationRate(stack))+"x longer":"Open: shift-use with offhand food to store; empty offhand to retrieve"));event.getToolTip().add(Component.literal("Water: "+waterUnits(stack)+"/"+capacity(stack)+" ("+(safeWater(stack)?"boiled":"untreated")+"). Shift-use with lid to seal."));}
        if(ItemAging.isFood(stack))event.getToolTip().add(Component.literal(ItemAging.spoiled(stack)?"SPOILED — illness risk":"Freshness age: "+(int)(ItemAging.data(stack).getDoubleOr("foodAge",0)/24000*100)/100.0+" days"));
    }
}
