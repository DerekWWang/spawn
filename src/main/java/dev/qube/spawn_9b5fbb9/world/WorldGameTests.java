package dev.qube.spawn_9b5fbb9.world;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;

public final class WorldGameTests {
    public static void containerProhibition(GameTestHelper helper){
        BlockPos pos=helper.absolutePos(new BlockPos(1,1,1));helper.getLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),3);
        var chest=(net.minecraft.world.Container)helper.getLevel().getBlockEntity(pos);
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL));
        chest.setItem(1,net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,net.minecraft.world.item.alchemy.Potions.WATER));
        helper.assertTrue(chest.getItem(0).isEmpty(),"Container insertion/access cannot retain ender pearls");
        helper.assertFalse(chest.getItem(1).isEmpty(),"Ordinary water bottles must remain usable");
        helper.succeed();
    }
    public static void prohibitedAdditions(GameTestHelper helper) {
        for (EntityType<?> type : new EntityType<?>[] {EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER, EntityTypes.ENDERMAN, EntityTypes.HOGLIN, EntityTypes.STRIDER, EntityTypes.RAVAGER, EntityTypes.SULFUR_CUBE}) {
            Entity entity = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
            entity.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
            helper.assertFalse(helper.getLevel().addFreshEntity(entity), "Fantasy entity was admitted: " + type);
        }
        Entity cow = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "A natural animal must remain allowed");
        helper.succeed();
    }
}
