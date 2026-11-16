package dev.qube.spawn_9b5fbb9.controllers;

import com.google.gson.*;
import dev.qube.spawn_9b5fbb9.environment.*;
import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.world.ResourceFields;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import static net.minecraft.commands.Commands.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;

public final class EnvironmentActions {
    public static JsonObject observe(LivingEntity actor){
        var out=new JsonObject();if(!(actor.level() instanceof ServerLevel level)||!SurvivalApi.isHumanoid(actor)||!actor.isAlive()){out.addProperty("error","A living server humanoid is required");return out;}
        var pos=actor.blockPosition();var climate=SurvivalEvents.climate(actor,level);var e=EnvironmentApi.exposure(level,pos);out.addProperty("profile",WorldEnvironment.settings(level).profile());out.addProperty("regional",WorldEnvironment.active(level));out.addProperty("temperatureC",climate.ambient());out.addProperty("roof",climate.sheltered());out.addProperty("nearFire",climate.fire());
        if(WorldEnvironment.active(level)){var w=EnvironmentApi.at(level,pos);out.addProperty("rain",w.rain());out.addProperty("windMetersPerSecond",w.wind());out.addProperty("snow",w.snow());out.addProperty("windShelter",e.enclosure());}else out.addProperty("rainingAtActor",level.isRainingAt(pos));return out;
    }
    public static void commands(RegisterCommandsEvent event){
        var root=literal("survival");root.then(literal("climate").executes(c->report(c.getSource(),observe(c.getSource().getPlayerOrException()))));
        root.then(literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("actor",EntityArgument.entity()).then(literal("climate").executes(c->{var entity=EntityArgument.getEntity(c,"actor");return entity instanceof LivingEntity living?report(c.getSource(),observe(living)):0;}))));
        root.then(literal("environmentSurvey").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("x",IntegerArgumentType.integer(-29000000,29000000)).then(argument("z",IntegerArgumentType.integer(-29000000,29000000)).executes(c->report(c.getSource(),survey(c.getSource().getLevel(),IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z")))))));
        event.getDispatcher().register(root);
    }
    /** Explicit GM diagnostic, separate from agent observations. Never generates or loads chunks. */
    public static JsonObject survey(ServerLevel level,int x,int z){
        long start=System.nanoTime();var t=TerrainSampling.sample(level,x,z);var settings=WorldEnvironment.settings(level);var original=ResourceFields.district(level.getSeed(),Math.floorDiv(x,256),Math.floorDiv(z,256));var anchor=TerrainSampling.sample(level,original.x(),original.z());int sea=level.getChunkSource().getGenerator().getSeaLevel();
        var d=ResourceFields.district(level.getSeed(),Math.floorDiv(x,256),Math.floorDiv(z,256),(int)Math.round(anchor.elevation()+sea),anchor.relief(),level.getMinY(),level.getMaxY());
        var out=new JsonObject();out.addProperty("profile",settings.profile());out.addProperty("x",x);out.addProperty("z",z);out.addProperty("height",t.elevation()+sea);out.addProperty("relief",t.relief());out.addProperty("baselineC",EnvironmentModel.baseline(t));out.addProperty("humidity",t.humidity());out.addProperty("rainShadow",t.rainShadow());out.addProperty("mineral",d.mineral().name());out.addProperty("depositX",d.x());out.addProperty("depositY",d.y());out.addProperty("depositZ",d.z());out.addProperty("anchorHeight",anchor.elevation()+sea);out.addProperty("cachePoints",TerrainSampling.cachedPoints(level));
        var w=EnvironmentApi.at(level,new BlockPos(x,(int)Math.round(t.elevation()+sea),z));out.addProperty("rain",w.rain());out.addProperty("temperatureC",w.temperature());out.addProperty("elapsedMicros",(System.nanoTime()-start)/1000);return out;
    }
    private static int report(CommandSourceStack source,JsonObject out){source.sendSuccess(()->Component.literal(out.toString()),false);return out.has("error")?0:1;}
}
