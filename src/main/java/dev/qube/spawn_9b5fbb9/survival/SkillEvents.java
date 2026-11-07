package dev.qube.spawn_9b5fbb9.survival;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Practice at actual outcomes, shared by native player combat and scripted hunting. */
public final class SkillEvents {
    private static final TagKey<EntityType<?>> REAL_ANIMALS=TagKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath("spawn_9b5fbb9","real_animals"));
    private SkillEvents(){}
    @SubscribeEvent public static void quarryKilled(LivingDropsEvent event){
        var quarry=event.getEntity();
        if(!(quarry.level() instanceof ServerLevel)||SurvivalApi.isHumanoid(quarry)||dev.qube.spawn_9b5fbb9.world.NaturalWorldRules.prohibited(quarry))return;
        if(!(quarry instanceof Animal)&&!quarry.getType().builtInRegistryHolder().is(REAL_ANIMALS))return;
        if(event.getSource().getEntity() instanceof LivingEntity hunter&&SurvivalApi.isHumanoid(hunter))SkillApi.award(hunter,SkillSet.Skill.HUNTING,25);
    }
}
