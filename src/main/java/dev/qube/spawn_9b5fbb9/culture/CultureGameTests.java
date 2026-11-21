package dev.qube.spawn_9b5fbb9.culture;

import dev.qube.spawn_9b5fbb9.content.Production;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.content.WorkstationEntity;
import dev.qube.spawn_9b5fbb9.controllers.WorldActions;
import dev.qube.spawn_9b5fbb9.survival.SkillSet;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.SurvivalState;
import dev.qube.spawn_9b5fbb9.world.NaturalResourceGeneration;
import dev.qube.spawn_9b5fbb9.world.ResourceFields;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

/** Luxury resources, the works made of them, and the score they earn. */
public final class CultureGameTests {
    private CultureGameTests(){}
    private static void work(LivingEntity actor){for(int i=0;i<1000&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}}
    private static WorldGenLevel world(GameTestHelper h,ResourceKey<Biome> biome){
        var level=h.getLevel();var holder=level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome);
        return (WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class[]{WorldGenLevel.class},(proxy,method,args)->method.getName().equals("getSeed")?1L:method.getName().equals("getBiome")?holder:method.invoke(level,args));
    }
    private static LivingEntity actor(GameTestHelper h,int x,SkillSet.Skill skill,int level){
        h.setBlock(x,1,1,Blocks.STONE);var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(x,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);
        var body=SurvivalApi.state(actor);body.skills.setLevel(skill,level);SurvivalApi.save(actor,body);return actor;
    }
    private static WorkstationEntity station(GameTestHelper h,String id,int x){
        var pos=h.absolutePos(new BlockPos(x,1,2));h.getLevel().setBlock(pos,BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(SurvivalContent.MODID,id)).defaultBlockState(),3);return (WorkstationEntity)h.getLevel().getBlockEntity(pos);
    }
    private static net.minecraft.world.item.Item dye(String colour){return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(colour+"_dye"));}
    private static int dropped(GameTestHelper h,LivingEntity near,String item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,near.getBoundingBox().inflate(5),e->e.getItem().is(SurvivalContent.item(item))).stream().mapToInt(e->e.getItem().getCount()).sum();}

    /** The fields give each shore and each climate its own finds, at the rarity intended, and the adapters place them only where they belong. */
    public static void shoreAndHerbs(GameTestHelper h){
        var warm=new EnumMap<ResourceFields.Shore,Integer>(ResourceFields.Shore.class);var cold=new EnumMap<ResourceFields.Shore,Integer>(ResourceFields.Shore.class);int lake=0,bank=0;
        for(int x=0;x<400;x++)for(int z=0;z<400;z++){
            warm.merge(ResourceFields.shore(7,x,z,true,true,true),1,Integer::sum);cold.merge(ResourceFields.shore(7,x,z,true,true,false),1,Integer::sum);
            var fresh=ResourceFields.shore(7,x,z,true,false,true);if(fresh==ResourceFields.Shore.SHELLS)lake++;else h.assertTrue(fresh==ResourceFields.Shore.NONE,"A lake or river shore carries shells and nothing else");
            if(ResourceFields.shore(7,x,z,false,true,true)==ResourceFields.Shore.SHELLS)bank++;
        }
        int shells=warm.getOrDefault(ResourceFields.Shore.SHELLS,0),murex=warm.getOrDefault(ResourceFields.Shore.MUREX,0),pearl=warm.getOrDefault(ResourceFields.Shore.PEARL,0);
        h.assertTrue(shells>2800&&shells<3600&&lake==shells,"Shells lie on about one beach column in fifty, by sea or fresh water: "+shells+", "+lake);
        h.assertTrue(murex>700&&murex<1200&&pearl>70&&pearl<200,"A warm sea gives dye whelks sparingly and pearls rarely: "+murex+", "+pearl);
        h.assertTrue(!cold.containsKey(ResourceFields.Shore.MUREX)&&!cold.containsKey(ResourceFields.Shore.PEARL)&&cold.getOrDefault(ResourceFields.Shore.SHELLS,0)==shells,"A cold sea has shells but neither the whelk nor the pearl oyster");
        h.assertTrue(bank>400&&bank<900,"A muddy bank carries a fifth of the shells a beach does: "+bank);
        var kinds=new EnumMap<ResourceFields.Herb,Integer>(ResourceFields.Herb.class);int patches=0;
        for(int cx=0;cx<60;cx++)for(int cz=0;cz<60;cz++)for(var patch:ResourceFields.herbPatches(7,cx,cz)){kinds.merge(patch.herb(),1,Integer::sum);patches++;}
        h.assertTrue(kinds.size()==ResourceFields.Herb.values().length&&patches>400,"Every dye and medicine plant has stands somewhere: "+kinds);
        int marble=0;for(int q=0;q<400;q++)if(ResourceFields.marble(7,q*128,q*128*3))marble++;
        h.assertTrue(marble>100&&marble<180,"About a third of limestone quarries are marble: "+marble);
        // Habitat: a dry open pad, then the same pad beside water.
        var dry=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(dry.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        var plains=world(h,Biomes.PLAINS);var forest=world(h,Biomes.FOREST);var jungle=world(h,Biomes.JUNGLE);var taiga=world(h,Biomes.TAIGA);var savanna=world(h,Biomes.SAVANNA);
        h.assertTrue(NaturalResourceGeneration.habitat(plains,dry,ResourceFields.Herb.WOAD)&&NaturalResourceGeneration.habitat(plains,dry,ResourceFields.Herb.WELD)&&NaturalResourceGeneration.habitat(plains,dry,ResourceFields.Herb.MADDER),"Open temperate ground carries woad, weld and madder");
        h.assertTrue(!NaturalResourceGeneration.habitat(forest,dry,ResourceFields.Herb.WOAD)&&!NaturalResourceGeneration.habitat(taiga,dry,ResourceFields.Herb.MADDER)&&!NaturalResourceGeneration.habitat(jungle,dry,ResourceFields.Herb.WELD),"Dye plants of open ground do not grow under trees, nor madder in the cold");
        h.assertTrue(NaturalResourceGeneration.habitat(jungle,dry,ResourceFields.Herb.CINCHONA)&&NaturalResourceGeneration.habitat(jungle,dry,ResourceFields.Herb.INDIGO)&&NaturalResourceGeneration.habitat(savanna,dry,ResourceFields.Herb.INDIGO),"Fever bark and indigo are plants of the hot country");
        h.assertTrue(!NaturalResourceGeneration.habitat(plains,dry,ResourceFields.Herb.CINCHONA)&&!NaturalResourceGeneration.habitat(forest,dry,ResourceFields.Herb.INDIGO)&&!NaturalResourceGeneration.habitat(plains,dry,ResourceFields.Herb.MEADOWSWEET)&&!NaturalResourceGeneration.habitat(taiga,dry,ResourceFields.Herb.SPHAGNUM),"Nothing tropical grows in temperate country, and the wetland plants need water");
        h.getLevel().setBlock(dry.below().east(2),Blocks.WATER.defaultBlockState(),3);
        h.assertTrue(NaturalResourceGeneration.habitat(forest,dry,ResourceFields.Herb.MEADOWSWEET)&&NaturalResourceGeneration.habitat(plains,dry,ResourceFields.Herb.MEADOWSWEET)&&NaturalResourceGeneration.habitat(taiga,dry,ResourceFields.Herb.SPHAGNUM),"Meadowsweet grows by temperate water and bog moss by cold water");
        h.assertTrue(!NaturalResourceGeneration.habitat(jungle,dry,ResourceFields.Herb.MEADOWSWEET)&&!NaturalResourceGeneration.habitat(plains,dry,ResourceFields.Herb.WOAD)&&!NaturalResourceGeneration.habitat(savanna,dry,ResourceFields.Herb.SPHAGNUM),"Wet ground is no place for woad, nor hot country for bog moss");
        h.succeed();
    }

    /** Each find is taken by ordinary timed hand-gathering and gives what it says. */
    public static void gatheredLuxuries(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);
        String[][] finds={{"seashells","seashell","3"},{"pearl_oysters","pearl","1"},{"murex_shells","murex","4"},{"wild_cinchona","cinchona_bark","2"},{"wild_sphagnum","sphagnum","3"},{"wild_indigo","indigo_leaves","4"}};
        for(var find:finds){
            var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos.below(),(find[0].startsWith("wild_")?Blocks.DIRT:Blocks.SANDSTONE).defaultBlockState(),3);
            h.getLevel().setBlock(pos,SurvivalContent.FORAGE.get(find[0]).get().defaultBlockState(),3);
            h.assertTrue(h.getLevel().getBlockState(pos).is(SurvivalContent.FORAGE.get(find[0]).get()),find[0]+" must rest on its natural ground");
            int before=dropped(h,actor,find[1]);h.assertTrue(WorldActions.forage(actor,pos).success(),find[0]+" is gathered by hand");work(actor);
            h.assertTrue(dropped(h,actor,find[1])-before==Integer.parseInt(find[2])&&h.getLevel().getBlockState(pos).isAir(),find[0]+" gives "+find[2]+" "+find[1]+" and is used up");
        }
        actor.discard();h.succeed();
    }

    /** Every process names real items in quantities a hand can hold, and each luxury has its route. */
    public static void recipesResolve(GameTestHelper h){
        for(var entry:Production.RECIPES.entrySet()){
            var recipe=entry.getValue();
            for(String id:new String[]{recipe.input(),recipe.output(),recipe.second()}){
                if(id.isEmpty()||id.startsWith("@"))continue;
                h.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)),entry.getKey()+" names an item that does not exist: "+id);
            }
            h.assertTrue(recipe.count()<=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(recipe.input()))).getMaxStackSize(),entry.getKey()+" asks for more than one stack of its input");
        }
        for(String key:new String[]{"red_ochre","alum","woad_vat","indigo_vat","madder_red","weld_yellow","tyrian_purple","dye_yarn_blue","meadowsweet_painkiller","sphagnum_bandage","fever_bark","shell_beads","amber_pendant","pearl_necklace","tapestry","stone_figurine","malachite_beads","lapis_amulet","topaz_ring","painted_pot","polished_marble","marble_column","marble_statue","mosaic"})
            h.assertTrue(Production.RECIPES.containsKey(key),"Missing luxury process: "+key);
        for(String art:Culture.PORTABLE.keySet())h.assertTrue(Production.RECIPES.values().stream().anyMatch(r->r.output().equals(SurvivalContent.MODID+":"+art)&&r.manual()&&r.skill()!=null),art+" must be made by skilled hand work, so that it can be scored");
        for(String stone:Culture.FABRIC.keySet())h.assertTrue(BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath(SurvivalContent.MODID,stone))&&Production.RECIPES.values().stream().anyMatch(r->r.output().equals(SurvivalContent.MODID+":"+stone)),stone+" must be a block with a process that makes it");
        // Both blues, the fast red and the bandage each have more than one way to be made.
        var level=h.getLevel();
        h.assertTrue(WorldActions.recipesMaking(level,dye("blue")).containsAll(java.util.List.of("woad_vat","indigo_vat")),"Woad and indigo are two ways to the same blue: "+WorldActions.recipesMaking(level,dye("blue")));
        h.assertTrue(WorldActions.recipesMaking(level,SurvivalContent.item("bandage")).size()>=2&&WorldActions.recipesMaking(level,SurvivalContent.item("painkiller")).size()>=2,"Bog moss and meadowsweet are second ways to a dressing and a painkiller");
        h.succeed();
    }

    /** A finished work is credited to its maker's society at its worth by skill; ordinary goods are not. */
    public static void artIsScored(GameTestHelper h){
        Production.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"shell_beads\":{\"ticks\":20},\"red_ochre\":{\"ticks\":20}}").getAsJsonObject());
        try{
            var master=actor(h,1,SkillSet.Skill.WEAVING,20);var novice=actor(h,3,SkillSet.Skill.WEAVING,0);var miner=actor(h,5,SkillSet.Skill.MINING,10);
            var looms=new WorkstationEntity[]{station(h,"loom",1),station(h,"loom",3)};var anvil=station(h,"anvil",5);var makers=new LivingEntity[]{master,novice};
            for(int i=0;i<2;i++){
                makers[i].setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("seashell"),8));makers[i].setItemInHand(InteractionHand.OFF_HAND,new ItemStack(SurvivalContent.item("cordage"),1));
                String started=Production.start(makers[i],looms[i],"shell_beads");h.assertTrue(started.startsWith("Started"),"Eight shells and a cord at a loom start a string of beads: "+started);
            }
            miner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("hematite"),1));String ground=Production.start(miner,anvil,"red_ochre");h.assertTrue(ground.startsWith("Started"),"Haematite is ground to ochre at the anvil: "+ground);
            h.runAfterDelay(80,()->{
                try{
                    h.assertTrue(looms[0].output.is(SurvivalContent.item("shell_beads"))&&looms[1].output.is(SurvivalContent.item("shell_beads"))&&anvil.output.is(SurvivalContent.item("red_ochre")),"The work must finish under a tending hand");
                    int best=Culture.score(master).get("culture").getAsInt(),least=Culture.score(novice).get("culture").getAsInt();
                    h.assertTrue(best==5&&least==2,"Beads worth 3 by an average hand are worth half as much again from a master and half from a beginner: "+best+", "+least);
                    h.assertTrue(Culture.score(master).getAsJsonObject("works").getAsJsonObject("kinds").getAsJsonObject("shell_beads").get("count").getAsInt()==1,"The work is listed by kind");
                    h.assertTrue(Culture.score(miner).get("culture").getAsInt()==0,"Ground pigment is a material, not a work of art");
                    int again=Culture.score(master).get("culture").getAsInt();looms[0].advance(h.getLevel(),SurvivalApi.calendar(h.getLevel()));h.assertTrue(Culture.score(master).get("culture").getAsInt()==again,"A finished work is counted once");
                    master.discard();novice.discard();miner.discard();h.succeed();
                }finally{Production.reloadDefinitions(new com.google.gson.JsonObject());}
            });
        }catch(RuntimeException failed){Production.reloadDefinitions(new com.google.gson.JsonObject());throw failed;}
    }

    /** Three handfuls of shells gathered at three places charge the loom together; what is left stays in the pack. */
    public static void handfulsDrawnTogether(GameTestHelper h){
        var worker=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var loom=station(h,"loom",1);worker.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(loom.getBlockPos().above()));
        var pack=worker.getInventory();pack.setItem(pack.getSelectedSlot(),new ItemStack(SurvivalContent.item("seashell"),3));pack.setItem(9,new ItemStack(SurvivalContent.item("seashell"),3));pack.setItem(10,new ItemStack(SurvivalContent.item("seashell"),3));
        pack.setItem(11,new ItemStack(SurvivalContent.item("murex"),5));worker.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(SurvivalContent.item("cordage"),1));
        String started=Production.start(worker,loom,"shell_beads");h.assertTrue(started.startsWith("Started"),"Nine shells in three handfuls are enough for a string of eight: "+started);
        int left=0;for(int slot=0;slot<pack.getContainerSize();slot++)if(pack.getItem(slot).is(SurvivalContent.item("seashell")))left+=pack.getItem(slot).getCount();
        h.assertTrue(left==1&&pack.getItem(11).getCount()==5,"Eight shells go into the work, one stays, and nothing else is touched: "+left);
        var short_=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var other=station(h,"loom",3);short_.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(other.getBlockPos().above()));
        short_.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("seashell"),3));short_.getInventory().setItem(9,new ItemStack(SurvivalContent.item("seashell"),4));short_.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(SurvivalContent.item("cordage"),1));
        h.assertTrue(Production.start(short_,other,"shell_beads").equals("Missing input."),"Seven shells are still not eight");h.succeed();
    }

    /** Worked stone counts only standing together in a dedicated monument, and stops counting when it is pulled down. */
    public static void monument(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(3,2,3));actor.setNoAi(true);SurvivalApi.enroll(actor);var level=h.getLevel();var centre=actor.blockPosition();Culture.forgetNear(level,centre);
        h.assertTrue(!Culture.dedicate(actor).success()&&Culture.score(actor).get("culture").getAsInt()==0,"Bare ground is no monument");
        var statue=h.absolutePos(new BlockPos(1,2,1));level.setBlock(statue,SurvivalContent.MARBLE_STATUE.get().defaultBlockState(),3);
        h.assertTrue(!Culture.dedicate(actor).success(),"A statue with no floor laid about it is not yet a monument");
        for(int x=2;x<=4;x++)for(int z=2;z<=4;z++)level.setBlock(h.absolutePos(new BlockPos(x,1,z)),(x==3?SurvivalContent.MOSAIC:SurvivalContent.POLISHED_MARBLE).get().defaultBlockState(),3);
        var founded=Culture.dedicate(actor);h.assertTrue(founded.success(),"A statue over nine metres of floor is a monument: "+founded.message());
        // 400 for the statue, 9 x 6 for the floor, and two tenths more for three kinds of work together.
        h.assertTrue(Culture.score(actor).get("culture").getAsInt()==545,"The monument is worth its stones and a little more as an ensemble: "+Culture.score(actor));
        var other=h.spawn(EntityTypes.VILLAGER,new BlockPos(4,2,4));other.setNoAi(true);SurvivalApi.enroll(other);
        h.assertTrue(!Culture.dedicate(other).success()&&Culture.score(other).get("culture").getAsInt()==0,"Nobody else can claim a monument that stands");
        for(int i=0;i<4;i++)level.setBlock(h.absolutePos(new BlockPos(1+i,2,5)),SurvivalContent.MARBLE_COLUMN.get().defaultBlockState(),3);
        h.assertTrue(Culture.score(actor).get("culture").getAsInt()==(int)Math.round((400+54+80)*1.3),"What is added to a standing monument counts at the next reckoning: "+Culture.score(actor));
        level.setBlock(statue,Blocks.AIR.defaultBlockState(),3);for(int i=0;i<4;i++)level.setBlock(h.absolutePos(new BlockPos(1+i,2,5)),Blocks.AIR.defaultBlockState(),3);
        var ruined=Culture.score(actor);h.assertTrue(ruined.get("culture").getAsInt()==0&&!ruined.getAsJsonObject("monuments").getAsJsonArray("list").get(0).getAsJsonObject().get("standing").getAsBoolean(),"A monument pulled down counts for nothing: "+ruined);
        Culture.forgetNear(level,centre);actor.discard();other.discard();h.succeed();
    }

    /** Fever bark halves the harm of a serious illness for half a day and cures nothing. */
    public static void remedies(GameTestHelper h){
        var well=new SurvivalState();h.assertTrue(!well.treat("febrifuge"),"Fever bark is not taken by the well");
        var ill=new SurvivalState();ill.severeTicks=40000;var eased=new SurvivalState();eased.severeTicks=40000;
        h.assertTrue(ill.severeSymptoms()&&eased.treat("febrifuge")&&!eased.treat("febrifuge")&&eased.conditions().contains("FEVER RELIEF"),"The sick take one dose at a time");
        double plain=ill.advance(6000,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false).damage(),relieved=eased.advance(6000,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false).damage();
        h.assertTrue(plain>0&&Math.abs(relieved-plain/2)<1e-6&&eased.severeTicks==ill.severeTicks,"The fever does half the harm and runs its full course: "+plain+", "+relieved);
        var restored=SurvivalState.decode(eased.encode());h.assertTrue(restored.feverReliefTicks==eased.feverReliefTicks&&restored.feverReliefTicks==SurvivalState.FEVER_RELIEF_TICKS-6000,"Relief is saved with the body: "+restored.feverReliefTicks);
        eased.advance(6000,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false);h.assertTrue(eased.feverReliefTicks==0&&eased.severeSymptoms()&&eased.treat("febrifuge"),"The dose wears off and the illness is still there");
        h.succeed();
    }

    /** A flower in a bowl dyes nothing fast. Colour comes from the dye plants, the whelk and the mordant. */
    public static void flowersDoNotDye(GameTestHelper h){
        var level=h.getLevel();
        for(var dye:new net.minecraft.world.item.Item[]{dye("red"),dye("yellow"),dye("blue"),dye("pink"),dye("orange"),dye("magenta"),dye("light_gray"),dye("light_blue"),dye("cyan"),dye("white"),dye("gray"),dye("black")}){
            var ways=WorldActions.recipesMaking(level,dye);
            h.assertTrue(ways.stream().noneMatch(id->id.matches("minecraft:[a-z_]+_dye_from_(poppy|tulip|rose_bush|beetroot|dandelion|golden_dandelion|sunflower|wildflowers|cornflower|lily_of_the_valley|pink_tulip|pink_petals|peony|cactus_flower|torchflower|orange_tulip|open_eyeblossom|lilac|allium|white_tulip|oxeye_daisy|azure_bluet|blue_orchid|closed_eyeblossom|pitcher_plant|wither_rose)")),"No flower makes "+BuiltInRegistries.ITEM.getKey(dye)+": "+ways);
        }
        h.assertTrue(WorldActions.recipesMaking(level,dye("red")).contains("madder_red")&&WorldActions.recipesMaking(level,dye("yellow")).contains("weld_yellow")&&WorldActions.recipesMaking(level,dye("purple")).contains("tyrian_purple"),"Madder, weld and the whelk give red, yellow and purple");
        h.assertTrue(WorldActions.recipesMaking(level,dye("purple")).contains("minecraft:purple_dye"),"Purple can still be had by dyeing red over blue");
        h.succeed();
    }
}
