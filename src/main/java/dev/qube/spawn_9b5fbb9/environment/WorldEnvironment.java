package dev.qube.spawn_9b5fbb9.environment;

import com.google.gson.*;
import dev.qube.spawn_9b5fbb9.Config;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static dev.qube.spawn_9b5fbb9.environment.EnvironmentModel.*;

/** Per-world profile is loaded before generation; no command-storage access in features. */
public final class WorldEnvironment {
    private static final Map<MinecraftServer,Settings> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    public static final Settings LEGACY=new Settings("legacy",0,28,1);
    private WorldEnvironment(){}
    public static void init(){NeoForge.EVENT_BUS.addListener(WorldEnvironment::starting);NeoForge.EVENT_BUS.addListener(WorldEnvironment::stopped);}
    public static Settings settings(ServerLevel level){return WORLDS.getOrDefault(level.getServer(),LEGACY);}
    public static boolean active(ServerLevel level){return level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)&&settings(level).active();}
    public static boolean runtimeCompatible(String tectonic,String lithostitched){return "3.0.32".equals(tectonic)&&"2.0.6".equals(lithostitched);}
    private static String version(String id){return ModList.get().getModContainerById(id).map(c->c.getModInfo().getVersion().toString()).orElse("absent");}
    public static Settings replaceForTest(ServerLevel level,Settings value){var before=settings(level);WORLDS.put(level.getServer(),value);return before;}
    public static String selectProfile(boolean tectonic,boolean existing,boolean adopt,boolean testServer){
        if(testServer)return "legacy";
        if(tectonic&&existing&&!adopt)throw new IllegalStateException("Tectonic profile refuses an existing unprofiled world. Create a new world, or explicitly set -Dspawn.adoptEnvironment=true after backing up the world.");
        return tectonic?"tectonic":"legacy";
    }
    private static void starting(ServerAboutToStartEvent event){
        var server=event.getServer();Path file=server.getWorldPath(LevelResource.ROOT).resolve("data/spawn_9b5fbb9-environment.json");
        boolean tectonic=ModList.get().isLoaded("tectonic"),testServer=server instanceof net.minecraft.gametest.framework.GameTestServer;
        if(tectonic&&!runtimeCompatible(version("tectonic"),version("lithostitched")))throw new IllegalStateException("Environment profile requires Tectonic 3.0.32 and Lithostitched 2.0.6; found "+version("tectonic")+" / "+version("lithostitched"));
        try{
            String fingerprint=tectonic?tectonicFingerprint():"legacy";
            Settings settings;
            if(Files.exists(file)){
                var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();if(json.get("schema").getAsInt()!=1)throw new IllegalStateException("Unsupported environment schema; world was not modified");
                settings=new Settings(json.get("profile").getAsString(),json.get("epoch").getAsLong(),json.get("seasonDays").getAsInt(),json.get("resources").getAsInt());
                if(settings.profile().equals("tectonic")&&!tectonic)throw new IllegalStateException("This world requires its Tectonic profile. Install the pinned Tectonic runtime before opening it.");
                if(!settings.profile().equals("tectonic")&&tectonic&&!testServer)throw new IllegalStateException("Tectonic is installed for a legacy world. Use a new world/profile; do not silently substitute its generator.");
                if(settings.profile().equals("tectonic")&&(!json.has("generatorFingerprint")||!json.get("generatorFingerprint").getAsString().equals(fingerprint)))throw new IllegalStateException("Tectonic configuration differs from this world's saved generator fingerprint. Restore its original config or create a new world.");
                if(settings.active()&&settings.seasonDays()!=Config.SEASON_DAYS.get())throw new IllegalStateException("Restore this world's saved seasonDays="+settings.seasonDays()+" before loading its environment profile.");
            }else{
                boolean existing=server.getWorldData().overworldData().isInitialized();
                String profile=selectProfile(tectonic,existing,Boolean.getBoolean("spawn.adoptEnvironment"),testServer);
                settings=new Settings(profile,0,Config.SEASON_DAYS.get(),profile.equals("legacy")?1:2);
                var json=new JsonObject();json.addProperty("schema",1);json.addProperty("profile",profile);json.addProperty("epoch",0);json.addProperty("seasonDays",settings.seasonDays());json.addProperty("resources",settings.resources());json.addProperty("tectonicVersion",tectonic?"3.0.32":"absent");
                json.addProperty("generatorFingerprint",fingerprint);
                Files.createDirectories(file.getParent());Path temporary=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(json));Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
            }
            WORLDS.put(server,settings);
        }catch(IOException|JsonParseException|IllegalArgumentException failure){throw new IllegalStateException("Cannot load the world's environment profile without changing it",failure);}
    }
    private static String tectonicFingerprint() throws IOException {
        Path config=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        var json=JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        if(!json.getAsJsonObject("general").get("mod_enabled").getAsBoolean())throw new IllegalStateException("Tectonic is installed but disabled; enable it before creating the profile.");
        if(json.getAsJsonObject("continents").get("river_lanterns").getAsBoolean())throw new IllegalStateException("The realism profile requires tectonic.json continents.river_lanterns=false before world creation. Copy config/tectonic-realism.json to the game's config/tectonic.json.");
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void stopped(ServerStoppedEvent event){WORLDS.remove(event.getServer());TerrainSampling.clear(event.getServer());}
}
