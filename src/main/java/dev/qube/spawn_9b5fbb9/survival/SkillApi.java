package dev.qube.spawn_9b5fbb9.survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;

/** Server-side practice adapters; normal actions award only after a real completed outcome. */
public final class SkillApi {
    private SkillApi(){}
    public static int level(LivingEntity actor,Skill skill){return SurvivalApi.state(actor).skills.level(skill);}
    public static double workRate(LivingEntity actor,Skill skill){return .5+level(actor,skill)/40d;}
    public static double manualRate(SurvivalState body,Skill skill){return Math.min(body.manipulation(),body.mental())*(skill==null?1:.5+body.skills.level(skill)/40d);}
    public static double effective(LivingEntity actor,Skill skill){var body=SurvivalApi.state(actor);return body.skills.level(skill)*Math.min(body.manipulation(),body.mental());}
    public static void award(LivingEntity actor,Skill skill,double experience){
        if(!(actor.level() instanceof ServerLevel)||!SurvivalApi.isHumanoid(actor)||SurvivalApi.exempt(actor)||!actor.isAlive()||!Double.isFinite(experience)||experience<=0)return;
        var body=SurvivalApi.state(actor);body.skills.practice(skill,experience);SurvivalApi.save(actor,body);
    }
    public static void awardWork(LivingEntity actor,Skill skill,long activeTicks,double weight){
        if(activeTicks<=0||!Double.isFinite(weight)||weight<=0)return;
        award(actor,skill,.25*Math.min(1000000,activeTicks)*Math.min(100,weight));
    }
    public static Skill craftingSkill(net.minecraft.world.item.ItemStack result){
        if(result.has(net.minecraft.core.component.DataComponents.FOOD))return Skill.COOKING;
        String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
        if(java.util.Set.of("cordage","plant_fiber","dried_fiber").contains(id))return Skill.WEAVING;
        if(java.util.Set.of("wooden_toy","crossbow_stock","kiln","forge","bloomery","anvil","loom","trough","workshop","assembly_yard","niter_bed").contains(id))return Skill.CONSTRUCTION;
        for(String wood:new String[]{"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak","poplar","bamboo","crimson","warped"})if(id.startsWith(wood+"_")&&java.util.Set.of("planks","stairs","slab","fence","fence_gate","door","trapdoor","sign","hanging_sign","boat","chest_boat").contains(id.substring(wood.length()+1)))return Skill.CONSTRUCTION;
        return null;
    }
    public static String describe(SurvivalState body){
        var out=new StringBuilder();for(Skill skill:Skill.values()){
            if(!out.isEmpty())out.append(" | ");out.append(skill.label()).append(' ').append(body.skills.level(skill)).append("/20");
        }return out.toString();
    }
    public static com.google.gson.JsonObject inspect(SurvivalState body){
        var result=new com.google.gson.JsonObject();for(Skill skill:Skill.values()){
            var entry=new com.google.gson.JsonObject();entry.addProperty("level",body.skills.level(skill));entry.addProperty("maxLevel",20);entry.addProperty("experience",body.skills.experience(skill));entry.addProperty("experienceToNext",body.skills.untilNext(skill));entry.addProperty("workRate",.5+body.skills.level(skill)/40d);
            entry.addProperty("passion",body.skills.passion(skill).name().toLowerCase(java.util.Locale.ROOT));entry.addProperty("learningMultiplier",body.skills.learningMultiplier(skill));entry.addProperty("dailyNetExperience",body.skills.dailyNetExperience(skill));entry.addProperty("dailySoftCap",SkillSet.DAILY_SOFT_CAP);entry.addProperty("decayPerDay",body.skills.decayPerDay(skill));entry.addProperty("effectiveLevel",body.skills.level(skill)*Math.min(body.manipulation(),body.mental()));entry.addProperty("levelExperience",body.skills.levelExperience(skill));entry.addProperty("levelCost",body.skills.levelCost(skill));
            result.add(skill.name().toLowerCase(java.util.Locale.ROOT),entry);
        }return result;
    }
}
