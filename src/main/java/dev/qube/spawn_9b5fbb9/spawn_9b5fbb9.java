package dev.qube.spawn_9b5fbb9;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.qube.spawn_9b5fbb9.network.ServerNetwork;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
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
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A functional tech tree from stone tools to servers, built from the
 * "Functional tech tree: stone to server" spreadsheet. Every item is made by a function; every
 * function must be discovered by a society that holds its inputs, its means of production and its
 * theory, and can explain its mechanism.
 */
@Mod(spawn_9b5fbb9.MODID)
public class spawn_9b5fbb9 {
    public static final String MODID = "spawn_9b5fbb9";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, MODID);

    public spawn_9b5fbb9(IEventBus modEventBus, ModContainer modContainer) {
        TechTree.get(); // fail fast on a broken tech tree
        ModContent.register(modEventBus);
        TechTreeGameTests.registerFunctions(TEST_FUNCTIONS);
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(ServerNetwork::register);
        modEventBus.addListener(this::registerGameTests);
        modContainer.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(MODID, "techtree"), new TestEnvironmentDefinition.AllOf(List.of()));
        // a blank 32x24x32 volume with open sky (kilns, furnaces and windmills check the sky); padded so far apart
        // that one test's engines and grid substation (96-block coverage) cannot power another's machines
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(env, Level.OVERWORLD,
                Identifier.fromNamespaceAndPath(MODID, "test_platform"), 1200, 0, true, Rotation.NONE, false, 1, 1, true, 128);
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> fn : TechTreeGameTests.FUNCTIONS) {
            event.registerTest(fn.getId(), new FunctionGameTestInstance(fn.key(), data));
        }
    }
}
