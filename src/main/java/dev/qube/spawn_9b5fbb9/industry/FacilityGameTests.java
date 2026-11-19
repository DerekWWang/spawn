package dev.qube.spawn_9b5fbb9.industry;

import java.util.Set;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** The technology tree as a whole, and the physical plant a works needs around it. */
public final class FacilityGameTests {
    private FacilityGameTests(){}
    private static final String M="spawn_9b5fbb9:";
    private static Block plant(String id){return IndustrialContent.PLANT_BLOCKS.get(id).get();}
    private static void fill(GameTestHelper h,int x1,int y1,int z1,int x2,int y2,int z2,Block block){for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)h.setBlock(x,y,z,block);}
    private static String lacks(WorksEntity works){return String.join("; ",works.site);}

    public static void noDeadEnds(GameTestHelper h){
        var reachable=TechTree.reachable(Set.of());
        var stranded=TechTree.products().stream().filter(product->!reachable.contains(product)).toList();
        h.assertTrue(stranded.isEmpty(),"Every product must be obtainable from gathered materials through real stations; stranded: "+stranded);
        for(String station:IndustrialContent.STATIONS)h.assertTrue(TechTree.BASE.contains(station)||reachable.contains(M+station),"The "+station+" itself must be buildable");
        h.succeed();
    }
    public static void eraGates(GameTestHelper h){
        var handAndWater=TechTree.reachable(Set.of("machine_shop"));
        for(String early:new String[]{M+"musket","minecraft:gunpowder","vehicle:cannon","vehicle:trebuchet","vehicle:wagon",M+"pig_iron",M+"steam_engine",M+"sulfuric_acid"})
            h.assertTrue(handAndWater.contains(early),early+" is pre-industrial or early-industrial work and needs no machine shop");
        for(String later:new String[]{M+"steel_rail",M+"precision_parts","vehicle:locomotive","vehicle:field_gun",M+"dynamo",M+"power_station",M+"aluminium_ingot",M+"chip",M+"computer",M+"server"})
            h.assertTrue(!handAndWater.contains(later),later+" cannot exist without machine tools");
        var noIronmaking=TechTree.reachable(Set.of(M+"pig_iron"));
        h.assertTrue(noIronmaking.contains(M+"musket")&&!noIronmaking.contains(M+"steam_engine")&&!noIronmaking.contains(M+"machine_shop")&&!noIronmaking.contains(M+"computer"),"Steam power stands on blast-furnace iron; muskets do not");
        var steamOnly=TechTree.reachable(Set.of("power_station",M+"power_station"));
        for(String steam:new String[]{"vehicle:locomotive",M+"steel_rail","vehicle:field_gun",M+"insulated_wire",M+"dynamo"})h.assertTrue(steamOnly.contains(steam),steam+" belongs to the age of steam and needs no electricity supply");
        for(String electric:new String[]{M+"aluminium_ingot",M+"metallurgical_silicon",M+"phosphorus",M+"vacuum_tube",M+"fine_wire",M+"chip",M+"computer",M+"server"})
            h.assertTrue(!steamOnly.contains(electric),electric+" cannot be made without a power station");
        var noCleanroom=TechTree.reachable(Set.of(M+"filter_unit"));
        h.assertTrue(noCleanroom.contains(M+"vacuum_tube")&&noCleanroom.contains(M+"circuit_board")&&noCleanroom.contains(M+"silicon_wafer")&&!noCleanroom.contains(M+"patterned_wafer")&&!noCleanroom.contains(M+"chip")&&!noCleanroom.contains(M+"computer"),"Valves, boards and bare wafers need no cleanroom; lithography and everything after it does");
        for(String vital:new String[]{M+"fluorite",M+"salt",M+"lens",M+"vacuum_pump",M+"lithography_stepper",M+"aluminium_ingot",M+"lead_chamber",M+"coke","minecraft:glass",M+"bauxite",M+"wolframite",M+"borax",M+"tungsten",M+"silver",M+"rubber",M+"caustic_soda",M+"lubricant"})
            h.assertTrue(!TechTree.reachable(Set.of(vital)).contains(M+"computer"),"No computer without "+vital);
        h.succeed();
    }
    public static void foundryPlant(GameTestHelper h){
        IndustrialGameTests.floor(h);var smith=IndustrialGameTests.worker(h,7,8,Skill.SMITHING,14);var foundry=IndustrialGameTests.works(h,"foundry",8,8);var level=h.getLevel();
        h.assertTrue(Industry.select(smith,foundry,"crucible_steel").success(),"The recipe can be selected before the plant exists");
        foundry.stock.put("minecraft:iron_ingot",30L);foundry.stock.put("minecraft:charcoal",1L);foundry.fuel=8000;Industry.operate(smith,foundry);
        long now=SurvivalApi.calendar(level);foundry.advance(level,now);
        h.assertTrue(!foundry.active&&foundry.stock.get("minecraft:iron_ingot")==30&&lacks(foundry).contains("firebrick")&&lacks(foundry).contains("chimney"),"A bare foundry block melts nothing and consumes nothing: "+lacks(foundry));
        fill(h,7,2,9,9,3,10,plant("firebrick"));fill(h,10,2,8,10,4,8,plant("chimney"));
        foundry.survey(level,Industry.RECIPES.get("crucible_steel"));
        h.assertTrue(lacks(foundry).contains("chimney")&&!lacks(foundry).contains("firebrick"),"Twelve firebricks satisfy the hearth; a three-block stack is too short: "+lacks(foundry));
        h.setBlock(10,5,8,plant("chimney"));h.setBlock(10,6,8,Blocks.STONE);
        foundry.survey(level,Industry.RECIPES.get("crucible_steel"));
        h.assertTrue(lacks(foundry).contains("chimney"),"A capped stack does not draw");
        h.setBlock(10,6,8,Blocks.AIR);foundry.survey(level,Industry.RECIPES.get("crucible_steel"));
        h.assertTrue(foundry.site.isEmpty(),"Hearth and open flue complete the plant: "+lacks(foundry));
        foundry.advance(level,now+20);h.assertTrue(foundry.active,"With its plant the charge is melted");
        foundry.active=false;Industry.select(smith,foundry,"pig_iron_charcoal");
        h.assertTrue(lacks(foundry).contains("24 firebrick")&&lacks(foundry).contains("at least 8")&&lacks(foundry).contains("blast"),"A blast furnace needs a bigger lining, a taller stack and a blast: "+lacks(foundry));
        // A furnace wants a casting floor under cover, not a hearth in a field.
        for(int x=5;x<=11;x++)for(int z=5;z<=11;z++)if(x!=10||z!=8)h.setBlock(x,6,z,Blocks.AIR);
        Industry.select(smith,foundry,"crucible_steel");
        h.assertTrue(lacks(foundry).contains("square metres of roofed working floor"),"Without its shed the foundry has no working floor: "+lacks(foundry));
        Industry.select(smith,foundry,"steel");
        h.assertTrue(lacks(foundry).contains("stationary steam engine"),"Bessemer steel needs a blowing engine: "+lacks(foundry));
        smith.discard();h.succeed();
    }
    public static void machineShopEngine(GameTestHelper h){
        IndustrialGameTests.floor(h);var turner=IndustrialGameTests.worker(h,7,8,Skill.ENGINEERING,10);var shop=IndustrialGameTests.works(h,"machine_shop",8,8);var level=h.getLevel();var recipe=Industry.RECIPES.get("precision_parts");
        Industry.select(turner,shop,"precision_parts");
        h.assertTrue(lacks(shop).contains("stationary steam engine"),"Machine tools need an engine: "+lacks(shop));
        h.setBlock(10,2,8,plant("stationary_engine"));shop.survey(level,recipe);h.assertTrue(lacks(shop).contains("boiler"),"An engine needs a boiler: "+lacks(shop));
        h.setBlock(11,2,8,plant("steam_boiler"));shop.survey(level,recipe);h.assertTrue(lacks(shop).contains("chimney"),"A boiler needs a flue: "+lacks(shop));
        fill(h,12,2,8,12,5,8,plant("chimney"));shop.survey(level,recipe);h.assertTrue(lacks(shop).contains("feed water"),"A boiler needs water: "+lacks(shop));
        h.setBlock(11,1,10,Blocks.WATER);shop.survey(level,recipe);
        h.assertTrue(shop.site.isEmpty(),"Engine, boiler, flue and water complete the power train: "+lacks(shop));
        shop.stock.put(M+"steel_ingot",30L);shop.stock.put(M+"lubricant",1L);Industry.operate(turner,shop);long now=SurvivalApi.calendar(level);shop.advance(level,now);
        h.assertTrue(shop.active&&shop.progress==0,"Without coal in the firebox the lathe does not turn");
        shop.fuel=8000;
        h.runAfterDelay(45,()->{h.assertTrue(shop.progress>0&&shop.fuel<=8000-640,"Fired up, the shop works and its engine burns a hundred kilograms of coal an hour");turner.discard();h.succeed();});
    }
    /** One survey in its own second of power-station load. */
    private static void pump(WorksEntity station,WorksEntity fab,Industry.Recipe recipe){station.loadUsed=0;fab.survey((net.minecraft.server.level.ServerLevel)fab.getLevel(),recipe);}
    public static void cleanroomFab(GameTestHelper h){
        IndustrialGameTests.floor(h);var level=h.getLevel();Block panel=plant("cleanroom_panel"),filter=plant("filter_unit"),cable=plant("power_cable");
        // A 5 x 5 x 3 clean space (x 2..6, y 2..4, z 6..10) in a panel shell with a full filter ceiling and an iron door.
        fill(h,1,1,5,7,5,11,panel);fill(h,2,2,6,6,4,10,Blocks.AIR);fill(h,2,5,6,6,5,10,filter);
        h.setBlock(4,2,5,Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));h.setBlock(4,3,5,Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        var pos=h.absolutePos(new BlockPos(4,2,8));level.setBlock(pos,IndustrialContent.WORKS.get("electronics_works").get().defaultBlockState(),3);var fab=(WorksEntity)level.getBlockEntity(pos);
        h.setBlock(3,2,8,plant("wire_bonder"));
        // Power: an underfloor duct to a switchboard with generator, boiler, flue, feed water and fuel.
        for(int z=3;z<=8;z++)h.setBlock(4,1,z,cable);
        var station=IndustrialGameTests.works(h,"power_station",4,3);h.setBlock(4,1,3,cable);h.setBlock(5,2,3,plant("generator"));h.setBlock(3,2,3,plant("steam_boiler"));fill(h,2,2,3,2,7,3,plant("chimney"));h.setBlock(3,1,1,Blocks.WATER);
        var operator=h.spawn(EntityTypes.VILLAGER,new BlockPos(5,2,8));operator.setNoAi(true);SurvivalApi.enroll(operator);var body=SurvivalApi.state(operator);body.skills.setLevel(Skill.ENGINEERING,14);SurvivalApi.save(operator,body);
        operator.setItemSlot(EquipmentSlot.CHEST,IndustrialGameTests.item("cleanroom_suit",1));
        var recipe=Industry.RECIPES.get("chip");Industry.select(operator,fab,"chip");
        h.assertTrue(lacks(fab).contains("stationary engine"),"A dynamo with nothing to turn it generates nothing: "+lacks(fab));
        h.setBlock(4,2,2,plant("stationary_engine"));fab.survey(level,recipe);
        h.assertTrue(lacks(fab).contains("no fuel"),"A cold power station powers nothing: "+lacks(fab));
        station.fuel=200000;pump(station,fab,recipe);
        h.assertTrue(fab.cleanFaults.isEmpty()&&lacks(fab).contains("cleanliness"),"A sound room still has to be pumped clean before work starts: "+lacks(fab)+" / "+fab.cleanFaults);
        long fuel=station.fuel;for(int i=0;i<25;i++)pump(station,fab,recipe);
        h.assertTrue(fab.cleanliness==100&&fab.site.isEmpty()&&station.fuel<fuel,"Sealed, filtered, powered and suited, the room reaches full cleanliness and the filters burn power-station fuel: "+lacks(fab));
        // Kilowatts: one generator gives 100 kW at the switchboard, and cable length is paid for.
        station.loadUsed=0;h.assertTrue(Facility.draw(level,pos,30)&&!Facility.draw(level,pos,10),"One 35 kW generating set carries 30 kW and refuses 10 more in the same second");
        station.loadUsed=0;long before=station.fuel;Facility.draw(level,pos,20);long burned=before-station.fuel;
        h.assertTrue(Facility.feed(level,pos).length()>=5&&burned>16*20&&burned<=16*21,"Twenty kilowatts down a short duct burn 2.5 kg of coal a kilowatt-hour plus the cable loss: "+burned);
        h.assertTrue(Industry.RECIPES.get("silicon_die").amount()==160&&Industry.RECIPES.get("metallurgical_silicon").load()==400,"Dice per wafer and furnace load are the real figures");
        // Every rule matters.
        operator.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);pump(station,fab,recipe);
        h.assertTrue(fab.cleanliness==85&&lacks(fab).contains("cleanliness"),"One person in street clothes contaminates the room");
        operator.setItemSlot(EquipmentSlot.CHEST,IndustrialGameTests.item("cleanroom_suit",1));for(int i=0;i<4;i++)pump(station,fab,recipe);
        for(int y=2;y<=3;y++){var door=h.absolutePos(new BlockPos(4,y,5));level.setBlock(door,level.getBlockState(door).setValue(DoorBlock.OPEN,true),2);}
        pump(station,fab,recipe);h.assertTrue(fab.cleanliness==85,"An open door lets the dirt in");
        for(int y=2;y<=3;y++){var door=h.absolutePos(new BlockPos(4,y,5));level.setBlock(door,level.getBlockState(door).setValue(DoorBlock.OPEN,false),2);}
        h.setBlock(1,3,8,Blocks.AIR);pump(station,fab,recipe);
        h.assertTrue(fab.cleanliness==0&&lacks(fab).contains("not sealed"),"A hole in the wall ends the cleanroom: "+lacks(fab));
        h.setBlock(1,3,8,Blocks.COBBLESTONE);pump(station,fab,recipe);
        h.assertTrue(lacks(fab).contains("shed particles"),"Ordinary masonry is not a cleanroom surface: "+lacks(fab));
        h.setBlock(1,3,8,Blocks.GLASS);fill(h,2,5,6,6,5,9,panel);pump(station,fab,recipe);
        h.assertTrue(lacks(fab).contains("filter coverage")&&!lacks(fab).contains("shed"),"A window is fine; five filter units cannot clean twenty-five blocks of floor: "+lacks(fab));
        fill(h,2,5,6,6,5,10,filter);fill(h,2,2,10,6,4,10,panel);pump(station,fab,recipe);
        h.assertTrue(lacks(fab).contains("at least 5 x 5"),"A room narrowed to four blocks is too small: "+lacks(fab));
        fill(h,2,2,10,6,4,10,Blocks.AIR);h.setBlock(3,2,8,Blocks.AIR);for(int i=0;i<25;i++)pump(station,fab,recipe);
        h.assertTrue(fab.cleanliness==100&&lacks(fab).contains("wire bonder")&&fab.site.size()==1,"A perfect room without the bonder still cannot package chips: "+lacks(fab));
        h.setBlock(3,2,8,plant("wire_bonder"));pump(station,fab,recipe);h.assertTrue(fab.site.isEmpty(),"Restored, the fab is in order: "+lacks(fab));
        // And then it really makes chips, from real dies, bond wire and packages.
        fab.stock.put(M+"silicon_die",16L);fab.stock.put(M+"fine_wire",256L);fab.stock.put(M+"chip_package",16L);
        h.assertTrue(Industry.operate(operator,fab).success(),"A suited engineer takes the bench");
        fab.advance(level,SurvivalApi.calendar(level));h.assertTrue(fab.active&&fab.stock.isEmpty(),"The batch begins and its materials are committed");
        fab.progress=Industry.ticks(recipe)-6;
        h.runAfterDelay(60,()->{
            h.assertTrue(fab.products.getOrDefault(M+"chip",0L)==16,"Sixteen dies, 256 bond wires and sixteen packages make sixteen chips");
            operator.discard();h.succeed();
        });
    }
}
