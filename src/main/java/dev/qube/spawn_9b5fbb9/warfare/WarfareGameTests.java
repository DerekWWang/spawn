package dev.qube.spawn_9b5fbb9.warfare;

import java.util.List;
import dev.qube.spawn_9b5fbb9.industry.IndustrialGameTests;
import dev.qube.spawn_9b5fbb9.industry.Ledger;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.transport.Vehicles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class WarfareGameTests {
    private WarfareGameTests(){}
    private static Vec3 at(GameTestHelper h,double x,double y,double z){return h.absoluteVec(new Vec3(x,y,z));}
    private static Player commander(GameTestHelper h,double x,double z){var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(at(h,x,2,z));return player;}
    /** A villager with its own will: needed wherever orders must be carried out on foot. */
    private static Mob soldier(GameTestHelper h,int x,int z){var mob=h.spawn(EntityTypes.VILLAGER,new BlockPos(x,2,z));SurvivalApi.enroll(mob);return mob;}
    private static void hand(LivingEntity actor,ItemStack stack){actor.setItemInHand(InteractionHand.MAIN_HAND,stack);}
    private static Mob pressed(GameTestHelper h,Player commander,int x,int z){
        var mob=soldier(h,x,z);hand(commander,new ItemStack(Items.IRON_SWORD));
        h.assertTrue(Military.conscript(commander,mob).success(),"An armed commander presses an unarmed villager");hand(commander,ItemStack.EMPTY);return mob;
    }
    public static void recruitBounty(GameTestHelper h){
        IndustrialGameTests.floor(h);var commander=commander(h,8.5,8.5);var recruit=soldier(h,9,8);recruit.setNoAi(true);
        h.assertTrue(!Military.recruit(commander,recruit).success()&&!Military.serving(recruit),"Nobody enlists for nothing");
        hand(commander,new ItemStack(Items.BREAD,3));
        h.assertTrue(!Military.recruit(commander,recruit).success()&&commander.getMainHandItem().getCount()==3,"Three rations are not a bounty, and are not taken");
        hand(commander,new ItemStack(Items.BREAD,6));
        h.assertTrue(Military.recruit(commander,recruit).success()&&commander.getMainHandItem().isEmpty(),"A real bounty of food is handed over");
        var record=Military.inspect(recruit);
        h.assertTrue(record.get("kind").getAsString().equals("volunteer")&&record.get("morale").getAsDouble()==70&&record.get("rations").getAsInt()==6,"The volunteer keeps the bounty as rations and starts willing");
        var rival=commander(h,9.5,9.5);hand(rival,new ItemStack(Items.BREAD,8));
        h.assertTrue(!Military.recruit(rival,recruit).success()&&rival.getMainHandItem().getCount()==8,"A serving soldier cannot be enlisted by another commander");
        h.assertTrue(Military.discharge(commander,recruit).success()&&!Military.serving(recruit),"Discharge ends service");
        recruit.discard();h.succeed();
    }
    public static void conscription(GameTestHelper h){
        IndustrialGameTests.floor(h);var commander=commander(h,8.5,8.5);var subject=soldier(h,9,8);subject.setNoAi(true);
        h.assertTrue(!Military.conscript(commander,subject).success(),"An unarmed man presses nobody");
        hand(commander,new ItemStack(Items.IRON_SWORD));hand(subject,new ItemStack(Items.IRON_AXE));
        h.assertTrue(!Military.conscript(commander,subject).success(),"An armed subject resists a lone press-ganger");
        hand(subject,ItemStack.EMPTY);
        h.assertTrue(Military.conscript(commander,subject).success(),"An unarmed subject is pressed at sword point");
        var record=Military.inspect(subject);
        h.assertTrue(record.get("kind").getAsString().equals("conscript")&&record.get("morale").getAsDouble()==35&&record.get("rations").getAsInt()==0,"Conscripts start unwilling and unprovisioned");
        subject.discard();h.succeed();
    }
    public static void equipment(GameTestHelper h){
        IndustrialGameTests.floor(h);var commander=commander(h,8.5,8.5);var soldier=pressed(h,commander,9,8);soldier.setNoAi(true);
        var stranger=soldier(h,10,8);stranger.setNoAi(true);hand(commander,new ItemStack(Items.IRON_SWORD));
        h.assertTrue(!Military.equip(commander,stranger,InteractionHand.MAIN_HAND).success()&&commander.getMainHandItem().is(Items.IRON_SWORD),"Kit is issued only to one's own soldiers");
        h.assertTrue(Military.equip(commander,soldier,InteractionHand.MAIN_HAND).success()&&soldier.getMainHandItem().is(Items.IRON_SWORD)&&commander.getMainHandItem().isEmpty(),"A weapon goes to the soldier's hand and leaves the commander's");
        hand(commander,new ItemStack(Items.SHIELD));Military.equip(commander,soldier,InteractionHand.MAIN_HAND);
        hand(commander,new ItemStack(Items.IRON_HELMET));Military.equip(commander,soldier,InteractionHand.MAIN_HAND);
        hand(commander,new ItemStack(Items.IRON_CHESTPLATE));Military.equip(commander,soldier,InteractionHand.MAIN_HAND);
        h.assertTrue(soldier.getOffhandItem().is(Items.SHIELD)&&soldier.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)&&soldier.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),"Shield and armour go to their proper places");
        hand(commander,new ItemStack(Items.STONE_SWORD));Military.equip(commander,soldier,InteractionHand.MAIN_HAND);
        h.assertTrue(soldier.getMainHandItem().is(Items.STONE_SWORD)&&commander.getInventory().contains(new ItemStack(Items.IRON_SWORD)),"Re-arming returns the old weapon rather than destroying it");
        hand(commander,new ItemStack(Items.ARROW,24));Military.equip(commander,soldier,InteractionHand.MAIN_HAND);
        hand(commander,IndustrialGameTests.item("paper_cartridge",10));Military.equip(commander,soldier,InteractionHand.MAIN_HAND);
        hand(commander,new ItemStack(Items.BREAD,5));Military.equip(commander,soldier,InteractionHand.MAIN_HAND);
        var record=Military.inspect(soldier);
        h.assertTrue(record.get("arrows").getAsInt()==24&&record.get("cartridges").getAsInt()==10&&record.get("rations").getAsInt()==5&&commander.getMainHandItem().isEmpty(),"Ammunition and rations go into the pack");
        soldier.discard();stranger.discard();h.succeed();
    }
    public static void attackOrder(GameTestHelper h){
        IndustrialGameTests.floor(h);var commander=commander(h,3.5,8.5);var soldier=pressed(h,commander,4,8);
        var enemy=soldier(h,10,8);enemy.setNoAi(true);
        hand(commander,new ItemStack(Items.IRON_SWORD));Military.equip(commander,soldier,InteractionHand.MAIN_HAND);
        h.assertTrue(!Military.order(commander,List.of(enemy),"attack",null,soldier,0,null).success(),"Orders bind only one's own soldiers");
        h.assertTrue(Military.order(commander,List.of(soldier),"attack",null,enemy,0,null).success()&&Military.order(soldier).equals("attack"),"The soldier accepts the order to engage");
        float full=enemy.getHealth();
        h.succeedWhen(()->{
            h.assertTrue(enemy.getHealth()<full||!enemy.isAlive(),"The soldier must close with the enemy and wound it with the weapon issued");
            h.assertTrue(SurvivalApi.state(soldier).skills.experience(Skill.MELEE)>0,"Fighting is melee practice");
        });
    }
    public static void moveFormation(GameTestHelper h){
        IndustrialGameTests.floor(h);var commander=commander(h,3.5,3.5);commander.setYRot(0);
        var first=pressed(h,commander,3,5);var second=pressed(h,commander,5,5);Vec3 post=at(h,8.5,2,11.5);
        h.assertTrue(Military.order(commander,List.of(first,second),"move",post,null,0,"line").success(),"Two soldiers are ordered into line");
        h.succeedWhen(()->{
            h.assertTrue(first.position().distanceTo(post)<3&&second.position().distanceTo(post)<3,"Both march to the ordered position");
            h.assertTrue(first.position().distanceTo(second.position())>.6,"Each holds his own place in the rank");
            h.assertTrue(Military.order(first).equals("hold")&&Military.order(second).equals("hold"),"On arrival they hold");
        });
    }
    public static void musket(GameTestHelper h){
        IndustrialGameTests.floor(h);var level=h.getLevel();var shooter=soldier(h,3,8);shooter.setNoAi(true);var target=soldier(h,9,8);target.setNoAi(true);
        ItemStack musket=IndustrialGameTests.item("musket",1);hand(shooter,musket);
        Vec3 aim=target.getEyePosition().subtract(0,.4,0).subtract(shooter.getEyePosition()).normalize();
        h.assertTrue(!Firearms.fire(shooter,musket,level,aim).fired()&&target.getHealth()==target.getMaxHealth(),"An unloaded musket does nothing");
        var body=SurvivalApi.state(shooter);body.skills.setLevel(Skill.MARKSMANSHIP,20);SurvivalApi.save(shooter,body);
        h.assertTrue(Firearms.reloadRate(shooter)>Firearms.reloadRate(target)&&Firearms.RELOAD_WORK/Firearms.reloadRate(target)>=390,"A raw recruit needs about twenty seconds to load; drill shortens it");
        boolean hit=false;int shots=0,misfires=0;
        for(;shots<40&&!hit;shots++){
            Firearms.setLoaded(musket,true);var result=Firearms.fire(shooter,musket,level,aim);
            h.assertTrue(!Firearms.loaded(musket),"Firing or misfiring always spends the charge");
            if(!result.fired())misfires++;hit=result.hit()==target;
        }
        h.assertTrue(hit&&(!target.isAlive()||target.getHealth()<=target.getMaxHealth()-6),"A drilled shot at six blocks hits and wounds badly: "+shots+" shots, "+misfires+" misfires");
        h.assertTrue(musket.getDamageValue()>0&&Firearms.damageAt(10)>Firearms.damageAt(100),"The piece wears, and the ball loses force with range");
        h.assertTrue(Firearms.fouling(musket)==shots&&Firearms.loadWork(musket)>Firearms.RELOAD_WORK,"Every round fouls the barrel and the next rams harder");
        Firearms.clean(musket);h.assertTrue(Firearms.fouling(musket)==0&&Firearms.loadWork(musket)==Firearms.RELOAD_WORK,"A scoured barrel loads at drill speed");
        for(int i=shots;i<Firearms.FLINT_LIFE;i++){Firearms.setLoaded(musket,true);Firearms.fire(shooter,musket,level,aim.scale(-1));}
        Firearms.setLoaded(musket,true);var dull=Firearms.fire(shooter,musket,level,aim.scale(-1));
        h.assertTrue(!dull.fired()&&dull.message().contains("flint")&&Firearms.loaded(musket),"After forty sparks the flint is done and the charge stays in the barrel");
        Firearms.reflint(musket);h.assertTrue(!Firearms.flintWorn(musket),"A new flint puts the lock back in order");
        shooter.discard();target.discard();h.succeed();
    }
    public static void moraleRout(GameTestHelper h){
        IndustrialGameTests.floor(h);var commander=commander(h,8.5,8.5);var conscript=pressed(h,commander,9,8);
        h.assertTrue(!Military.inspect(conscript).get("routing").getAsBoolean(),"An unhurt conscript stands");
        conscript.hurtServer(h.getLevel(),h.getLevel().damageSources().generic(),10);
        var record=Military.inspect(conscript);
        h.assertTrue(record.get("morale").getAsDouble()<20&&record.get("routing").getAsBoolean(),"A badly wounded conscript's nerve breaks and he runs");
        Military.shock(h.getLevel(),conscript.position(),6,5);
        h.assertTrue(Military.morale(conscript)<record.get("morale").getAsDouble(),"Shot landing nearby shakes morale further");
        conscript.discard();h.succeed();
    }
    public static void archerVolley(GameTestHelper h){
        IndustrialGameTests.floor(h);var commander=commander(h,2.5,8.5);var archer=pressed(h,commander,3,8);var enemy=soldier(h,11,8);enemy.setNoAi(true);
        hand(commander,new ItemStack(Items.BOW));Military.equip(commander,archer,InteractionHand.MAIN_HAND);
        h.assertTrue(Military.order(commander,List.of(archer),"fire",null,enemy,0,null).success(),"The archer is ordered to shoot");
        h.runAfterDelay(60,()->{
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,archer.getBoundingBox().inflate(16)).isEmpty()&&enemy.getHealth()==enemy.getMaxHealth(),"A bow without arrows shoots nothing");
            h.assertTrue(archer.position().distanceTo(at(h,3.5,2,8.5))<2,"Ordered to fire, an archer without arrows does not charge");
            hand(commander,new ItemStack(Items.ARROW,12));Military.equip(commander,archer,InteractionHand.MAIN_HAND);
            h.succeedWhen(()->h.assertTrue(Military.inspect(archer).get("arrows").getAsInt()<12,"Each shot spends a real arrow from the quiver"));
        });
    }
    public static void mountedSoldier(GameTestHelper h){
        IndustrialGameTests.floor(h);var commander=commander(h,3.5,3.5);var rider=pressed(h,commander,4,5);
        var horse=h.spawn(EntityTypes.HORSE,new BlockPos(6,2,5));horse.setAge(0);
        h.assertTrue(Military.order(commander,List.of(rider),"mount",null,horse,0,null).success(),"A soldier can be ordered to mount");
        h.runAfterDelay(60,()->{
            h.assertTrue(rider.getVehicle()!=horse&&Military.order(rider).equals("hold"),"An untamed, unsaddled horse cannot be ridden to war");
            horse.setTamed(true);horse.setItemSlot(EquipmentSlot.SADDLE,new ItemStack(Items.SADDLE));
            Military.order(commander,List.of(rider),"mount",null,horse,0,null);
            h.runAfterDelay(40,()->{
                h.assertTrue(rider.getVehicle()==horse,"With a tamed, saddled horse the soldier mounts");
                Vec3 post=at(h,12.5,2,12.5);double start=horse.position().distanceTo(post);
                Military.order(commander,List.of(rider),"move",post,null,0,"line");
                h.succeedWhen(()->h.assertTrue(rider.getVehicle()==horse&&horse.position().distanceTo(post)<start-4,"The mounted soldier rides his horse towards the ordered position"));
            });
        });
    }
    public static void cannonMasonryAndEarth(GameTestHelper h){
        IndustrialGameTests.floor(h);var level=h.getLevel();
        for(int y=2;y<=4;y++)for(int z=10;z<=12;z++){h.setBlock(11,y,z,Blocks.COBBLESTONE);h.setBlock(12,y,z,Blocks.COBBLESTONE);}
        for(int y=2;y<=4;y++)for(int z=3;z<=5;z++){h.setBlock(11,y,z,Blocks.DIRT);h.setBlock(12,y,z,Blocks.DIRT);}
        var gun=Vehicles.spawn(level,"cannon",at(h,3.5,2,8.5),-90);var gunner=IndustrialGameTests.worker(h,3,6,Skill.GUNNERY,20);
        BlockPos stone=h.absolutePos(new BlockPos(11,3,11)),behind=h.absolutePos(new BlockPos(12,3,11)),earth=h.absolutePos(new BlockPos(11,3,4));
        h.assertTrue(!Siege.fire(gunner,gun,Vec3.atCenterOf(stone)).success(),"An unloaded cannon cannot fire");
        gun.shot="spawn_9b5fbb9:round_shot";gun.charges=1;gun.ready=true;
        h.assertTrue(Siege.fire(gunner,gun,Vec3.atCenterOf(stone)).success()&&!gun.ready&&gun.shot.isEmpty(),"Firing spends the loaded round");
        h.runAfterDelay(20,()->{
            h.assertTrue(level.getBlockState(stone).isAir(),"Round shot smashes the masonry block it strikes");
            h.assertTrue(level.getBlockState(behind).is(Blocks.COBBLESTONE)&&Siege.battered(level,behind)>0,"The block behind is battered but stands: breaching is cumulative");
            gun.shot="spawn_9b5fbb9:round_shot";gun.charges=1;gun.ready=true;
            h.assertTrue(Siege.fire(gunner,gun,Vec3.atCenterOf(earth)).success(),"The piece is relaid on the earth bank");
            h.runAfterDelay(20,()->{
                h.assertTrue(level.getBlockState(earth).is(Blocks.DIRT)&&Siege.battered(level,earth)>0,"An earth rampart absorbs the same shot that broke stone");
                h.assertTrue(Siege.resistance(level,earth,level.getBlockState(earth))>Siege.resistance(level,behind,level.getBlockState(behind)),"Earth outlasts masonry under cannon fire");
                h.assertTrue(SurvivalApi.state(gunner).skills.experience(Skill.GUNNERY)>265000,"Serving the piece is gunnery practice");
                gun.discard();gunner.discard();h.succeed();
            });
        });
    }
    public static void crewLoadsFromStore(GameTestHelper h){
        IndustrialGameTests.floor(h);var gun=Vehicles.spawn(h.getLevel(),"cannon",at(h,8.5,2,8.5),0);var gunner=IndustrialGameTests.worker(h,8,6,Skill.GUNNERY,20);
        h.assertTrue(Siege.man(gunner,gun).success(),"A capable humanoid serves the piece");
        h.runAfterDelay(45,()->{
            h.assertTrue(gun.shot.isEmpty()&&gun.loadProgress==0,"A crew with no ammunition in the store loads nothing");
            Ledger.add(gun.cargo,"spawn_9b5fbb9:round_shot",3);
            h.runAfterDelay(45,()->{
                h.assertTrue(gun.shot.isEmpty()&&Ledger.count(gun.cargo,"spawn_9b5fbb9:round_shot")==3,"Shot without powder cannot be loaded");
                Ledger.add(gun.cargo,"spawn_9b5fbb9:powder_charge",2);
                h.runAfterDelay(65,()->{
                    h.assertTrue(gun.shot.equals("spawn_9b5fbb9:round_shot")&&Ledger.count(gun.cargo,"spawn_9b5fbb9:round_shot")==2&&Ledger.count(gun.cargo,"spawn_9b5fbb9:powder_charge")==1,"Loading draws exactly one round and one charge from the store");
                    h.assertTrue(gun.loadProgress>0&&!gun.ready&&gun.loadProgress<gun.spec().artillery().reloadTicks()/4d,"One man serves an eight-man piece slowly: "+gun.loadProgress);
                    gun.discard();gunner.discard();h.succeed();
                });
            });
        });
    }
    public static void trebuchetArc(GameTestHelper h){
        IndustrialGameTests.floor(h);var engine=Vehicles.spawn(h.getLevel(),"trebuchet",at(h,8.5,2,8.5),0);var master=IndustrialGameTests.worker(h,8,5,Skill.GUNNERY,10);
        var art=engine.spec().artillery();
        h.assertTrue(Siege.lay(master,engine,at(h,8.5,2,14.5)).message().contains("back"),"A trebuchet lobs high and cannot hit what is under its nose");
        var cannon=Vehicles.SPECS.get("cannon").artillery();
        double far=Math.toDegrees(Siege.elevation(art,150,0)),flat=Math.toDegrees(Siege.elevation(cannon,60,0));
        h.assertTrue(far>art.minPitch()&&far<art.maxPitch()&&flat<1,"At a hundred and fifty blocks a trebuchet throws in a high arc; at sixty a cannon at 440 m/s fires dead flat: "+far+" vs "+flat);
        h.assertTrue(Math.abs(Siege.heightAt(art,Math.toRadians(far),150))<.05&&Double.isInfinite(Siege.elevation(art,60,0)),"The firing solution lands on the mark");
        double reach=Siege.reach(art),vacuum=art.velocity()*art.velocity()/Siege.GRAVITY;
        h.assertTrue(reach>180&&reach<vacuum&&Double.isNaN(Siege.elevation(art,300,0)),"A 50 m/s release reaches about two hundred metres, less than it would in a vacuum, and no further: "+reach);
        double shot=Siege.range(cannon,Math.toRadians(15)),noAir=cannon.velocity()*cannon.velocity()*Math.sin(Math.toRadians(30))/Siege.GRAVITY;
        h.assertTrue(shot>1200&&shot<3500&&shot<noAir/2,"Air drag holds a six-pounder at full elevation to a couple of kilometres, a fraction of its vacuum range: "+shot+" vs "+noAir);
        h.assertTrue(engine.immobileReason().equals("fixed emplacement")&&art.powderCharges()==0,"It is built where it shoots and needs no powder");
        engine.discard();master.discard();h.succeed();
    }
}
