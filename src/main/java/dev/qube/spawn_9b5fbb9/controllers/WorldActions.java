package dev.qube.spawn_9b5fbb9.controllers;

import java.util.*;
import java.util.function.*;
import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops;
import dev.qube.spawn_9b5fbb9.agriculture.Husbandry;
import dev.qube.spawn_9b5fbb9.content.*;
import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import dev.qube.spawn_9b5fbb9.world.NaturalWorldRules;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import static net.minecraft.commands.Commands.*;

/** Server-owned, controller-neutral manual work. A job accrues one unit per actual entity tick,
 * never from calendar elapsed time. Inputs leave real inventories and persist inside the actor. */
public final class WorldActions {
    private static final String KEY="survivalWorldJob";
    public static final int PLANNING_BYTE_LIMIT=24*1024;
    private static final double RANGE_SQUARED=16;
    private static final double HUNT_RADIUS=16;
    private static final net.minecraft.tags.TagKey<EntityType<?>> REAL_ANIMALS=net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"real_animals"));
    private static final Map<LivingEntity,Integer> TICKED=new WeakHashMap<>();
    private static final Set<LivingEntity> COMPLETING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS=DeferredRegister.create(Registries.TEST_FUNCTION,SurvivalContent.MODID);
    static {
        TESTS.register("skill_native_left_gather",()->NativeForageGameTests::leftGather);
        TESTS.register("skill_native_fruit_paths",()->NativeForageGameTests::fruitHarvestAndDismantle);
        TESTS.register("skill_native_berry_timing",()->SkillActionGameTests::nativeBerryTiming);
        TESTS.register("skill_station_cancel_surface",()->SkillActionGameTests::stationCancelSurface);
        TESTS.register("skill_native_sowing",()->SkillActionGameTests::nativeSowing);
        TESTS.register("skill_hunt_pursuit",()->SkillActionGameTests::huntPursuit);
        TESTS.register("skill_forage_practice",()->SkillActionGameTests::foragePractice);
        TESTS.register("skill_farming_parity",()->SkillActionGameTests::farmingParity);
        TESTS.register("skill_hunt_validation",()->SkillActionGameTests::huntValidation);
        TESTS.register("skill_hunt_outcome",()->SkillActionGameTests::huntOutcome);
        TESTS.register("controller_forge_blocks_work",()->ControllerGameTests::forgeBlocksWorldWork);
        TESTS.register("controller_native_planting",()->ControllerGameTests::nativePlantingSubstrates);
        TESTS.register("controller_mining_cost_once",()->ControllerGameTests::miningCostOnce);
        TESTS.register("controller_adventure_permissions",()->ControllerGameTests::adventurePermissions);
        TESTS.register("controller_planning_bounded",()->ControllerGameTests::planningBounded);
        TESTS.register("controller_farm_adapters",()->ControllerGameTests::farmAdapters);
        TESTS.register("controller_no_offline_labor",()->ControllerGameTests::noOfflineLabor);
        TESTS.register("controller_sow_physical_seed",()->ControllerGameTests::sowPhysicalSeed);
        TESTS.register("controller_frost_harvest",()->ControllerGameTests::frostHarvest);
        TESTS.register("controller_bootstrap_hoe",()->ControllerGameTests::bootstrapHoe);
        TESTS.register("controller_gather_timed",()->ControllerGameTests::gatherTimed);
        TESTS.register("controller_mine_validated",()->ControllerGameTests::mineValidated);
        TESTS.register("controller_mine_drops_wear",()->ControllerGameTests::mineDropsAndWear);
        TESTS.register("controller_craft_shared_supplies",()->ControllerGameTests::craftSharedSupplies);
        TESTS.register("controller_craft_reserved",()->ControllerGameTests::craftReserved);
        TESTS.register("controller_craft_finishes",()->ControllerGameTests::craftFinishes);
        TESTS.register("controller_sow_seed",()->ControllerGameTests::sowRequiresSeed);
    }
    private WorldActions(){}
    public static void register(IEventBus bus){TESTS.register(bus);bus.addListener(WorldActions::tests);}
    private static void tests(RegisterGameTestsEvent event){
        var env=event.registerEnvironment(Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"controller_actions"),new TestEnvironmentDefinition.AllOf(List.of()));
        var data=new TestData<Holder<TestEnvironmentDefinition<?>>>(env,Identifier.withDefaultNamespace("empty"),100,0,true,Rotation.NONE);
        TESTS.getEntries().forEach(t->event.registerTest(t.getId(),new FunctionGameTestInstance(t.getKey(),data)));
    }
    public static void init(){
        NeoForge.EVENT_BUS.addListener(WorldActions::onTick);NeoForge.EVENT_BUS.addListener(WorldActions::death);NeoForge.EVENT_BUS.addListener(WorldActions::commands);NeoForge.EVENT_BUS.addListener(WorldActions::nativeResource);NeoForge.EVENT_BUS.addListener(WorldActions::resourceSpeed);
    }
    private static void nativeResource(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel)||SurvivalApi.exempt(event.getEntity()))return;var state=event.getLevel().getBlockState(event.getPos());if(!(state.getBlock() instanceof GroundResourceBlock)&&!(state.getBlock() instanceof FruitTreeBlock))return;
        event.setCanceled(true);if(event.getAction()!=net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START)return;
        var actor=event.getEntity();if(state.getBlock() instanceof GroundResourceBlock)actor.sendOverlayMessage(Component.literal(start(actor,"gather",event.getPos()).message()));
        else if(actor.isShiftKeyDown())actor.sendOverlayMessage(Component.literal(start(actor,"mine",event.getPos()).message()));
        else if(((FruitTreeBlock)state.getBlock()).harvestable(actor,event.getPos()))actor.sendOverlayMessage(Component.literal(start(actor,"harvest",event.getPos()).message()));
        else actor.sendOverlayMessage(Component.literal("No ripe fruit available. Sneak-left-click to dismantle the tree with timed work."));
    }
    private static void resourceSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event){if(event.getEntity().level() instanceof ServerLevel&&!SurvivalApi.exempt(event.getEntity())&&(event.getState().getBlock() instanceof GroundResourceBlock||event.getState().getBlock() instanceof FruitTreeBlock))event.setNewSpeed(0);}
    private static SurvivalApi.ActionResult result(boolean ok,String text){return new SurvivalApi.ActionResult(ok,text);}
    private static String actorError(LivingEntity actor){
        if(!(actor.level() instanceof ServerLevel)||!actor.isAlive()||!SurvivalApi.isHumanoid(actor))return "Living server humanoid required";
        if(actor instanceof Player p&&(p.isSpectator()||p.isCreative()))return "Manual survival work requires survival or adventure mode";
        return "";
    }
    private static double capacity(LivingEntity actor){var s=SurvivalApi.state(actor);return Math.min(s.manipulation(),s.mental());}
    private static Skill jobSkill(CompoundTag job){try{return Skill.valueOf(job.getStringOr("skill",""));}catch(IllegalArgumentException absent){return null;}}
    private static InteractionHand jobHand(CompoundTag job){try{return InteractionHand.valueOf(job.getStringOr("hand",InteractionHand.MAIN_HAND.name()));}catch(IllegalArgumentException invalid){return InteractionHand.MAIN_HAND;}}
    private static double jobRate(LivingEntity actor,CompoundTag job){Skill skill=jobSkill(job);return capacity(actor)*(skill==null?1:SkillApi.workRate(actor,skill));}
    private static String blockId(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
    private static boolean reachable(LivingEntity actor,BlockPos pos){return actor.level().hasChunkAt(pos)&&actor.distanceToSqr(Vec3.atCenterOf(pos))<=RANGE_SQUARED;}
    private static boolean hoe(ItemStack tool){return tool.is(net.minecraft.tags.ItemTags.HOES);}
    private static String validate(LivingEntity actor,String operation,BlockPos pos,ItemStack seed,InteractionHand hand){
        var level=(ServerLevel)actor.level();if(!reachable(actor,pos))return "Work must remain within four blocks in a loaded chunk";
        if(!level.mayInteract(actor,pos)||!level.getWorldBorder().isWithinBounds(pos))return "World protection prevents this action";
        var state=level.getBlockState(pos);var held=actor.getItemInHand(hand);
        if(actor instanceof Player player&&!player.mayBuild()&&!operation.equals("sow")){
            boolean denied=operation.equals("till")?!held.canPlaceOnBlockInAdventureMode(new net.minecraft.world.level.block.state.pattern.BlockInWorld(level,pos,false)):hand==InteractionHand.MAIN_HAND?player.blockActionRestricted(level,pos,net.minecraft.world.level.GameType.ADVENTURE):!held.canBreakBlockInAdventureMode(new net.minecraft.world.level.block.state.pattern.BlockInWorld(level,pos,false));
            if(denied)return "Adventure mode requires an item authorized for this block action";
        }
        switch(operation){
            case "gather":return state.getBlock() instanceof GroundResourceBlock?"":"Target is not a hand-gatherable patch";
            case "harvest":{
                if(state.getBlock() instanceof FruitTreeBlock)return ((FruitTreeBlock)state.getBlock()).harvestable(actor,pos)?"":"Tree is juvenile, out of season, or already harvested";
                if(!SeasonalCrops.requiresCalendarTick(state)&&!state.is(Blocks.MELON)&&!state.is(Blocks.PUMPKIN))return "Target has no declared crop harvest";
                if(SeasonalCrops.berries(state)&&!SeasonalCrops.harvestReady(level,pos,state))return "Berries require suitable growth days and summer/autumn harvest season";
                return "";
            }
            case "mine":{
                if(state.isAir()||state.getDestroySpeed(level,pos)<0||!state.getFluidState().isEmpty())return "Target cannot be mined";
                if(level.getBlockEntity(pos)!=null)return "Empty and dismantle containers or workstations through their interaction";
                if(state.requiresCorrectToolForDrops()&&!held.isCorrectToolForDrops(state))return "A correct harvest tool is required";
                return "";
            }
            case "till":return hoe(held)&&(state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT_PATH))&&level.isEmptyBlock(pos.above())?"":"Hold a hoe beside unobstructed dirt or grass";
            case "sow":{
                Block crop=plant(seed);if(crop==null)return "Hold a declared crop seed in the main hand";
                var cropKey=BuiltInRegistries.BLOCK.getKey(crop);String id=cropKey.getPath();long now=SurvivalApi.calendar(level);
                if(!state.isAir())return "Sow into an empty position";
                if((crop instanceof CropBlock||crop instanceof StemBlock)&&(!level.getBlockState(pos.below()).is(Blocks.FARMLAND)||level.getBlockState(pos.below()).getValue(FarmlandBlock.MOISTURE)<=0))return "This crop requires moist prepared farmland";
                if(!SeasonalCrops.sowable(cropKey.toString(),now)||(crop instanceof FruitTreeBlock&&SeasonalCrops.season(now)!=0&&SeasonalCrops.season(now)!=2))return "Outside this crop's seasonal sowing window";
                if(id.equals("rice_crop")&&!SeasonalCrops.flooded(level,pos))return "Rice requires a flooded paddy";
                var planted=plantingState(level,pos,seed);if(planted==null)return "Crop cannot survive on its actual substrate or support";
                if(actor instanceof Player player&&!player.mayBuild()&&!seed.canPlaceOnBlockInAdventureMode(new net.minecraft.world.level.block.state.pattern.BlockInWorld(level,plantingSupport(pos,planted),false)))return "Adventure mode requires CAN_PLACE_ON permission for the planting support";
                return "";
            }
            default:return "Use gather, harvest, mine, till, or sow";
        }
    }
    private static Block plant(ItemStack seed){
        String id=BuiltInRegistries.ITEM.getKey(seed.getItem()).toString();
        return switch(id){
            case "minecraft:wheat_seeds"->Blocks.WHEAT;case "minecraft:carrot"->Blocks.CARROTS;case "minecraft:potato"->Blocks.POTATOES;case "minecraft:beetroot_seeds"->Blocks.BEETROOTS;
            case "minecraft:melon_seeds"->Blocks.MELON_STEM;case "minecraft:pumpkin_seeds"->Blocks.PUMPKIN_STEM;
            case "spawn_9b5fbb9:rice_seeds"->SurvivalContent.CROPS.get("rice").get();case "spawn_9b5fbb9:maize_seeds"->SurvivalContent.CROPS.get("maize").get();
            case "spawn_9b5fbb9:fiber_seeds"->SurvivalContent.CROPS.get("fiber").get();case "spawn_9b5fbb9:medicinal_seeds"->SurvivalContent.CROPS.get("medicinal").get();case "spawn_9b5fbb9:fruit_tree"->SurvivalContent.FRUIT_TREE.get();
            default->seed.getItem() instanceof BlockItem blockItem&&SeasonalCrops.schedule(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString())!=null?blockItem.getBlock():null;
        };
    }
    private static BlockState plantingState(ServerLevel level,BlockPos pos,ItemStack seed){
        Block crop=plant(seed);if(crop==null)return null;var state=crop.defaultBlockState();
        if(crop instanceof CocoaBlock){for(var direction:Direction.Plane.HORIZONTAL){var facing=state.setValue(CocoaBlock.FACING,direction);if(facing.canSurvive(level,pos))return facing;}return null;}
        if(crop instanceof FruitTreeBlock&&!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return null;
        return state.canSurvive(level,pos)?state:null;
    }
    private static BlockPos plantingSupport(BlockPos pos,BlockState state){return state.getBlock() instanceof CocoaBlock?pos.relative(state.getValue(CocoaBlock.FACING)):state.getBlock() instanceof CaveVinesBlock?pos.above():pos.below();}
    public static SurvivalApi.ActionResult forage(LivingEntity actor,BlockPos target){return start(actor,"gather",target);}
    public static SurvivalApi.ActionResult hunt(LivingEntity actor,Animal target){
        String invalid=actorError(actor);if(!invalid.isEmpty())return result(false,invalid);
        if(actor.getPersistentData().contains(KEY)||LaborGuard.forgeBusy(actor)||LaborGuard.gridBusy(actor))return result(false,"Finish the current manual job before hunting");
        if(capacity(actor)<=.05)return result(false,"Manipulation or mental capacity prevents hunting");
        if(!quarry(actor,target))return result(false,"Target a living natural animal within sixteen loaded blocks");
        if(!weapon(actor.getMainHandItem()))return result(false,"Hold a usable physical weapon to hunt");
        var job=baseJob(actor,"hunt",actor.blockPosition(),20);job.putString("target",target.getUUID().toString());job.putString("skill",Skill.HUNTING.name());job.putString("tool",BuiltInRegistries.ITEM.getKey(actor.getMainHandItem().getItem()).toString());job.putInt("damage",actor.getMainHandItem().getDamageValue());
        actor.getPersistentData().put(KEY,job);return result(true,"Hunting started; move into actual weapon reach and line of sight. NPC navigation searches only loaded terrain.");
    }
    private static boolean weapon(ItemStack stack){return !stack.isEmpty()&&!NaturalWorldRules.prohibited(stack)&&stack.has(DataComponents.WEAPON)&&stack.isDamageableItem()&&stack.getDamageValue()<stack.getMaxDamage()&&stack.getAttributeModifiers().compute(Attributes.ATTACK_DAMAGE,1,EquipmentSlot.MAINHAND)>1;}
    private static boolean quarry(LivingEntity actor,Animal target){return target!=null&&target!=actor&&target.isAlive()&&target.level()==actor.level()&&target.getType().builtInRegistryHolder().is(REAL_ANIMALS)&&!NaturalWorldRules.prohibited(target)&&actor.level().hasChunkAt(target.blockPosition())&&actor.distanceToSqr(target)<=HUNT_RADIUS*HUNT_RADIUS&&actor.canAttack(target);}
    public static SurvivalApi.ActionResult start(LivingEntity actor,String operation,BlockPos target){return start(actor,operation,target,InteractionHand.MAIN_HAND);}
    public static SurvivalApi.ActionResult start(LivingEntity actor,String operation,BlockPos target,InteractionHand hand){
        if(operation.equals("forage"))operation="gather";
        String invalid=actorError(actor);if(!invalid.isEmpty())return result(false,invalid);
        if(hand==null||hand!=InteractionHand.MAIN_HAND&&!operation.equals("sow")&&!operation.equals("till")&&!operation.equals("harvest"))return result(false,"Use the main hand for this action; sowing, tilling and harvesting also accept the offhand");
        if(LaborGuard.forgeBusy(actor)||LaborGuard.gridBusy(actor))return result(false,"Finish or leave the current manual forge or crafting grid before starting another job");
        if(actor.getPersistentData().contains(KEY))return result(false,"Already working; inspect jobs or cancel first");
        if(capacity(actor)<=.05)return result(false,"Manipulation or mental capacity prevents manual work");
        var level=(ServerLevel)actor.level();var seed=actor.getItemInHand(hand);invalid=validate(actor,operation,target,seed,hand);if(!invalid.isEmpty())return result(false,invalid);
        var state=level.getBlockState(target);double ticks=switch(operation){case "gather"->40;case "harvest"->60;case "till"->100;case "sow"->60;default->Math.max(30,Math.ceil(state.getDestroySpeed(level,target)*30/Math.max(1,seed.getDestroySpeed(state))));};
        var job=baseJob(actor,operation,target,ticks);job.putString("block",blockId(state));job.putString("hand",hand.name());
        Skill skill=operation.equals("gather")?Skill.FORAGING:operation.equals("till")||operation.equals("sow")?Skill.FARMING:operation.equals("harvest")?SeasonalCrops.harvestSkill(level,target,state):null;if(skill!=null)job.putString("skill",skill.name());
        if(operation.equals("mine")||operation.equals("till")){job.putString("tool",BuiltInRegistries.ITEM.getKey(seed.getItem()).toString());job.putInt("damage",seed.getDamageValue());}
        if(operation.equals("sow"))putStack(actor,job,"seed",seed.split(1));
        actor.getPersistentData().put(KEY,job);return result(true,"Started "+operation+"; "+(long)ticks+" base loaded work ticks");
    }
    private static CompoundTag baseJob(LivingEntity actor,String operation,BlockPos target,double ticks){
        var job=new CompoundTag();job.putString("operation",operation);job.putLong("pos",target.asLong());job.putString("dimension",actor.level().dimension().identifier().toString());job.putDouble("required",ticks);job.putDouble("progress",0);job.putString("status","working");return job;
    }
    private static void putStack(LivingEntity actor,CompoundTag job,String name,ItemStack stack){job.put(name,ItemStack.OPTIONAL_CODEC.encodeStart(actor.registryAccess().createSerializationContext(NbtOps.INSTANCE),stack).getOrThrow());}
    private static ItemStack stack(LivingEntity actor,CompoundTag job,String name){var tag=job.get(name);return tag==null?ItemStack.EMPTY:ItemStack.OPTIONAL_CODEC.parse(actor.registryAccess().createSerializationContext(NbtOps.INSTANCE),tag).result().orElse(ItemStack.EMPTY);}
    private static void onTick(EntityTickEvent.Post event){if(event.getEntity() instanceof LivingEntity actor)tick(actor);}
    private static void death(LivingDeathEvent event){cancel(event.getEntity(),"Worker died");}
    /** Actual loaded entity-tick adapter. Package visibility allows deterministic GameTests without exposing labor credit to controllers. */
    static void tick(LivingEntity actor){
        if(!(actor.level() instanceof ServerLevel level)||!actor.getPersistentData().contains(KEY))return;
        Integer last=TICKED.put(actor,actor.tickCount);if(last!=null&&last==actor.tickCount)return;
        var job=actor.getPersistentData().getCompoundOrEmpty(KEY);String operation=job.getStringOr("operation","");var pos=BlockPos.of(job.getLongOr("pos",0));
        if(operation.equals("hunt")){tickHunt(actor,level,job,pos);return;}
        if(jobSkill(job)==null){Skill migrated=operation.equals("gather")?Skill.FORAGING:operation.equals("till")||operation.equals("sow")?Skill.FARMING:operation.equals("harvest")?SeasonalCrops.harvestSkill(level,pos,level.getBlockState(pos)):null;if(migrated!=null)job.putString("skill",migrated.name());}
        if(!actorError(actor).isEmpty()||!job.getStringOr("dimension","").equals(level.dimension().identifier().toString())||!reachable(actor,pos)){cancel(actor,"Worker dead, changed dimension, or out of range");return;}
        String invalid="";
        if(operation.equals("craft")&&job.getBooleanOr("table",false)&&!level.getBlockState(pos).is(Blocks.CRAFTING_TABLE))invalid="Crafting table changed";
        if(!operation.equals("craft")){
            if(!job.getStringOr("block","").equals(blockId(level.getBlockState(pos))))invalid="Target block changed";
            else invalid=validate(actor,operation,pos,operation.equals("sow")?stack(actor,job,"seed"):actor.getItemInHand(jobHand(job)),jobHand(job));
            if(job.contains("tool")&&(!job.getStringOr("tool","").equals(BuiltInRegistries.ITEM.getKey(actor.getItemInHand(jobHand(job)).getItem()).toString())||job.getIntOr("damage",0)!=actor.getItemInHand(jobHand(job)).getDamageValue()))invalid="Tool changed";
        }
        if(!invalid.isEmpty()){cancel(actor,invalid);return;}
        double capacity=capacity(actor);if(capacity<=.05){job.putString("status","paused: physiological capacity");actor.getPersistentData().put(KEY,job);return;}
        job.putString("status","working");double worked=Math.min(jobRate(actor,job),job.getDoubleOr("required",1)-job.getDoubleOr("progress",0));job.putDouble("progress",job.getDoubleOr("progress",0)+worked);
        LaborGuard.worked(actor);
        var body=SurvivalApi.state(actor);body.hunger=Math.max(0,body.hunger-.0005);SurvivalApi.save(actor,body);SurvivalApi.syncFood(actor,body);
        actor.getPersistentData().put(KEY,job);
        if(job.getDoubleOr("progress",0)+1e-8<job.getDoubleOr("required",1))return;
        finish(actor,job,pos,operation);
    }
    private static void tickHunt(LivingEntity actor,ServerLevel level,CompoundTag job,BlockPos origin){
        if(!actorError(actor).isEmpty()||!job.getStringOr("dimension","").equals(level.dimension().identifier().toString())){cancel(actor,"Hunter died or changed dimension");return;}
        Entity found;try{found=level.getEntity(UUID.fromString(job.getStringOr("target","")));}catch(IllegalArgumentException malformed){cancel(actor,"Invalid quarry identity");return;}
        if(found instanceof Animal dead&&!dead.isAlive()){finishHunt(actor,"Quarry died; practice follows its actual killer");return;}
        if(!(found instanceof Animal target)||!quarry(actor,target)||actor.distanceToSqr(Vec3.atCenterOf(origin))>HUNT_RADIUS*HUNT_RADIUS||target.distanceToSqr(Vec3.atCenterOf(origin))>HUNT_RADIUS*HUNT_RADIUS){cancel(actor,"Quarry left the living, loaded hunting area");return;}
        var held=actor.getMainHandItem();if(!weapon(held)||!job.getStringOr("tool","").equals(BuiltInRegistries.ITEM.getKey(held.getItem()).toString())||job.getIntOr("damage",0)!=held.getDamageValue()){cancel(actor,"Hunting weapon changed or exhausted");return;}
        if(capacity(actor)<=.05){if(actor instanceof Mob mob)mob.getNavigation().stop();job.putString("status","paused: physiological capacity");actor.getPersistentData().put(KEY,job);return;}
        boolean contact=actor.getAttackRangeWith(held).isInRange(actor,target.getBoundingBox(),0)&&actor.hasLineOfSight(target);
        if(!contact){
            int pursuing=job.getIntOr("pursuitTicks",0)+1;if(pursuing>1200){cancel(actor,"Quarry could not be reached in sixty loaded seconds");return;}job.putInt("pursuitTicks",pursuing);
            job.putString("status",actor instanceof Mob?"pursuing within loaded terrain":"move into weapon reach and line of sight");
            if(actor instanceof Mob mob&&(actor.tickCount%10==0||mob.getNavigation().isDone()))pursue(mob,target,origin);
            actor.getPersistentData().put(KEY,job);return;
        }
        if(actor instanceof Mob mob)mob.getNavigation().stop();job.putInt("pursuitTicks",0);job.putString("status","aiming within actual reach");job.putDouble("progress",job.getDoubleOr("progress",0)+jobRate(actor,job));
        LaborGuard.worked(actor);var body=SurvivalApi.state(actor);body.hunger=Math.max(0,body.hunger-.0005);SurvivalApi.save(actor,body);SurvivalApi.syncFood(actor,body);actor.getPersistentData().put(KEY,job);
        if(job.getDoubleOr("progress",0)+1e-8<job.getDoubleOr("required",20))return;
        var source=actor instanceof Player player?actor.damageSources().playerAttack(player):actor.damageSources().mobAttack(actor);
        double base=held.getAttributeModifiers().compute(Attributes.ATTACK_DAMAGE,1,EquipmentSlot.MAINHAND);float damage=(float)Math.max(0,(base+held.getItem().getAttackDamageBonus(target,(float)base,source))*SurvivalApi.state(actor).manipulation());
        // Real hurtServer drives protection, actual attack hunger, death/loot, and the shared kill-practice listener.
        boolean hit=damage>0&&target.hurtServer(level,source,damage);actor.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);
        if(hit){held.hurtEnemy(target,actor);held.postHurtEnemy(target,actor);actor.setLastHurtMob(target);}
        SurvivalApi.syncFood(actor,SurvivalApi.state(actor));
        if(!target.isAlive()){finishHunt(actor,"Hunt completed through real animal damage and loot");return;}
        if(!weapon(held)){cancel(actor,"Weapon exhausted during hunting");return;}
        job.putInt("damage",held.getDamageValue());job.putDouble("progress",0);job.putString("status",hit?"strike landed":"quarry resisted the strike");actor.getPersistentData().put(KEY,job);
    }
    private static void finishHunt(LivingEntity actor,String message){actor.getPersistentData().remove(KEY);if(actor instanceof Mob mob)mob.getNavigation().stop();actor.getPersistentData().putString("survivalLastJob",message);}
    private static void pursue(Mob actor,Animal target,BlockPos origin){
        var level=(ServerLevel)actor.level();int radius=24;
        for(int cx=Math.floorDiv(actor.blockPosition().getX()-radius,16);cx<=Math.floorDiv(actor.blockPosition().getX()+radius,16);cx++)for(int cz=Math.floorDiv(actor.blockPosition().getZ()-radius,16);cz<=Math.floorDiv(actor.blockPosition().getZ()+radius,16);cz++)if(!level.hasChunkAt(new BlockPos(cx*16,actor.blockPosition().getY(),cz*16))){actor.getNavigation().stop();return;}
        var path=actor.getNavigation().createPath(target.blockPosition(),0,16);if(path==null)return;
        for(int i=0;i<path.getNodeCount();i++){var pos=path.getNode(i).asBlockPos();if(!level.hasChunkAt(pos)||pos.distSqr(origin)>HUNT_RADIUS*HUNT_RADIUS){actor.getNavigation().stop();return;}}
        actor.getLookControl().setLookAt(target);actor.getNavigation().moveTo(path,1.0);
    }
    private static void finish(LivingEntity actor,CompoundTag job,BlockPos pos,String operation){
        var level=(ServerLevel)actor.level();boolean completed=false,useful=true;
        COMPLETING.add(actor);
        try{
            switch(operation){
                case "gather":completed=((GroundResourceBlock)level.getBlockState(pos).getBlock()).completeGather(actor,pos);break;
                case "harvest":{
                    var before=level.getBlockState(pos);if(before.getBlock() instanceof FruitTreeBlock tree){completed=tree.completeHarvest(actor,pos);break;}
                    // Reconcile accumulated suitable days and frost before consulting the actual block/drop events.
                    SeasonalCrops.randomTick(before,level,pos,level.getRandom());var current=level.getBlockState(pos);
                    useful=SeasonalCrops.harvestReady(level,pos,current);
                    if(!current.isAir()&&current.getBlock()==before.getBlock())completed=SeasonalCrops.berries(current)?SeasonalCrops.harvestBerries(actor,pos):level.destroyBlock(pos,true,actor);
                    break;
                }
                case "mine":{
                    var state=level.getBlockState(pos);var tool=actor.getMainHandItem();
                    completed=level.destroyBlock(pos,false,actor);
                    if(completed){Block.dropResources(state,level,pos,null,actor,tool);tool.getItem().mineBlock(tool,level,state,pos,actor);SurvivalApi.syncFood(actor,SurvivalApi.state(actor));}break;
                }
                case "till":completed=level.setBlock(pos,Blocks.FARMLAND.defaultBlockState(),3);if(completed)wear(actor,1,jobHand(job));break;
                case "sow":{
                    var seed=stack(actor,job,"seed");var planted=plantingState(level,pos,seed);if(planted==null)break;
                    completed=level.setBlock(pos,planted,3);
                    if(completed){var all=SeasonalCrops.records(level);var record=new CompoundTag();long now=SurvivalApi.calendar(level);record.putLong("born",now);record.putLong("last",now);record.putBoolean("sown",true);record.putString("type",BuiltInRegistries.BLOCK.getKey(planted.getBlock()).toString());all.put(Long.toString(pos.asLong()),record);SeasonalCrops.save(level,all);}
                    break;
                }
                case "craft":completed=finishCraft(actor,job);break;
            }
        }finally{COMPLETING.remove(actor);}
        if(completed){actor.getPersistentData().remove(KEY);actor.getPersistentData().putString("survivalLastJob","Completed "+operation);Skill skill=jobSkill(job);if(skill!=null&&useful)SkillApi.award(actor,skill,operation.equals("harvest")?6:operation.equals("sow")?3:operation.equals("till")?2:4);}
        else cancel(actor,"Work target or recipe changed before completion");
    }
    public static boolean completing(LivingEntity actor){return COMPLETING.contains(actor);}
    private static void wear(LivingEntity actor,int amount,InteractionHand hand){actor.getItemInHand(hand).hurtAndBreak(amount,actor,hand);}
    public static SurvivalApi.ActionResult cancel(LivingEntity actor,String reason){
        if(!(actor.level() instanceof ServerLevel))return result(false,"No active job");
        if(!actor.getPersistentData().contains(KEY))return actor.isAlive()?SurvivalApi.work(actor,"cancel",""):result(false,"No active job");
        var job=actor.getPersistentData().getCompoundOrEmpty(KEY);actor.getPersistentData().remove(KEY);
        if(job.getStringOr("operation","").equals("hunt")&&actor instanceof Mob mob)mob.getNavigation().stop();
        var reserved=new ArrayList<ItemStack>();reserved.add(stack(actor,job,"seed"));for(int i=0;i<job.getIntOr("slots",0);i++)reserved.add(stack(actor,job,"input"+i));
        for(var item:reserved)if(!item.isEmpty()){ItemAging.update(item,(ServerLevel)actor.level(),1);if(actor instanceof Player||actor.isAlive())SurvivalApi.give(actor,item);else actor.spawnAtLocation((ServerLevel)actor.level(),item);}
        actor.getPersistentData().putString("survivalLastJob","Cancelled: "+reason);return result(true,"Cancelled; reserved supplies returned. "+reason);
    }

    private record Supply(Supplier<ItemStack> get,Consumer<ItemStack> set){}
    private static List<Supply> supplies(LivingEntity actor){
        List<Supply> out=new ArrayList<>();
        if(actor instanceof Player player)addContainer(out,player.getInventory());
        else {out.add(new Supply(actor::getMainHandItem,s->actor.setItemSlot(EquipmentSlot.MAINHAND,s)));out.add(new Supply(actor::getOffhandItem,s->actor.setItemSlot(EquipmentSlot.OFFHAND,s)));if(actor instanceof InventoryCarrier carrier)addContainer(out,carrier.getInventory());}
        var level=(ServerLevel)actor.level();
        for(var p:BlockPos.betweenClosed(actor.blockPosition().offset(-3,-2,-3),actor.blockPosition().offset(3,2,3)))if(level.hasChunkAt(p)&&actor.distanceToSqr(Vec3.atCenterOf(p))<=9&&level.getBlockEntity(p) instanceof Container c)addContainer(out,c);
        for(var drop:level.getEntitiesOfClass(ItemEntity.class,actor.getBoundingBox().inflate(3),e->e.isAlive()&&actor.distanceToSqr(e)<=9))out.add(new Supply(drop::getItem,s->{if(s.isEmpty())drop.discard();else drop.setItem(s);}));
        return out;
    }
    private static void addContainer(List<Supply> out,Container c){for(int i=0;i<c.getContainerSize();i++){final int slot=i;out.add(new Supply(()->c.getItem(slot),s->{c.setItem(slot,s);c.setChanged();}));}}
    private static CraftingRecipe recipe(ServerLevel level,String id){
        try{return level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse(id))).map(RecipeHolder::value).filter(CraftingRecipe.class::isInstance).map(CraftingRecipe.class::cast).orElse(null);}catch(IllegalArgumentException invalid){return null;}
    }
    private static boolean assign(List<Ingredient> ingredients,List<Supply> supplies,int[] used,int[] choices,int cursor,int[] budget){
        if(cursor==ingredients.size())return true;if(--budget[0]<0)return false;
        for(int i=0;i<supplies.size();i++){ItemStack stack=supplies.get(i).get.get();if(stack.getCount()<=used[i]||!ingredients.get(cursor).test(stack))continue;used[i]++;choices[cursor]=i;if(assign(ingredients,supplies,used,choices,cursor+1,budget))return true;used[i]--;}
        return false;
    }
    private static int width(CraftingRecipe recipe){int size=recipe.placementInfo().slotsToIngredientIndex().size();return recipe instanceof ShapedRecipe shaped?shaped.getWidth():Math.min(size<=4?2:3,size);}
    private static int height(CraftingRecipe recipe){return recipe instanceof ShapedRecipe shaped?shaped.getHeight():(int)Math.ceil(recipe.placementInfo().slotsToIngredientIndex().size()/(double)width(recipe));}
    private static BlockPos workTable(LivingEntity actor){for(var pos:BlockPos.betweenClosed(actor.blockPosition().offset(-4,-3,-4),actor.blockPosition().offset(4,3,4)))if(reachable(actor,pos)&&actor.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE))return pos.immutable();return null;}
    public static long craftTicks(CraftingRecipe recipe){return (long)CraftingGuard.REQUIRED;}
    public static SurvivalApi.ActionResult craft(LivingEntity actor,String recipeId){
        String invalid=actorError(actor);if(!invalid.isEmpty())return result(false,invalid);
        if(LaborGuard.forgeBusy(actor)||LaborGuard.gridBusy(actor))return result(false,"Finish or leave the current manual forge or crafting grid before handcrafting");
        if(actor.getPersistentData().contains(KEY))return result(false,"Already working; cancel or finish first");
        if(capacity(actor)<=.05)return result(false,"Manipulation or mental capacity prevents crafting");
        var level=(ServerLevel)actor.level();var recipe=recipe(level,recipeId);
        if(recipe==null||recipe.isSpecial()||recipe.placementInfo().isImpossibleToPlace())return result(false,"A registered normal crafting recipe with declared ingredients is required; use produce for kiln or forge work");
        int width=width(recipe),height=height(recipe);if(width<1||height<1||width>3||height>3)return result(false,"Recipe exceeds the manual three by three work area");
        boolean needsTable=width>2||height>2;BlockPos table=needsTable?workTable(actor):null;if(needsTable&&table==null)return result(false,"This recipe requires an actual crafting table within four blocks");
        var ingredients=recipe.placementInfo().ingredients();var supplies=supplies(actor);var choices=new int[ingredients.size()];
        if(!assign(ingredients,supplies,new int[supplies.size()],choices,0,new int[]{100000}))return result(false,"Missing actual recipe supplies in inventory, hands, or reachable containers/drops");
        var grid=new ArrayList<ItemStack>(Collections.nCopies(width*height,ItemStack.EMPTY));var mapping=recipe.placementInfo().slotsToIngredientIndex();
        for(int i=0;i<mapping.size();i++)if(mapping.getInt(i)>=0)grid.set(i,supplies.get(choices[mapping.getInt(i)]).get.get().copyWithCount(1));
        CraftingInput input=CraftingInput.of(width,height,grid);
        if(!recipe.matches(input,level))return result(false,"Server recipe rejected the actual supplied materials");
        ItemStack output=recipe.assemble(input);if(output.isEmpty()||NaturalWorldRules.prohibited(output))return result(false,"Recipe output is unavailable under the natural-world rules");
        if(Production.requiresStation(output))return result(false,"This output requires its practiced production station; use produce instead of a grid recipe");
        var job=baseJob(actor,"craft",table==null?actor.blockPosition():table,craftTicks(recipe));job.putBoolean("table",needsTable);job.putString("recipe",recipeId);job.putInt("width",input.width());job.putInt("height",input.height());job.putInt("slots",input.size());
        // Commit only after every match/output check; keep exact real components in the reservation.
        for(int i=0;i<ingredients.size();i++){var source=supplies.get(choices[i]);var live=source.get.get();live.shrink(1);source.set.accept(live);}
        for(int i=0;i<input.size();i++)putStack(actor,job,"input"+i,input.getItem(i));
        actor.getPersistentData().put(KEY,job);return result(true,"Started "+recipeId+"; "+craftTicks(recipe)+" base loaded work ticks; supplies reserved");
    }
    private static boolean finishCraft(LivingEntity actor,CompoundTag job){
        var level=(ServerLevel)actor.level();var recipe=recipe(level,job.getStringOr("recipe",""));if(recipe==null)return false;
        List<ItemStack> ingredients=new ArrayList<>();for(int i=0;i<job.getIntOr("slots",0);i++)ingredients.add(stack(actor,job,"input"+i));
        var input=CraftingInput.of(job.getIntOr("width",0),job.getIntOr("height",0),ingredients);if(!recipe.matches(input,level))return false;
        var output=recipe.assemble(input);if(output.isEmpty()||NaturalWorldRules.prohibited(output)||Production.requiresStation(output))return false;
        var remainder=recipe.getRemainingItems(input);ItemAging.inherit(output,ingredients,level);
        // Clear persisted reservation before granting items so reentrant inspection cannot claim it twice.
        actor.getPersistentData().remove(KEY);SurvivalApi.give(actor,output);for(var item:remainder)if(!NaturalWorldRules.prohibited(item))SurvivalApi.give(actor,item);return true;
    }
    public static JsonObject inspectJob(LivingEntity actor){
        var json=new JsonObject();boolean active=actor.getPersistentData().contains(KEY);json.addProperty("active",active);
        if(active){var job=actor.getPersistentData().getCompoundOrEmpty(KEY);json.addProperty("operation",job.getStringOr("operation",""));json.addProperty("status",job.getStringOr("status",""));json.addProperty("progress",job.getDoubleOr("progress",0));json.addProperty("required",job.getDoubleOr("required",0));json.addProperty("recipe",job.getStringOr("recipe",""));json.addProperty("target",job.getStringOr("target",""));var pos=BlockPos.of(job.getLongOr("pos",0));json.addProperty("x",pos.getX());json.addProperty("y",pos.getY());json.addProperty("z",pos.getZ());json.addProperty("loadedTicksRemainingAtCurrentCapacity",capacity(actor)<=.05?-1:Math.ceil((job.getDoubleOr("required",0)-job.getDoubleOr("progress",0))/jobRate(actor,job)));var skill=jobSkill(job);if(skill!=null){json.addProperty("skill",skill.name().toLowerCase(Locale.ROOT));json.addProperty("skillLevel",SkillApi.level(actor,skill));json.addProperty("skilledWorkRate",jobRate(actor,job));}}
        else json.addProperty("last",actor.getPersistentData().getStringOr("survivalLastJob","Idle"));return json;
    }
    public static JsonObject planning(LivingEntity actor){return planning(actor,0,0);}
    public static JsonObject planning(LivingEntity actor,int offset){return planning(actor,offset,0);}
    public static JsonObject planning(LivingEntity actor,int offset,int cropOffset){
        var json=new JsonObject();if(!(actor.level() instanceof ServerLevel level)||!SurvivalApi.isHumanoid(actor)){json.addProperty("error","Server humanoid required");return json;}
        long now=SurvivalApi.calendar(level);var body=SurvivalApi.state(actor);json.addProperty("calendar",now);json.addProperty("season",SeasonalCrops.season(now));json.addProperty("seasonDay",SeasonalCrops.seasonDay(now));json.addProperty("movement",body.movement());json.addProperty("manipulation",body.manipulation());json.addProperty("mental",body.mental());json.addProperty("manualCapacity",capacity(actor));json.addProperty("conditions",body.conditions());json.addProperty("workRange",4);json.addProperty("supplyRange",3);json.addProperty("hungerPerLoadedWorkTick",.0005);json.addProperty("unloadedLabor",false);json.add("job",inspectJob(actor));
        json.add("skills",SkillApi.inspect(body));var hunting=new JsonObject();hunting.addProperty("weaponAvailable",weapon(actor.getMainHandItem()));hunting.addProperty("loadedPursuitRadius",HUNT_RADIUS);hunting.addProperty("weaponReach",actor.getAttackRangeWith(actor.getMainHandItem()).effectiveMaxRange(actor));hunting.addProperty("lineOfSightRequired",true);hunting.addProperty("npcNavigation",actor instanceof Mob);hunting.addProperty("baseStrikeWorkTicks",20);hunting.addProperty("practiceOnActualKill",true);json.add("hunting",hunting);
        var crops=new JsonArray();for(var entry:new TreeMap<>(SeasonalCrops.schedules()).entrySet()){
            String id=entry.getKey();var rule=entry.getValue();var crop=new JsonObject();crop.addProperty("crop",id);crop.addProperty("sowingSeasonMask",rule.mask());crop.addProperty("sowableNow",SeasonalCrops.sowable(id,now));long next=now;for(int i=0;i<=4*dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get();i++){if(SeasonalCrops.sowable(id,next))break;next=(Math.floorDiv(next,24000)+1)*24000;}crop.addProperty("nextSowingCalendar",next);crop.addProperty("moistureRequired",true);crop.addProperty("floodedPaddy",rule.flooded());crop.addProperty("warmClimateRequired",rule.warm());crop.addProperty("frostSensitive",rule.frost());crop.addProperty("growthDays",rule.days());crop.addProperty("lateSpringOrEarlySummerWindow",id.endsWith(":maize_crop")||id.contains("melon"));crops.add(crop);
        }var tree=new JsonObject();tree.addProperty("crop","fruit_tree");tree.addProperty("sowableNow",SeasonalCrops.season(now)==0||SeasonalCrops.season(now)==2);tree.addProperty("establishmentDays",dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()*4);tree.addProperty("fruitingSeasonMask",6);crops.add(tree);json.add("crops",crops);
        // Crop definitions can themselves be extended by packs; keep their page bounded too.
        var cropPage=new JsonArray();int cropCursor=Math.min(Math.max(0,cropOffset),crops.size());json.addProperty("cropOffset",cropCursor);json.addProperty("totalCrops",crops.size());
        while(cropCursor<crops.size()&&cropPage.size()<64){cropPage.add(crops.get(cropCursor));if(cropPage.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8000){cropPage.remove(cropPage.size()-1);break;}cropCursor++;}
        json.add("crops",cropPage);json.addProperty("nextCropOffset",cropCursor<crops.size()?cropCursor:-1);
        var catalog=new ArrayList<PlanEntry>();for(var holder:level.recipeAccess().getRecipes())if(holder.value() instanceof CraftingRecipe recipe&&!recipe.isSpecial()&&!recipe.placementInfo().isImpossibleToPlace()&&width(recipe)<=3&&height(recipe)<=3)catalog.add(new PlanEntry(holder.id().identifier().toString(),recipe,null,null));
        Production.RECIPES.forEach((id,r)->catalog.add(new PlanEntry(SurvivalContent.MODID+":process/"+id,null,r,id)));
        catalog.sort(Comparator.comparingInt((PlanEntry e)->e.id.startsWith(SurvivalContent.MODID+":")?0:1).thenComparing(PlanEntry::id));
        int cursor=Math.min(Math.max(0,offset),catalog.size()),shown=0;json.addProperty("offset",cursor);json.addProperty("totalRecipes",catalog.size());json.addProperty("pageSizeLimit",64);json.addProperty("byteLimit",PLANNING_BYTE_LIMIT);json.addProperty("nextOffset",-1);var recipes=new JsonArray();var processes=new JsonArray();json.add("craftingRecipes",recipes);json.add("processRecipes",processes);
        while(cursor<catalog.size()&&shown<64){var entry=catalog.get(cursor);var info=entry.craft!=null?craftInfo(actor,entry.id,entry.craft,0):processInfo(actor,entry.processId,entry.process);var destination=entry.craft!=null?recipes:processes;destination.add(info);
            if(bytes(json)>PLANNING_BYTE_LIMIT-128){destination.remove(destination.size()-1);if(shown>0)break;info.remove("ingredientSlots");info.addProperty("detailsTruncated",true);destination.add(info);if(bytes(json)>PLANNING_BYTE_LIMIT-128){destination.remove(destination.size()-1);json.addProperty("oversizedEntrySkipped",true);cursor++;break;}}
            cursor++;shown++;
        }
        json.addProperty("nextOffset",cursor<catalog.size()?cursor:-1);if(bytes(json)>PLANNING_BYTE_LIMIT){var error=new JsonObject();error.addProperty("error","Configured planner definitions exceed the response budget");error.addProperty("offset",offset);error.addProperty("nextOffset",cursor<catalog.size()?cursor:-1);return error;}return json;
    }
    private record PlanEntry(String id,CraftingRecipe craft,Production.Recipe process,String processId){}
    private static int bytes(JsonObject json){return json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    private static JsonObject craftInfo(LivingEntity actor,String id,CraftingRecipe recipe,int alternativeOffset){
        var info=new JsonObject();info.addProperty("recipe",id);info.addProperty("kind","craft");info.addProperty("craftingTableRequired",width(recipe)>2||height(recipe)>2);info.addProperty("baseWorkTicks",craftTicks(recipe));double currentCapacity=capacity(actor);info.addProperty("loadedTicksAtCurrentCapacity",currentCapacity<=.05?-1:Math.ceil(craftTicks(recipe)/currentCapacity));var ingredients=new JsonArray();boolean more=false;alternativeOffset=Math.max(0,alternativeOffset);
        for(var ingredient:recipe.placementInfo().ingredients()){var slot=new JsonObject();var choices=ingredient.items().toList();var alternatives=new JsonArray();choices.stream().skip(alternativeOffset).limit(8).forEach(i->alternatives.add(BuiltInRegistries.ITEM.getKey(i.value()).toString()));slot.add("alternatives",alternatives);slot.addProperty("totalAlternatives",choices.size());slot.addProperty("truncated",choices.size()>(long)alternativeOffset+8);more|=choices.size()>(long)alternativeOffset+8;ingredients.add(slot);}info.add("ingredientSlots",ingredients);info.addProperty("alternativeOffset",alternativeOffset);info.addProperty("nextAlternativeOffset",more?alternativeOffset+8:-1);
        List<ItemStack> sample=new ArrayList<>();for(int index:recipe.placementInfo().slotsToIngredientIndex())sample.add(index<0?ItemStack.EMPTY:recipe.placementInfo().ingredients().get(index).items().findFirst().map(i->new ItemStack(i.value())).orElse(ItemStack.EMPTY));while(sample.size()<width(recipe)*height(recipe))sample.add(ItemStack.EMPTY);
        var output=recipe.assemble(CraftingInput.of(width(recipe),height(recipe),sample));info.addProperty("output",BuiltInRegistries.ITEM.getKey(output.getItem()).toString());info.addProperty("outputCount",output.getCount());info.addProperty("permitted",!output.isEmpty()&&!NaturalWorldRules.prohibited(output)&&!Production.requiresStation(output));return info;
    }
    private static JsonObject processInfo(LivingEntity actor,String id,Production.Recipe recipe){
        var info=new JsonObject();info.addProperty("recipe",id);info.addProperty("kind","process");info.addProperty("input",recipe.input());info.addProperty("inputCount",recipe.count());info.addProperty("secondInput",recipe.second());info.addProperty("secondCount",recipe.secondCount());info.addProperty("output",recipe.output());info.addProperty("outputCount",recipe.amount());info.addProperty("baseWorkTicks",recipe.ticks());info.addProperty("forgeRequired",recipe.station().equals("forge"));info.addProperty("stationRequired",recipe.station());info.addProperty("manual",recipe.manual());info.addProperty("heated",recipe.heated());info.addProperty("minimumManipulation",recipe.minimumManipulation());info.addProperty("minimumSkill",recipe.minimumSkill());info.addProperty("practiceOnCompletion",recipe.practice());
        double cap=recipe.manual()?capacity(actor):1;boolean skilled=recipe.skill()==null||SkillApi.level(actor,recipe.skill())>=recipe.minimumSkill();if(recipe.skill()!=null){info.addProperty("skill",recipe.skill().name().toLowerCase(Locale.ROOT));info.addProperty("skillLevel",SkillApi.level(actor,recipe.skill()));info.addProperty("effectiveSkill",SkillApi.effective(actor,recipe.skill()));if(recipe.manual())cap*=SkillApi.workRate(actor,recipe.skill());}info.addProperty("skillAndCapacityPermit",skilled&&capacity(actor)>.05&&SurvivalApi.state(actor).manipulation()>=recipe.minimumManipulation());info.addProperty("loadedTicksAtCurrentCapacity",cap<=.05?-1:Math.ceil(recipe.ticks()/cap));info.addProperty("heatTicks",!recipe.heated()?0:cap<=.05?-1:(long)Math.ceil(recipe.ticks()/cap));info.addProperty("safeWaterRequired",recipe.second().equals("@safe_water"));return info;
    }
    public static JsonObject recipeInfo(LivingEntity actor,String id,int alternativeOffset){
        var invalid=new JsonObject();if(!(actor.level() instanceof ServerLevel level)||!SurvivalApi.isHumanoid(actor)){invalid.addProperty("error","Server humanoid required");return invalid;}
        var process=Production.RECIPES.get(id);var recipe=recipe(level,id);JsonObject info;
        if(process!=null)info=processInfo(actor,id,process);else if(recipe!=null&&!recipe.isSpecial()&&!recipe.placementInfo().isImpossibleToPlace()&&width(recipe)<=3&&height(recipe)<=3)info=craftInfo(actor,id,recipe,alternativeOffset);else{invalid.addProperty("error","Unknown normal crafting or production recipe");return invalid;}
        if(bytes(info)>PLANNING_BYTE_LIMIT){info.remove("ingredientSlots");info.addProperty("detailsTruncated",true);}if(bytes(info)>PLANNING_BYTE_LIMIT){invalid.addProperty("error","Recipe definition exceeds the response budget");return invalid;}return info;
    }
    public static SurvivalApi.ActionResult livestock(LivingEntity actor,net.minecraft.world.entity.animal.Animal animal){
        String invalid=actorError(actor);if(!invalid.isEmpty())return result(false,invalid);if(animal==null)return result(false,"Living farm animal required");if(capacity(actor)<=.05)return result(false,"Physiological incapacity prevents livestock work");int count=actor.getMainHandItem().getCount(),damage=actor.getMainHandItem().getDamageValue();String message=Husbandry.action(actor,animal);return result(count!=actor.getMainHandItem().getCount()||damage!=actor.getMainHandItem().getDamageValue(),message);
    }
    public static SurvivalApi.ActionResult nest(LivingEntity actor,BlockPos pos){String invalid=actorError(actor);if(!invalid.isEmpty())return result(false,invalid);if(capacity(actor)<=.05)return result(false,"Physiological incapacity prevents nesting work");int count=actor.getMainHandItem().getCount();String message=Husbandry.nest(actor,pos);return result(count>actor.getMainHandItem().getCount(),message);}
    public static SurvivalApi.ActionResult trough(LivingEntity actor,BlockPos pos){String invalid=actorError(actor);if(!invalid.isEmpty())return result(false,invalid);if(capacity(actor)<=.05)return result(false,"Physiological incapacity prevents trough work");if(!actor.level().hasChunkAt(pos)||!(actor.level().getBlockEntity(pos) instanceof WorkstationEntity station))return result(false,"Target an actual nearby trough");double feed=station.getPersistentData().getDoubleOr("feed",0),water=station.getPersistentData().getDoubleOr("water",0);String message=Production.stockTrough(actor,station);return result(station.getPersistentData().getDoubleOr("feed",0)>feed||station.getPersistentData().getDoubleOr("water",0)>water,message);}

    private static void commands(RegisterCommandsEvent event){
        var root=literal("survival");addActions(root);var control=literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));var actor=argument("actor",EntityArgument.entity());
        // Actions are literal children, allowing the existing generic controller action adapter to coexist.
        for(String op:new String[]{"gather","forage","harvest","mine","till","sow"})actor.then(literal(op).then(argument("pos",BlockPosArgument.blockPos()).executes(c->reply(c.getSource(),start(living(EntityArgument.getEntity(c,"actor")),op,BlockPosArgument.getLoadedBlockPos(c,"pos"))))));
        actor.then(literal("craft").then(argument("recipe",StringArgumentType.word()).executes(c->reply(c.getSource(),craft(living(EntityArgument.getEntity(c,"actor")),StringArgumentType.getString(c,"recipe"))))));
        actor.then(literal("jobs").executes(c->json(c.getSource(),inspectJob(living(EntityArgument.getEntity(c,"actor"))))));actor.then(literal("cancel").executes(c->reply(c.getSource(),cancel(living(EntityArgument.getEntity(c,"actor")),"Requested"))));addPlannerAndFarmCommands(actor,c->living(EntityArgument.getEntity(c,"actor")));root.then(control.then(actor));event.getDispatcher().register(root);
    }
    private static void addActions(LiteralArgumentBuilder<CommandSourceStack> root){
        for(String op:new String[]{"gather","forage","harvest","mine","till","sow"})root.then(literal(op).then(argument("pos",BlockPosArgument.blockPos()).executes(c->reply(c.getSource(),start(c.getSource().getPlayerOrException(),op,BlockPosArgument.getLoadedBlockPos(c,"pos"))))));
        root.then(literal("craft").then(argument("recipe",StringArgumentType.word()).executes(c->reply(c.getSource(),craft(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"recipe"))))));root.then(literal("jobs").executes(c->json(c.getSource(),inspectJob(c.getSource().getPlayerOrException()))));root.then(literal("cancel").executes(c->reply(c.getSource(),cancel(c.getSource().getPlayerOrException(),"Requested"))));addPlannerAndFarmCommands(root,c->c.getSource().getPlayerOrException());
    }
    @FunctionalInterface private interface ActorLookup{LivingEntity get(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)throws com.mojang.brigadier.exceptions.CommandSyntaxException;}
    private static void addPlannerAndFarmCommands(com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack,?> node,ActorLookup actor){
        node.then(literal("planning").executes(c->json(c.getSource(),planning(actor.get(c))))
            .then(argument("offset",IntegerArgumentType.integer(0)).executes(c->json(c.getSource(),planning(actor.get(c),IntegerArgumentType.getInteger(c,"offset"))))
                .then(argument("cropOffset",IntegerArgumentType.integer(0)).executes(c->json(c.getSource(),planning(actor.get(c),IntegerArgumentType.getInteger(c,"offset"),IntegerArgumentType.getInteger(c,"cropOffset")))))));
        node.then(literal("recipeinfo").then(argument("recipe",StringArgumentType.word()).executes(c->json(c.getSource(),recipeInfo(actor.get(c),StringArgumentType.getString(c,"recipe"),0)))
            .then(argument("alternativeOffset",IntegerArgumentType.integer(0)).executes(c->json(c.getSource(),recipeInfo(actor.get(c),StringArgumentType.getString(c,"recipe"),IntegerArgumentType.getInteger(c,"alternativeOffset")))))));
        node.then(literal("livestock").then(argument("animal",EntityArgument.entity()).executes(c->reply(c.getSource(),livestock(actor.get(c),animal(EntityArgument.getEntity(c,"animal")))))));
        node.then(literal("hunt").then(argument("animal",EntityArgument.entity()).executes(c->reply(c.getSource(),hunt(actor.get(c),animal(EntityArgument.getEntity(c,"animal")))))));
        node.then(literal("nest").then(argument("pos",BlockPosArgument.blockPos()).executes(c->reply(c.getSource(),nest(actor.get(c),BlockPosArgument.getLoadedBlockPos(c,"pos"))))));
        node.then(literal("trough").then(argument("pos",BlockPosArgument.blockPos()).executes(c->reply(c.getSource(),trough(actor.get(c),BlockPosArgument.getLoadedBlockPos(c,"pos"))))));
    }
    private static net.minecraft.world.entity.animal.Animal animal(Entity entity)throws com.mojang.brigadier.exceptions.CommandSyntaxException{if(entity instanceof net.minecraft.world.entity.animal.Animal animal)return animal;throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Living farm animal required")).create();}
    private static LivingEntity living(Entity entity)throws com.mojang.brigadier.exceptions.CommandSyntaxException{if(entity instanceof LivingEntity living&&SurvivalApi.isHumanoid(living))return living;throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Living humanoid required")).create();}
    private static int reply(CommandSourceStack source,SurvivalApi.ActionResult result){if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;}
    private static int json(CommandSourceStack source,JsonObject json){source.sendSuccess(()->Component.literal(json.toString()),false);return 1;}
}
