package dev.qube.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.client.SurvivalHud;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
@Mod(value=spawn_9b5fbb9.MODID,dist=Dist.CLIENT)
public final class spawn_9b5fbb9Client {
    public spawn_9b5fbb9Client(IEventBus bus){bus.addListener(SurvivalHud::register);bus.addListener(dev.qube.spawn_9b5fbb9.client.ClothingLayer::register);bus.addListener(dev.qube.spawn_9b5fbb9.client.NpcClothingLayer::register);bus.addListener(dev.qube.spawn_9b5fbb9.client.NpcClothingLayer::modifiers);bus.addListener(SurvivalHud::keys);bus.addListener(dev.qube.spawn_9b5fbb9.client.VehicleRenderer::register);NeoForge.EVENT_BUS.addListener(SurvivalHud::tick);
        // What is known of an actor goes when the actor does, and all of it when the server is left.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event)->{if(event.getLevel().isClientSide())dev.qube.spawn_9b5fbb9.network.SurvivalNetwork.entityStates.remove(event.getEntity().getId());});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event)->{dev.qube.spawn_9b5fbb9.network.SurvivalNetwork.forget();dev.qube.spawn_9b5fbb9.environment.WeatherNetwork.client=null;});
    }
}
