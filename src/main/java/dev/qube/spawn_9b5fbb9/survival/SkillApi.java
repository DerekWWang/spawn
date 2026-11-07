package dev.qube.spawn_9b5fbb9.survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;

/** Server-side practice adapters; normal actions award only after a real completed outcome. */
public final class SkillApi {
    private SkillApi(){}
    public static int level(LivingEntity actor,Skill skill){return SurvivalApi.state(actor).skills.level(skill);}
    public static double workRate(LivingEntity actor,Skill skill){return .5+level(actor,skill)/200d;}
    public static double effective(LivingEntity actor,Skill skill){var body=SurvivalApi.state(actor);return body.skills.level(skill)*Math.min(body.manipulation(),body.mental());}
    public static void award(LivingEntity actor,Skill skill,double experience){
        if(!(actor.level() instanceof ServerLevel)||!SurvivalApi.isHumanoid(actor)||SurvivalApi.exempt(actor)||!actor.isAlive()||!Double.isFinite(experience)||experience<=0)return;
        var body=SurvivalApi.state(actor);body.skills.practice(skill,experience);SurvivalApi.save(actor,body);
    }
    public static String describe(SurvivalState body){
        var out=new StringBuilder();for(Skill skill:Skill.values()){
            if(!out.isEmpty())out.append(" | ");out.append(skill.label()).append(' ').append(body.skills.level(skill)).append("/100");
        }return out.toString();
    }
    public static com.google.gson.JsonObject inspect(SurvivalState body){
        var result=new com.google.gson.JsonObject();for(Skill skill:Skill.values()){
            var entry=new com.google.gson.JsonObject();entry.addProperty("level",body.skills.level(skill));entry.addProperty("experience",body.skills.experience(skill));entry.addProperty("experienceToNext",body.skills.untilNext(skill));entry.addProperty("workRate",.5+body.skills.level(skill)/200d);
            result.add(skill.name().toLowerCase(java.util.Locale.ROOT),entry);
        }return result;
    }
}
