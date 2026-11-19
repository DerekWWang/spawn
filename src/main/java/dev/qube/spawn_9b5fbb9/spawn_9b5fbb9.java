package dev.qube.spawn_9b5fbb9;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.network.ServerNetwork;
import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.survival.SurvivalEvents;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.test.TechTreeGameTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A brutal-realism survival overhaul with a functional tech tree on top. The survival rules own the
 * body, the land, the workstations and the items they share with the tech tree; the tech tree adds
 * discovery, the rest of its items and stations, and the physics, engineering and logistics engines.
 */
@Mod(spawn_9b5fbb9.MODID)
public final class spawn_9b5fbb9 {
    public static final String MODID = "spawn_9b5fbb9";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, MODID);

    public spawn_9b5fbb9(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
        // survival
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
        // tech tree
        TechTree.get(); // fail fast on a broken tech tree
        ModContent.register(bus);
        TechTreeGameTests.registerFunctions(TEST_FUNCTIONS);
        dev.qube.spawn_9b5fbb9.test.EngineGameTests.registerFunctions(TEST_FUNCTIONS);
        TEST_FUNCTIONS.register(bus);
        bus.addListener(ServerNetwork::register);
        bus.addListener(this::registerTechTreeTests);
    }

    private void registerTechTreeTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(MODID, "techtree"), new TestEnvironmentDefinition.AllOf(List.of()));
        // A blank 32x24x32 volume with open sky (furnaces and windmills check the sky), padded 24 blocks: more than the
        // 16-block reach of stations and engines, so one test's machines never serve another's. The grid test places
        // and removes its substation within one tick. The runner clears the padding block by block, so a wider margin
        // (128 once) costs minutes on every run.
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(env, Level.OVERWORLD,
                Identifier.fromNamespaceAndPath(MODID, "test_platform"), 1200, 0, true, Rotation.NONE, false, 1, 1, true, 24);
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> fn : TechTreeGameTests.FUNCTIONS) {
            event.registerTest(fn.getId(), new FunctionGameTestInstance(fn.key(), data));
        }
    }
}
