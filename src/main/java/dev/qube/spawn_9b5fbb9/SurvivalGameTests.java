package dev.qube.spawn_9b5fbb9;

import java.util.*;
import java.util.function.Consumer;
import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SurvivalGameTests {
    private static final String NS=spawn_9b5fbb9.MODID;
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,NS);
    private static final Map<String,java.util.function.Supplier<Consumer<GameTestHelper>>> TESTS=new LinkedHashMap<>();
    public static void register(IEventBus bus){
        add("starvation_speed",SurvivalGameTests::starvation);
        add("controller_parity",SurvivalGameTests::controllerParity);
        add("supplied_treatment",SurvivalGameTests::treatment);
        add("thirst_death",SurvivalGameTests::thirstDeath);
        add("state_persistence",SurvivalGameTests::persistence);
        add("timed_consumption",SurvivalGameTests::timedConsumption);
        add("offhand_supplies",SurvivalGameTests::offhand);
        add("clothing_wear_handoff",SurvivalGameTests::clothing);
        add("closed_vessel_no_free_water",SurvivalGameTests::closedVessel);
        add("direct_food_credit",SurvivalGameTests::directFood);
        add("guard_crafting_incapacity",SurvivalGameTests::craftingIncapacity);
        add("guard_companion_clothing",SurvivalGameTests::companionClothing);
        add("mental_work_budget",SurvivalGameTests::mentalBudget);
        add("npc_supplied_self_care",SurvivalGameTests::selfCare);
        add("npc_supplied_clothing",SurvivalGameTests::selfClothe);
        FUNCTIONS.register(bus);bus.addListener(SurvivalGameTests::instances);
    }
    private static void add(String name,Consumer<GameTestHelper> test){TESTS.put(name,FUNCTIONS.register(name,()->test));}
    private static void instances(RegisterGameTestsEvent event){
        Holder<TestEnvironmentDefinition<?>> env=event.registerEnvironment(Identifier.fromNamespaceAndPath(NS,"survival"),new TestEnvironmentDefinition.AllOf(List.of()));
        var data=new TestData<Holder<TestEnvironmentDefinition<?>>>(env,Identifier.withDefaultNamespace("empty"),120,0,true,Rotation.NONE);
        TESTS.forEach((name,function)->event.registerTest(Identifier.fromNamespaceAndPath(NS,name),new FunctionGameTestInstance(net.minecraft.resources.ResourceKey.create(Registries.TEST_FUNCTION,Identifier.fromNamespaceAndPath(NS,name)),data)));
    }
    private static LivingEntity humanoid(GameTestHelper h){h.setBlock(1,1,1,Blocks.STONE);var entity=h.spawn(EntityTypes.VILLAGER,1,2,1);entity.setNoAi(true);return entity;}
    private static void starvation(GameTestHelper h){
        var entity=humanoid(h);var s=SurvivalApi.state(entity);s.hunger=0;SurvivalApi.save(entity,s);
        h.runAfterDelay(25,()->{double base=entity.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();double actual=entity.getAttributeValue(Attributes.MOVEMENT_SPEED);h.assertTrue(Math.abs(actual-base*.1)<.0001,"starvation must reduce actual movement speed to 10%");h.succeed();});
    }
    private static void controllerParity(GameTestHelper h){
        var bot=h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);var npc=humanoid(h);bot.setPos(npc.position());
        h.assertTrue(SurvivalApi.isHumanoid(bot),"server-controlled player must be a humanoid");
        var a=SurvivalApi.state(bot);var b=SurvivalApi.state(npc);a.thirst=b.thirst=30;
        h.assertTrue(a.mental()==.3&&b.mental()==.3,"players and NPCs must share mental impairment");
        SurvivalApi.save(bot,a);SurvivalApi.save(npc,b);bot.tickCount=npc.tickCount=20;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(bot));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(npc));
        h.assertTrue(Math.abs(SurvivalApi.state(bot).hunger-SurvivalApi.state(npc).hunger)<.0001&&Math.abs(SurvivalApi.state(bot).thirst-SurvivalApi.state(npc).thirst)<.0001,"actual player and NPC expenditure must match under identical activity");
        var scripted=h.spawn(EntityTypes.COW,3,2,1);SurvivalApi.enroll(scripted);
        h.assertTrue(SurvivalApi.isHumanoid(scripted),"script-controlled entity enrollment must use the same rules");h.succeed();
    }
    private static void treatment(GameTestHelper h){
        var actor=humanoid(h);var state=SurvivalApi.state(actor);state.injure(SurvivalState.Part.LEFT_ARM,60,true);SurvivalApi.save(actor,state);
        var noSupply=SurvivalApi.act(actor,"treat",actor);h.assertTrue(!noSupply.success()&&SurvivalApi.state(actor).bleeding>0,"no supplies must not cure bleeding");
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(NS,"bandage"));actor.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item,2));
        h.assertTrue(SurvivalApi.act(actor,"treat",actor).success(),"supplied bandage must work");
        h.assertTrue(SurvivalApi.state(actor).bleeding==0&&actor.getMainHandItem().getCount()==1,"bandage consumes exactly one and stops bleeding");h.succeed();
    }
    private static void thirstDeath(GameTestHelper h){var entity=humanoid(h);var s=SurvivalApi.state(entity);s.thirst=0;SurvivalApi.save(entity,s);h.runAfterDelay(25,()->{h.assertTrue(!entity.isAlive(),"zero thirst must kill humanoids");h.succeed();});}
    private static void persistence(GameTestHelper h){var entity=humanoid(h);var s=SurvivalApi.state(entity);s.thirst=23.25;s.injure(SurvivalState.Part.LEFT_EYE,100,false);s.injure(SurvivalState.Part.RIGHT_EYE,100,false);SurvivalApi.save(entity,s);var read=SurvivalApi.state(entity);h.assertTrue(read.thirst==23.25&&read.blind(),"persisted fractions and permanent sensory injuries must survive reload");h.succeed();}
    private static void timedConsumption(GameTestHelper h){
        var entity=humanoid(h);var s=SurvivalApi.state(entity);s.hunger=20;SurvivalApi.save(entity,s);entity.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BREAD,2));
        h.assertTrue(SurvivalApi.act(entity,"eat",null).success(),"scripted entity must be able to start eating");
        h.assertTrue(SurvivalApi.state(entity).hunger==20&&entity.getMainHandItem().getCount()==2,"nutrition and supply consumption must wait for food duration");
        h.runAfterDelay(50,()->{h.assertTrue(SurvivalApi.state(entity).hunger>44&&entity.getMainHandItem().getCount()==1,"completed NPC consumption must grant nutrition once and consume one item");h.succeed();});
    }
    private static void offhand(GameTestHelper h){
        var player=h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);var s=SurvivalApi.state(player);s.injure(SurvivalState.Part.LEFT_ARM,60,true);SurvivalApi.save(player,s);
        player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("painkiller")));
        player.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("bandage")));
        var result=SurvivalApi.act(player,"treat",player,net.minecraft.world.InteractionHand.OFF_HAND);
        h.assertTrue(result.success()&&SurvivalApi.state(player).bleeding==0,"offhand bandage must treat the selected condition");
        h.assertTrue(player.getOffhandItem().isEmpty()&&player.getMainHandItem().getCount()==1&&SurvivalApi.state(player).painkillerTicks==0,"offhand action must not consume or apply the main hand");h.succeed();
    }
    private static void clothing(GameTestHelper h){
        var actor=humanoid(h);ItemStack jacket=new ItemStack(dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("wool_jacket"));
        var data=dev.qube.spawn_9b5fbb9.content.ItemAging.data(jacket);data.putLong("garmentWear",27*24000L);dev.qube.spawn_9b5fbb9.content.ItemAging.data(jacket,data);
        jacket.set(net.minecraft.core.component.DataComponents.DYED_COLOR,new net.minecraft.world.item.component.DyedItemColor(0x246680));actor.setItemSlot(EquipmentSlot.MAINHAND,jacket);
        h.assertTrue(SurvivalApi.act(actor,"clothe",null).success(),"worn jacket must equip");var worn=SurvivalApi.state(actor);ItemStack returned=SurvivalApi.garment(worn,2);
        h.assertTrue(dev.qube.spawn_9b5fbb9.content.ItemAging.data(returned).getLongOr("garmentWear",0)==27*24000L&&returned.get(net.minecraft.core.component.DataComponents.DYED_COLOR).rgb()==0x246680,"garment handoff must preserve wear and dye");
        actor.setItemSlot(EquipmentSlot.MAINHAND,returned);SurvivalApi.act(actor,"clothe",null);h.assertTrue(SurvivalApi.state(actor).clothingWear[2]==27*24000L,"re-equipping must not refresh garment life");h.succeed();
    }
    private static void closedVessel(GameTestHelper h){
        var actor=humanoid(h);var state=SurvivalApi.state(actor);state.thirst=50;SurvivalApi.save(actor,state);ItemStack pot=new ItemStack(dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("fired_pot"));
        var data=dev.qube.spawn_9b5fbb9.content.ItemAging.data(pot);data.putInt("water",4);data.putBoolean("sealed",true);data.putBoolean("waterSafe",true);dev.qube.spawn_9b5fbb9.content.ItemAging.data(pot,data);actor.setItemSlot(EquipmentSlot.MAINHAND,pot);
        h.assertTrue(!SurvivalApi.act(actor,"drink",null).success()&&SurvivalApi.state(actor).thirst==50&&dev.qube.spawn_9b5fbb9.content.Pottery.waterUnits(pot)==4,"sealed vessels must not grant free hydration");h.succeed();
    }
    private static void directFood(GameTestHelper h){
        var actor=h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);var pos=h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));actor.setPos(pos.getX(),pos.getY(),pos.getZ());
        var s=SurvivalApi.state(actor);s.hunger=20;SurvivalApi.save(actor,s);SurvivalApi.syncFood(actor,s);actor.getFoodData().eat(2,.1f);actor.tickCount=20;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(actor));
        h.assertTrue(SurvivalApi.state(actor).hunger>29,"direct food paths such as cake must retain their nutrition");h.succeed();
    }
    private static void craftingIncapacity(GameTestHelper h){
        var player=h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);var body=SurvivalApi.state(player);body.injure(SurvivalState.Part.BRAIN,100,false);SurvivalApi.save(player,body);
        var result=new net.minecraft.world.inventory.ResultContainer();result.setItem(0,new ItemStack(Items.STICK));
        var slot=new net.minecraft.world.inventory.ResultSlot(player,player.inventoryMenu.getCraftSlots(),result,0,0,0);
        h.assertTrue(!slot.mayPickup(player),"mental incapacity must block actual crafting-result pickup");
        h.assertTrue(result.getItem(0).getCount()==1,"refused craft must not delete output or ingredients");h.succeed();
    }
    private static void companionClothing(GameTestHelper h){
        var helper=humanoid(h);var wearer=h.spawn(EntityTypes.VILLAGER,2,2,1);var body=SurvivalApi.state(wearer);body.clothing[2]="spawn_9b5fbb9:wool_jacket";body.clothingWear[2]=12000;SurvivalApi.save(wearer,body);
        helper.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("fiber_jacket")));
        h.assertTrue(!SurvivalApi.act(helper,"clothe",wearer).success(),"care action cannot remove a companion's existing garment");
        h.assertTrue(SurvivalApi.state(wearer).clothing[2].endsWith("wool_jacket")&&helper.getMainHandItem().getCount()==1,"refusal must preserve both garments");h.succeed();
    }
    private static void mentalBudget(GameTestHelper h){
        var actor=humanoid(h);var body=SurvivalApi.state(actor);body.thirst=30;SurvivalApi.save(actor,body);int allowed=0;
        for(int i=0;i<10;i++)if(SurvivalEvents.brainTick(actor))allowed++;
        h.assertTrue(allowed==3,"30 percent mental capacity must permit exactly three brain-work updates per ten ticks, got "+allowed);h.succeed();
    }
    private static void selfCare(GameTestHelper h){
        var actor=humanoid(h);var body=SurvivalApi.state(actor);body.injure(SurvivalState.Part.LEFT_ARM,60,true);SurvivalApi.save(actor,body);
        actor.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_AXE));actor.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("bandage")));
        actor.tickCount=100;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(actor));
        h.assertTrue(SurvivalApi.state(actor).bleeding==0&&actor.getOffhandItem().isEmpty(),"NPC must consume available bandage to stop its bleeding");
        h.assertTrue(actor.getMainHandItem().is(Items.STONE_AXE),"NPC self-care must preserve unrelated held tools");h.succeed();
    }
    private static void selfClothe(GameTestHelper h){
        var actor=humanoid(h);actor.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("wool_jacket")));
        actor.tickCount=100;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(actor));
        h.assertTrue(SurvivalApi.state(actor).clothing[2].endsWith("wool_jacket")&&actor.getOffhandItem().isEmpty(),"NPC must wear supplied clothing through the same consumptive action as players");h.succeed();
    }
}
