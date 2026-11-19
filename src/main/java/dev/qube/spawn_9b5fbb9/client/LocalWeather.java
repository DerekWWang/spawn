package dev.qube.spawn_9b5fbb9.client;

import dev.qube.spawn_9b5fbb9.environment.WeatherNetwork;
import net.minecraft.client.Minecraft;

public final class LocalWeather {
    private LocalWeather(){}
    public static WeatherNetwork.Local current(){var mc=Minecraft.getInstance();var p=WeatherNetwork.client;if(mc.level==null||mc.player==null||p==null)return null;return p.applicable(mc.level.dimension().identifier().toString(),mc.player.blockPosition().getX(),mc.player.blockPosition().getY(),mc.player.blockPosition().getZ(),(System.nanoTime()-WeatherNetwork.receivedNanos)/1_000_000)?p:null;}
    public static float rain(){var p=current();if(p==null)return 0;float t=Math.clamp((System.nanoTime()-WeatherNetwork.receivedNanos)/1_000_000_000f,0,1);return WeatherNetwork.previousRain+(p.rain()-WeatherNetwork.previousRain)*t;}
}
