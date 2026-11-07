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
        dev.qube.spawn_9b5fbb9.survival.RealismData.init();
        SurvivalContent.register(bus); SurvivalContent.init();
        dev.qube.spawn_9b5fbb9.controllers.WorldActions.register(bus);
        dev.qube.spawn_9b5fbb9.controllers.WorldActions.init();
        bus.addListener(SurvivalNetwork::register);
        SurvivalGameTests.register(bus);
        NeoForge.EVENT_BUS.register(SurvivalEvents.class);
    }
}
