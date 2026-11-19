package dev.qube.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import dev.qube.spawn_9b5fbb9.survival.SurvivalEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
@Mod(spawn_9b5fbb9.MODID)
public final class spawn_9b5fbb9 {
    public static final String MODID="spawn_9b5fbb9";
    public spawn_9b5fbb9(IEventBus bus,ModContainer container){
        container.registerConfig(ModConfig.Type.LOCAL,Config.SPEC);
        dev.qube.spawn_9b5fbb9.environment.WorldEnvironment.init();
        dev.qube.spawn_9b5fbb9.survival.RealismData.init();
        SurvivalContent.register(bus); dev.qube.spawn_9b5fbb9.world.ChunkRecords.register(bus); dev.qube.spawn_9b5fbb9.world.SpawnCategories.register(bus); SurvivalContent.init();
        dev.qube.spawn_9b5fbb9.controllers.WorldActions.register(bus);
        dev.qube.spawn_9b5fbb9.controllers.WorldActions.init();
        dev.qube.spawn_9b5fbb9.wildlife.Predators.init();
        bus.addListener(SurvivalNetwork::register);
        bus.addListener(dev.qube.spawn_9b5fbb9.environment.WeatherNetwork::register);
        NeoForge.EVENT_BUS.addListener(dev.qube.spawn_9b5fbb9.environment.WeatherNetwork::tick);
        NeoForge.EVENT_BUS.addListener(dev.qube.spawn_9b5fbb9.controllers.EnvironmentActions::commands);
        SurvivalGameTests.register(bus);
        NeoForge.EVENT_BUS.register(SurvivalEvents.class);
        NeoForge.EVENT_BUS.register(dev.qube.spawn_9b5fbb9.survival.SkillEvents.class);
    }
}
