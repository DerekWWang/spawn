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
    public static final List<String> STATIONS=List.of("workshop","assembly_yard","niter_bed","powder_mill","foundry","chemical_works","machine_shop","electronics_works","power_station");
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
        "musket_ball","paper_cartridge","round_shot","stone_shot","powder_charge","explosive_shell","cannon_barrel","bombard_barrel",
        // Second pass: the intermediate goods a real industrial and electrical economy cannot skip.
        "pig_iron","iron_plate","precision_parts","insulated_wire","salt","fluorite","hydrochloric_acid","nitric_acid","hydrofluoric_acid",
        "alumina","aluminium_ingot","phosphorus","methanol","lens","vacuum_tube","photomask","passive_components","electric_motor","display_tube",
        // Third pass: the raw materials and intermediate stages the real routes cannot do without.
        "oil_sand","crude_oil","petrol","lubricant","raw_rubber","rubber","rubber_tyre","bauxite","soda_ash","caustic_soda","cryolite","wolframite","tungsten",
        "silver","borax","boron_oxide","wafer_oxidised","wafer_diffused","wafer_gated","wafer_contacted");
    /** Plant and equipment blocks. They do nothing alone: a works checks that its site contains them (see Facility). */
    public static final List<String> PLANT=List.of("firebrick","chimney","steam_boiler","stationary_engine","lead_chamber","generator","power_cable","arc_furnace",
        "electrolytic_cell","vacuum_pump","cleanroom_panel","filter_unit","water_purifier","crystal_puller","diffusion_furnace","lithography_stepper","wire_bonder","distillation_column");
    public static final Map<String,DeferredBlock<Block>> PLANT_BLOCKS=new LinkedHashMap<>();
    public static final DeferredBlock<Block> ROCK_SALT=SurvivalContent.BLOCKS.registerSimpleBlock("rock_salt",p->p.strength(2.5f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> TAR_SAND=SurvivalContent.BLOCKS.registerSimpleBlock("tar_sand",p->p.strength(2).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> BAUXITE_ORE=SurvivalContent.BLOCKS.registerSimpleBlock("bauxite_ore",p->p.strength(2.5f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> WOLFRAMITE_ORE=SurvivalContent.BLOCKS.registerSimpleBlock("wolframite_ore",p->p.strength(3).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> BORAX_ORE=SurvivalContent.BLOCKS.registerSimpleBlock("borax_ore",p->p.strength(2).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> FLUORITE_ORE=SurvivalContent.BLOCKS.registerSimpleBlock("fluorite_ore",p->p.strength(3).requiresCorrectToolForDrops());
    static {
        for(String station:STATIONS){
            var block=SurvivalContent.BLOCKS.registerBlock(station,WorksBlock::new,p->p.strength(3));WORKS.put(station,block);
            SurvivalContent.MATERIALS.put(station,SurvivalContent.ITEMS.registerSimpleBlockItem(block));
        }
        WORKS_ENTITY=SurvivalContent.BLOCK_ENTITIES.register("works",()->new BlockEntityType<>(WorksEntity::new,new HashSet<>(WORKS.values().stream().map(b->(Block)b.get()).toList())));
        for(String material:MATERIALS)SurvivalContent.MATERIALS.put(material,SurvivalContent.ITEMS.registerSimpleItem(material));
        SurvivalContent.MATERIALS.put("musket",SurvivalContent.ITEMS.registerItem("musket",MusketItem::new,p->p.stacksTo(1).durability(240)));
        for(String plant:PLANT){var block=SurvivalContent.BLOCKS.registerSimpleBlock(plant,q->q.strength(3));PLANT_BLOCKS.put(plant,block);SurvivalContent.MATERIALS.put(plant,SurvivalContent.ITEMS.registerSimpleBlockItem(block));}
        SurvivalContent.MATERIALS.put("cleanroom_suit",SurvivalContent.ITEMS.registerSimpleItem("cleanroom_suit",q->q.stacksTo(1).equippable(net.minecraft.world.entity.EquipmentSlot.CHEST)));
        for(var block:List.of(GALENA_ORE,PYRITE_ORE,COMPUTER,SERVER,ROCK_SALT,FLUORITE_ORE,TAR_SAND,BAUXITE_ORE,WOLFRAMITE_ORE,BORAX_ORE))SurvivalContent.MATERIALS.put(block.getId().getPath(),SurvivalContent.ITEMS.registerSimpleBlockItem(block));
        // Deployment tokens for administrators and tests; survival vehicles leave an assembly yard as entities.
        for(String kind:Vehicles.SPECS.keySet())SurvivalContent.MATERIALS.put(kind,SurvivalContent.ITEMS.registerItem(kind,p->new VehicleItem(p,kind),p->p.stacksTo(1)));
    }
    public static void register(IEventBus bus){ENTITIES.register(bus);IndustrialGameTests.register(bus);}
    public static void init(){Industry.init();Vehicles.init();Railway.init();Military.init();Firearms.init();Siege.init();}
}
