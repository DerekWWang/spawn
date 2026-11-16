package dev.qube.spawn_9b5fbb9.industry;

import java.util.*;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.transport.*;
import dev.qube.spawn_9b5fbb9.warfare.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Registry entries for industry, transport and warfare. They share the survival item/block registers
 * so creative listing, lookups and data-driven recipes treat them like every other material. */
public final class IndustrialContent {
    private IndustrialContent(){}
    public static final String MODID=SurvivalContent.MODID;
    public static final List<String> STATIONS=List.of("workshop","assembly_yard","niter_bed","powder_mill","foundry","chemical_works","machine_shop","electronics_works");
    public static final Map<String,DeferredBlock<WorksBlock>> WORKS=new LinkedHashMap<>();
    public static final DeferredBlock<Block> GALENA_ORE=SurvivalContent.BLOCKS.registerSimpleBlock("galena_ore",p->p.strength(3).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> PYRITE_ORE=SurvivalContent.BLOCKS.registerSimpleBlock("pyrite_ore",p->p.strength(3).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> COMPUTER=SurvivalContent.BLOCKS.registerSimpleBlock("computer",p->p.strength(3));
    public static final DeferredBlock<Block> SERVER=SurvivalContent.BLOCKS.registerSimpleBlock("server",p->p.strength(4));
    public static final DeferredBlock<RailTrackBlock> RAIL_TRACK=SurvivalContent.BLOCKS.registerBlock("rail_track",RailTrackBlock::new,p->p.strength(1.5f).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<WorksEntity>> WORKS_ENTITY;
    public static final DeferredRegister.Entities ENTITIES=DeferredRegister.createEntities(MODID);
    public static final DeferredHolder<EntityType<?>,EntityType<VehicleEntity>> VEHICLE=ENTITIES.registerEntityType("vehicle",VehicleEntity::new,MobCategory.MISC,b->b.sized(1.6f,1.2f).clientTrackingRange(10).updateInterval(2));
    public static final DeferredHolder<EntityType<?>,EntityType<ShotEntity>> SHOT=ENTITIES.registerEntityType("shot",ShotEntity::new,MobCategory.MISC,b->b.sized(.4f,.4f).clientTrackingRange(12).updateInterval(1).noSave());
    /** Plain intermediate goods. Every one is made from real inputs by a process or an industrial recipe. */
    public static final List<String> MATERIALS=List.of(
        "galena","pyrite","lead_ingot","steel_ingot","quicklime","wood_ash","niter_earth","saltpeter","sulfur","fermented_mash","fuel_alcohol",
        "coke","coal_tar","sulfuric_acid","phenolic_resin","photoresist","metallurgical_silicon","polysilicon","silicon_boule","silicon_wafer",
        "patterned_wafer","silicon_die","chip_package","chip","copper_wire","fine_wire","circuit_board","solder","transformer","steel_plate",
        "machine_parts_blank","machine_parts","boiler","steam_engine","dynamo","combustion_engine","steel_rail","sleeper","wooden_wheel",
        "vehicle_frame","harness","gun_carriage","musket_stock","musket_barrel_blank","musket_barrel","musket_lock_blank","musket_lock",
        "musket_ball","paper_cartridge","round_shot","stone_shot","powder_charge","explosive_shell","cannon_barrel","bombard_barrel");
    static {
        for(String station:STATIONS){
            var block=SurvivalContent.BLOCKS.registerBlock(station,WorksBlock::new,p->p.strength(3));WORKS.put(station,block);
            SurvivalContent.MATERIALS.put(station,SurvivalContent.ITEMS.registerSimpleBlockItem(block));
        }
        WORKS_ENTITY=SurvivalContent.BLOCK_ENTITIES.register("works",()->new BlockEntityType<>(WorksEntity::new,new HashSet<>(WORKS.values().stream().map(b->(Block)b.get()).toList())));
        for(String material:MATERIALS)SurvivalContent.MATERIALS.put(material,SurvivalContent.ITEMS.registerSimpleItem(material));
        SurvivalContent.MATERIALS.put("musket",SurvivalContent.ITEMS.registerItem("musket",MusketItem::new,p->p.stacksTo(1).durability(240)));
        for(var block:List.of(GALENA_ORE,PYRITE_ORE,COMPUTER,SERVER))SurvivalContent.MATERIALS.put(block.getId().getPath(),SurvivalContent.ITEMS.registerSimpleBlockItem(block));
        // Deployment tokens for administrators and tests; survival vehicles leave an assembly yard as entities.
        for(String kind:Vehicles.SPECS.keySet())SurvivalContent.MATERIALS.put(kind,SurvivalContent.ITEMS.registerItem(kind,p->new VehicleItem(p,kind),p->p.stacksTo(1)));
    }
    public static void register(IEventBus bus){ENTITIES.register(bus);IndustrialGameTests.register(bus);}
    public static void init(){Industry.init();Vehicles.init();Railway.init();Military.init();Firearms.init();Siege.init();}
}
