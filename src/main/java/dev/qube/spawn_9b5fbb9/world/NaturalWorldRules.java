package dev.qube.spawn_9b5fbb9.world;

import java.util.Set;
import dev.qube.spawn_9b5fbb9.content.ItemAging;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Registry entries stay intact for saves; admission and inventories enforce the rule. */
public final class NaturalWorldRules {
    public static final TagKey<EntityType<?>> FORBIDDEN_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("spawn_9b5fbb9","forbidden_entities"));
    public static final TagKey<Item> FORBIDDEN_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("spawn_9b5fbb9","forbidden_items"));
    private static final Set<String> ITEMS = Set.of("ender_pearl","ender_eye","blaze_rod","blaze_powder","ghast_tear","magma_cream","nether_star","totem_of_undying","dragon_breath","dragon_egg","elytra","experience_bottle","enchanted_book","enchanting_table","brewing_stand","beacon","redstone","redstone_block","redstone_torch","redstone_wall_torch","repeater","comparator","observer","piston","sticky_piston","dispenser","dropper","daylight_detector","target","sculk_sensor","calibrated_sculk_sensor","sculk_shrieker","end_crystal","respawn_anchor","lodestone","fire_charge");
    public static void init() {
        NeoForge.EVENT_BUS.addListener(NaturalWorldRules::join);
        NeoForge.EVENT_BUS.addListener(NaturalWorldRules::tick);
        NeoForge.EVENT_BUS.addListener(NaturalWorldRules::interact);
        NeoForge.EVENT_BUS.addListener(NaturalWorldRules::craft);
        NeoForge.EVENT_BUS.addListener(NaturalWorldRules::portal);
        NeoForge.EVENT_BUS.addListener(NaturalWorldRules::travel);
        NeoForge.EVENT_BUS.addListener(NaturalWorldRules::use);
        NeoForge.EVENT_BUS.addListener(NaturalWorldRules::attack);
    }
    public static boolean prohibited(Entity entity) { return entity.getType().builtInRegistryHolder().is(FORBIDDEN_ENTITIES); }
    public static boolean prohibited(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (stack.is(FORBIDDEN_ITEMS) || ITEMS.contains(id) || id.startsWith("netherite_") || id.contains("shulker") || id.contains("chorus") || id.startsWith("purpur") || id.startsWith("end_stone") || id.endsWith("copper_bulb") || id.endsWith("_spawn_egg") || id.contains("command_block")) return true;
        if (stack.getItem() instanceof PotionItem) {
            var potion = stack.get(DataComponents.POTION_CONTENTS);
            return !stack.is(Items.POTION) || potion == null || !potion.is(Potions.WATER);
        }
        return stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.TIPPED_ARROW);
    }
    public static void sanitize(ItemStack stack) {
        if (prohibited(stack)) { stack.setCount(0); return; }
        stack.remove(DataComponents.ENCHANTMENTS);
        stack.remove(DataComponents.STORED_ENCHANTMENTS);
        var container=stack.get(DataComponents.CONTAINER);
        if(container!=null){var contents=container.itemCopies().toList();for(var entry:contents)if(!entry.isEmpty())sanitize(entry);stack.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(contents));}
    }
    private static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (prohibited(event.getEntity())) { event.setCanceled(true); event.getEntity().discard(); }
        if (event.getEntity() instanceof ItemEntity item) { sanitize(item.getItem()); if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) ItemAging.exposed(item.getItem(),level,1); if (item.getItem().isEmpty()) { event.setCanceled(true); item.discard(); } }
    }
    private static void tick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || event.getEntity().tickCount % 20 != 0) return;
        if (prohibited(event.getEntity())) { event.getEntity().discard(); return; }
        if(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living)for(var slot:net.minecraft.world.entity.EquipmentSlot.values()){var stack=living.getItemBySlot(slot);sanitize(stack);ItemAging.update(stack,(net.minecraft.server.level.ServerLevel)living.level(),1);}
        if(event.getEntity() instanceof net.minecraft.world.entity.npc.villager.AbstractVillager villager){villager.getOffers().removeIf(offer->prohibited(offer.getResult())||dev.qube.spawn_9b5fbb9.content.Production.requiresStation(offer.getResult())||prohibited(offer.getCostA())||prohibited(offer.getCostB()));for(var offer:villager.getOffers())sanitize(offer.getResult());}
        if (event.getEntity() instanceof Player player) {
            for (int i=0;i<player.getInventory().getContainerSize();i++) {
                ItemStack stack=player.getInventory().getItem(i); sanitize(stack); ItemAging.update(stack, (net.minecraft.server.level.ServerLevel)player.level(),1);
            }
            // Loaded/accessed menus, including hoppers and loot containers, are scrubbed before further interaction.
            if (player.containerMenu != null) for (var slot : player.containerMenu.slots) { sanitize(slot.getItem()); ItemAging.update(slot.getItem(),(net.minecraft.server.level.ServerLevel)player.level(),ItemAging.storageRate()); }
        }
        if (event.getEntity() instanceof ItemEntity item) ItemAging.weather(item);
    }
    private static void interact(PlayerInteractEvent.RightClickBlock event) {
        sanitize(event.getItemStack());
        String block=BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).getPath();
        if (ITEMS.contains(block) || block.equals("nether_portal") || block.equals("end_portal") || block.equals("furnace") || block.equals("blast_furnace") || block.equals("smoker")) event.setCanceled(true);
    }
    private static void craft(PlayerEvent.ItemCraftedEvent event) { sanitize(event.getCrafting()); }
    private static void use(PlayerInteractEvent.RightClickItem event){boolean denied=prohibited(event.getItemStack());sanitize(event.getItemStack());if(denied)event.setCanceled(true);}
    private static void attack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event){boolean denied=prohibited(event.getEntity().getMainHandItem());sanitize(event.getEntity().getMainHandItem());if(denied)event.setCanceled(true);}
    private static void portal(BlockEvent.PortalSpawnEvent event) { event.setCanceled(true); }
    private static void travel(EntityTravelToDimensionEvent event) {
        String path=event.getDimension().identifier().getPath();
        if (path.equals("the_nether") || path.equals("the_end")) event.setCanceled(true);
    }
}
