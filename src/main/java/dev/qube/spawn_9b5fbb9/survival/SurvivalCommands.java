package dev.qube.spawn_9b5fbb9.survival;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import static net.minecraft.commands.Commands.*;

public final class SurvivalCommands {
    private static final SimpleCommandExceptionType ERROR=new SimpleCommandExceptionType(Component.literal("A living humanoid is required"));
    private SurvivalCommands(){}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(literal("survival")
            .then(literal("skills").executes(c->{var player=c.getSource().getPlayerOrException();SurvivalNetwork.sendSkills(player,SurvivalApi.state(player));return skills(c.getSource(),player);})
                .then(argument("target",EntityArgument.entity()).executes(c->{
                    var actor=living(EntityArgument.getEntity(c,"target"));
                    if(!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(c.getSource())&&c.getSource().getPlayerOrException().distanceToSqr(actor)>64){c.getSource().sendFailure(Component.literal("Inspect skills within eight blocks"));return 0;}
                    return skills(c.getSource(),actor);
                })))
            .then(literal("debug_skill").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("target",EntityArgument.entity()).then(argument("skill",StringArgumentType.word())
                    .suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(SkillSet.Skill.values()).map(s->s.name().toLowerCase(java.util.Locale.ROOT)),b))
                    .then(argument("level",IntegerArgumentType.integer(0,20)).executes(c->{
                        var actor=living(EntityArgument.getEntity(c,"target"));SkillSet.Skill skill;
                        try{skill=SkillSet.Skill.valueOf(StringArgumentType.getString(c,"skill").toUpperCase(java.util.Locale.ROOT));}
                        catch(IllegalArgumentException invalid){c.getSource().sendFailure(Component.literal("Unknown survival skill"));return 0;}
                        var body=SurvivalApi.state(actor);body.skills.setLevel(skill,IntegerArgumentType.getInteger(c,"level"));SurvivalApi.save(actor,body);
                        return skills(c.getSource(),actor);
                    }))))));
        dispatcher.register(literal("survival").then(literal("debug_passion").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(argument("target",EntityArgument.entity()).then(argument("skill",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(SkillSet.Skill.values()).map(s->s.name().toLowerCase(java.util.Locale.ROOT)),b))
                .then(argument("passion",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"none","interested","burning"},b)).executes(c->{
                    var actor=living(EntityArgument.getEntity(c,"target"));var body=SurvivalApi.state(actor);
                    try{body.skills.setPassion(SkillSet.Skill.valueOf(StringArgumentType.getString(c,"skill").toUpperCase(java.util.Locale.ROOT)),SkillSet.Passion.valueOf(StringArgumentType.getString(c,"passion").toUpperCase(java.util.Locale.ROOT)));}
                    catch(IllegalArgumentException invalid){c.getSource().sendFailure(Component.literal("Unknown skill or passion"));return 0;}
                    SurvivalApi.save(actor,body);return skills(c.getSource(),actor);
                }))))));
        dispatcher.register(literal("survival")
            .then(literal("vessel").then(argument("operation",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"fill","boil","seal","store","retrieve"},b))
                .executes(c->vessel(c.getSource(),c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"operation")))))
            .then(literal("recipes").executes(c->{c.getSource().sendSuccess(()->Component.literal(String.join(", ",dev.qube.spawn_9b5fbb9.content.Production.RECIPES.keySet())),false);return 1;}))
            .then(literal("produce").then(argument("recipe",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(dev.qube.spawn_9b5fbb9.content.Production.RECIPES.keySet(),b))
                .executes(c->work(c.getSource(),c.getSource().getPlayerOrException(),"produce",StringArgumentType.getString(c,"recipe")))))
            .then(literal("fuel").executes(c->work(c.getSource(),c.getSource().getPlayerOrException(),"fuel","")))
            .then(literal("collect").executes(c->work(c.getSource(),c.getSource().getPlayerOrException(),"collect","")))
            .then(literal("station").executes(c->work(c.getSource(),c.getSource().getPlayerOrException(),"station","")))
            .then(literal("dye").then(argument("slot",IntegerArgumentType.integer(0,3)).executes(c->{var result=SurvivalApi.dye(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"slot"));c.getSource().sendSuccess(()->Component.literal(result.message()),false);return result.success()?1:0;})))
            .then(literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("actor",EntityArgument.entity())
                .then(literal("vessel").then(argument("operation",StringArgumentType.word()).executes(c->vessel(c.getSource(),living(EntityArgument.getEntity(c,"actor")),StringArgumentType.getString(c,"operation")))))
                .then(literal("produce").then(argument("recipe",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(dev.qube.spawn_9b5fbb9.content.Production.RECIPES.keySet(),b))
                    .executes(c->work(c.getSource(),living(EntityArgument.getEntity(c,"actor")),"produce",StringArgumentType.getString(c,"recipe")))))
                .then(literal("fuel").executes(c->work(c.getSource(),living(EntityArgument.getEntity(c,"actor")),"fuel","")))
                .then(literal("collect").executes(c->work(c.getSource(),living(EntityArgument.getEntity(c,"actor")),"collect","")))
                .then(literal("station").executes(c->work(c.getSource(),living(EntityArgument.getEntity(c,"actor")),"station",""))))));
        dispatcher.register(literal("survival")
            .executes(c->status(c.getSource(),c.getSource().getPlayerOrException()))
            .then(literal("health").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();SurvivalNetwork.send(p,SurvivalApi.state(p),true);return 1;}))
            .then(literal("json").executes(c->json(c.getSource(),c.getSource().getPlayerOrException())))
            .then(literal("act").then(argument("action",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"eat","feed","drink","read","play","clothe","undress","undress_jacket","undress_shirt","undress_pants","undress_shoes","mend","treat","social"},b))
                .executes(c->act(c.getSource(),c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"action"),null))
                .then(argument("target",EntityArgument.entity()).executes(c->act(c.getSource(),c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"action"),target(EntityArgument.getEntity(c,"target")))))))
            .then(literal("inspect").then(argument("target",EntityArgument.entity()).executes(c->{
                LivingEntity target=living(EntityArgument.getEntity(c,"target"));
                if(!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(c.getSource())&&c.getSource().getPlayerOrException().distanceToSqr(target)>64){c.getSource().sendFailure(Component.literal("Inspect within eight blocks"));return 0;}
                return status(c.getSource(),target);
            })))
            .then(literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("actor",EntityArgument.entity()).then(argument("action",StringArgumentType.word())
                    .executes(c->act(c.getSource(),living(EntityArgument.getEntity(c,"actor")),StringArgumentType.getString(c,"action"),null))
                    .then(argument("target",EntityArgument.entity()).executes(c->act(c.getSource(),living(EntityArgument.getEntity(c,"actor")),StringArgumentType.getString(c,"action"),target(EntityArgument.getEntity(c,"target"))))))))
            .then(literal("enroll").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("targets",EntityArgument.entities()).executes(c->{int n=0;for(Entity entity:EntityArgument.getEntities(c,"targets"))if(entity instanceof LivingEntity living){SurvivalApi.enroll(living);n++;}final int count=n;c.getSource().sendSuccess(()->Component.literal("Enrolled "+count+" scripted humanoids"),true);return n;})))
            .then(literal("query").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("target",EntityArgument.entity()).executes(c->json(c.getSource(),living(EntityArgument.getEntity(c,"target"))))))
            .then(literal("debug").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("target",EntityArgument.entity())
                .then(argument("field",StringArgumentType.word()).then(argument("value",DoubleArgumentType.doubleArg(0,100)).executes(c->{
                    LivingEntity entity=living(EntityArgument.getEntity(c,"target"));SurvivalState s=SurvivalApi.state(entity);double v=DoubleArgumentType.getDouble(c,"value");
                    switch(StringArgumentType.getString(c,"field")){case "hunger"->s.hunger=v;case "thirst"->s.thirst=v;case "recreation"->s.recreation=v;case "temperature"->s.temperature=Math.max(25,Math.min(45,v));default->{c.getSource().sendFailure(Component.literal("Fields: hunger, thirst, recreation, temperature"));return 0;}}
                    SurvivalApi.save(entity,s);SurvivalApi.syncFood(entity,s);SurvivalEvents.applyCapacities(entity,s,false);return status(c.getSource(),entity);
                })))))
            .then(literal("injure").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("target",EntityArgument.entity())
                .then(argument("part",StringArgumentType.word()).then(argument("amount",DoubleArgumentType.doubleArg(0,100)).executes(c->{
                    LivingEntity actor=living(EntityArgument.getEntity(c,"target"));var s=SurvivalApi.state(actor);try{s.injure(SurvivalState.Part.valueOf(StringArgumentType.getString(c,"part").toUpperCase(java.util.Locale.ROOT)),DoubleArgumentType.getDouble(c,"amount"),true);}catch(IllegalArgumentException invalid){c.getSource().sendFailure(Component.literal("Use an anatomy part, e.g. LEFT_ARM or RIGHT_EYE"));return 0;}SurvivalApi.save(actor,s);return 1;
                })))))
            .then(literal("calendar").executes(c->{long ticks=SurvivalApi.calendar(c.getSource().getLevel());c.getSource().sendSuccess(()->Component.literal(calendarText(ticks)),false);return 1;}))
            // For protocol bots: the mod's block states, items and entity types with this server's ids.
            .then(literal("export_registry").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c->{
                try{var file=dev.qube.spawn_9b5fbb9.controllers.RegistryExport.write(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal("Registry written to "+file),false);return 1;}
                catch(java.io.IOException failure){c.getSource().sendFailure(Component.literal("Could not write the registry: "+failure.getMessage()));return 0;}
            })));
    }
    public static String calendarText(long ticks){return calendarText(ticks,dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get());}
    public static String calendarText(long ticks,int length){long day=ticks/24000;return new String[]{"Spring","Summer","Autumn","Winter"}[(int)(day/length%4)]+" day "+(day%length+1)+" | Year "+(day/(length*4)+1);}
    private static LivingEntity living(Entity entity)throws CommandSyntaxException{if(entity instanceof LivingEntity living&&SurvivalApi.isHumanoid(living))return living;throw ERROR.create();}
    private static LivingEntity target(Entity entity)throws CommandSyntaxException{if(entity instanceof LivingEntity living)return living;throw ERROR.create();}
    private static int work(CommandSourceStack source,LivingEntity actor,String operation,String recipe){var result=SurvivalApi.work(actor,operation,recipe);if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;}
    private static int vessel(CommandSourceStack source,LivingEntity actor,String operation){var result=SurvivalApi.vessel(actor,operation);if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;}
    private static int status(CommandSourceStack source,LivingEntity actor){source.sendSuccess(()->Component.literal(SurvivalApi.inspect(actor)),false);return 1;}
    private static int act(CommandSourceStack source,LivingEntity actor,String action,LivingEntity target){
        var result=SurvivalApi.act(actor,action,target);if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;
    }
    private static int json(CommandSourceStack source,LivingEntity actor){
        var s=SurvivalApi.state(actor);var json=new com.google.gson.JsonObject();json.addProperty("entity",actor.getUUID().toString());
        json.addProperty("recreation",s.recreation);json.addProperty("recreationHours",s.recreationTicks/1000d);json.addProperty("requiredHours",s.quotaTicks/1000d);
        json.addProperty("hunger",s.hunger);json.addProperty("thirst",s.thirst);json.addProperty("temperature",s.temperature);json.addProperty("health",actor.getHealth());
        json.addProperty("movement",s.movement());json.addProperty("manipulation",s.manipulation());json.addProperty("mental",s.mental());json.addProperty("conditions",s.conditions());
        json.addProperty("calendar",SurvivalApi.calendar(source.getLevel()));
        json.addProperty("schemaVersion",6);json.add("skills",SkillApi.inspect(s));json.add("service",dev.qube.spawn_9b5fbb9.warfare.Military.inspect(actor));
        var parts=new com.google.gson.JsonObject();for(var part:SurvivalState.Part.values())parts.addProperty(part.name(),s.anatomy[part.ordinal()]);json.add("anatomy",parts);
        var clothes=new com.google.gson.JsonArray();for(String garment:s.clothing)clothes.add(garment);json.add("clothing",clothes);
        source.sendSuccess(()->Component.literal(json.toString()),false);return 1;
    }
    private static int skills(CommandSourceStack source,LivingEntity actor){
        var body=SurvivalApi.state(actor);source.sendSuccess(()->Component.literal(actor.getName().getString()+" — practiced skills"),false);
        for(var skill:SkillSet.Skill.values())source.sendSuccess(()->Component.literal(String.format(java.util.Locale.ROOT,"%s %d/20 | %s | %.0f XP to next | learning %.0f%% | today %.0f/4000 | decay %.0f/day",skill.label(),body.skills.level(skill),body.skills.passion(skill).name().toLowerCase(java.util.Locale.ROOT),body.skills.untilNext(skill),body.skills.learningMultiplier(skill)*100,body.skills.dailyNetExperience(skill),body.skills.decayPerDay(skill))),false);
        return 1;
    }
}
