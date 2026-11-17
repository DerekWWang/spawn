package dev.qube.spawn_9b5fbb9.wildlife;

import dev.qube.spawn_9b5fbb9.environment.EnvironmentApi;
import dev.qube.spawn_9b5fbb9.environment.WorldEnvironment;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Wolves, bears and lions as animals that have to eat. They hunt when the last meal has worn off, take the
 * easiest prey in reach, keep away from fire and from people in company, and break off when badly hurt.
 */
public final class Predators {
    public static final long DAY=24000;
    private static final String FED="spawnFedAt",ROUTED="spawnRoutedUntil";
    private static final Identifier CHASE=Identifier.fromNamespaceAndPath("spawn_9b5fbb9","chase"),BULK=Identifier.fromNamespaceAndPath("spawn_9b5fbb9","bulk");
    /** When a species will go for a person at all. */
    public enum Boldness {
        /** Only a starving pack, or a hungry one that finds someone hurt. */ WEAK_OR_STARVING,
        /** Only when starving. */ STARVING,
        /** Whenever the last meal has worn off. */ HUNGRY
    }
    /**
     * @param pack own kind needed within sixteen blocks before going for a person or for herd animals
     * @param satedDays days one full meal lasts
     * @param mealItems cuts of meat (0.3 kg each) that make a full meal when scavenged
     * @param chase movement speed in pursuit: faster than a person can sprint
     * @param gait walking attribute times the attack goal's own multiplier for animals whose goals are not ours (0 for ours)
     * @param rout share of health below which the animal gives up and runs
     * @param nocturnal hunts between dusk and dawn unless starving
     * @param dens lies up while it freezes and does not hunt
     * @param surprise distance in blocks inside which a person startles it into attacking whatever its hunger
     * @param crowd people standing together that it will not approach
     * @param prey animals it takes alone
     * @param herd larger animals it takes only as a pack
     */
    public record Species(String id,int pack,double satedDays,int mealItems,double chase,double gait,double rout,boolean nocturnal,boolean dens,double surprise,int crowd,Boldness people,Set<String> prey,Set<String> herd){}
    private static final Set<String> SMALL=Set.of("minecraft:sheep","minecraft:goat","minecraft:pig","minecraft:chicken","minecraft:rabbit");
    private static final Set<String> LARGE=Set.of("minecraft:cow","minecraft:horse","minecraft:donkey","minecraft:mule","minecraft:llama","minecraft:camel");
    private static final Set<String> ALL=union(SMALL,LARGE);
    private static final Set<String> MEAT=Set.of("beef","porkchop","mutton","chicken","rabbit","cod","salmon","rotten_flesh");
    public static final Map<String,Species> SPECIES=Map.of(
        // A 40 kg wolf bolts up to 9 kg at a kill and can then go days; people are taken rarely, by packs, when starving or easy.
        "minecraft:wolf",new Species("minecraft:wolf",3,3,10,.40,.30,.5,false,false,0,2,Boldness.WEAK_OR_STARVING,SMALL,LARGE),
        // The one bear that treats a person as prey.
        "minecraft:polar_bear",new Species("minecraft:polar_bear",1,5,40,.42,.3125,.2,false,false,0,4,Boldness.HUNGRY,ALL,Set.of()),
        // A 300 kg omnivore that avoids people, kills stock, dens through the frost and attacks when surprised at close range.
        "spawn_9b5fbb9:brown_bear",new Species("spawn_9b5fbb9:brown_bear",1,2,30,.42,0,.25,false,true,5,3,Boldness.STARVING,union(SMALL,Set.of("minecraft:cow")),Set.of()),
        // A 190 kg cat that gorges on up to 30 kg and rests for days; it hunts by night and takes cattle, horses and lone people.
        "spawn_9b5fbb9:lion",new Species("spawn_9b5fbb9:lion",1,4,40,.45,0,.4,true,false,0,3,Boldness.HUNGRY,ALL,Set.of()));
    private static final Map<Mob,Boolean> ATTACHED=Collections.synchronizedMap(new WeakHashMap<>());
    private Predators(){}
    private static Set<String> union(Set<String> a,Set<String> b){var all=new HashSet<>(a);all.addAll(b);return Set.copyOf(all);}
    public static void init(){
        NeoForge.EVENT_BUS.addListener(Predators::join);NeoForge.EVENT_BUS.addListener(Predators::tick);
        NeoForge.EVENT_BUS.addListener(Predators::hurt);NeoForge.EVENT_BUS.addListener(Predators::kill);NeoForge.EVENT_BUS.addListener(Predators::carcass);
    }
    public static Species species(Entity entity){return SPECIES.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());}
    /** Teeth and claws: its wounds bleed and fester. */
    public static boolean predator(LivingEntity entity){return species(entity)!=null;}
    private static boolean tame(Mob mob){return mob instanceof TamableAnimal pet&&pet.isTame();}
    private static String key(Entity entity){return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}

    // ---- hunger
    public static long fedAt(Mob mob,Species species,long now){
        var data=mob.getPersistentData();
        // An animal met for the first time is somewhere between full and two meals overdue.
        if(!data.contains(FED))data.putLong(FED,now-(long)(mob.getRandom().nextDouble()*2*species.satedDays()*DAY));
        return data.getLongOr(FED,now);
    }
    /** Zero just after a full meal, one when the meal has worn off, two when starving. */
    public static double appetite(Mob mob,Species species,long now){return Math.max(0,(now-fedAt(mob,species,now))/(species.satedDays()*DAY));}
    /** Eat a share of a full meal; hunger older than two meals is not carried. */
    public static void feed(Mob mob,Species species,long now,double share){
        long meal=(long)(species.satedDays()*DAY),fed=Math.max(fedAt(mob,species,now),now-2*meal);
        mob.getPersistentData().putLong(FED,Math.min(now,fed+(long)(Math.min(1,share)*(now-fed))));
    }
    public static void starve(Mob mob,Species species,long now,double meals){mob.getPersistentData().putLong(FED,now-(long)(meals*species.satedDays()*DAY));}

    // ---- what keeps them off
    public static boolean routed(Mob mob){return mob.getPersistentData().getLongOr(ROUTED,0)>mob.level().getGameTime();}
    /** Open flame within reach of the spot: a lit campfire, a burning block or lava. */
    public static boolean fire(ServerLevel level,BlockPos pos,int reach){
        for(BlockPos p:BlockPos.betweenClosed(pos.offset(-reach,-2,-reach),pos.offset(reach,2,reach))){
            if(!level.isLoaded(p))continue;var state=level.getBlockState(p);
            if(state.is(Blocks.FIRE)||state.is(Blocks.LAVA)||state.is(net.minecraft.tags.BlockTags.CAMPFIRES)&&state.getValue(CampfireBlock.LIT))return true;
        }
        return false;
    }
    private static boolean torch(LivingEntity person){return person.getMainHandItem().is(Items.TORCH)||person.getOffhandItem().is(Items.TORCH);}
    /** People standing within eight blocks of one another, the person included. */
    public static int company(ServerLevel level,LivingEntity person){
        return level.getEntitiesOfClass(LivingEntity.class,person.getBoundingBox().inflate(8),e->e.isAlive()&&SurvivalApi.isHumanoid(e)&&!SurvivalApi.exempt(e)).size();
    }
    public static int pack(Mob mob){
        return mob.level().getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(16),e->e.isAlive()&&e.getType()==mob.getType()&&!tame(e)&&!e.isBaby()).size();
    }
    public static boolean deterred(ServerLevel level,LivingEntity person,Species species){
        return company(level,person)>=species.crowd()||torch(person)||fire(level,person.blockPosition(),6);
    }
    private static boolean frost(ServerLevel level,BlockPos pos){
        if(WorldEnvironment.active(level))return EnvironmentApi.at(level,pos).temperature()<0;
        return level.getBiome(pos).value().coldEnoughToSnow(pos,level.getSeaLevel());
    }
    private static boolean wants(Species species,LivingEntity person,boolean hungry,boolean starving){
        return switch(species.people()){
            case HUNGRY->hungry;
            case STARVING->starving;
            case WEAK_OR_STARVING->starving||hungry&&(person.getHealth()<person.getMaxHealth()*.5f||SurvivalApi.state(person).bleeding>0);
        };
    }
    /** The nearest thing this animal would go for now, or nothing. People first: a startled or starving animal ignores the flock. */
    public static LivingEntity choose(PathfinderMob mob,Species species,ServerLevel level){
        if(tame(mob)||mob.isBaby()||routed(mob))return null;
        long now=SurvivalApi.calendar(level);double appetite=appetite(mob,species,now);
        boolean hungry=appetite>=1,starving=appetite>=2,denned=species.dens()&&frost(level,mob.blockPosition());
        boolean abroad=!species.nocturnal()||level.isDarkOutside()||starving;
        double range=mob.getAttributeValue(Attributes.FOLLOW_RANGE);int pack=pack(mob);
        LivingEntity best=null;double nearest=Double.MAX_VALUE;
        for(LivingEntity person:level.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(range),e->e.isAlive()&&SurvivalApi.isHumanoid(e)&&!SurvivalApi.exempt(e)&&mob.canAttack(e))){
            double distance=mob.distanceTo(person);
            boolean startled=species.surprise()>0&&distance<=species.surprise()&&mob.hasLineOfSight(person);
            if(!startled&&(denned||!abroad||pack<species.pack()||!wants(species,person,hungry,starving)||deterred(level,person,species)))continue;
            if(distance<nearest){nearest=distance;best=person;}
        }
        if(best!=null||!hungry||denned||!abroad)return best;
        for(PathfinderMob prey:level.getEntitiesOfClass(PathfinderMob.class,mob.getBoundingBox().inflate(range),e->e.isAlive()&&e!=mob&&mob.canAttack(e))){
            String id=key(prey);
            if(!species.prey().contains(id)&&!(species.herd().contains(id)&&pack>=species.pack())||fire(level,prey.blockPosition(),6))continue;
            double distance=mob.distanceTo(prey);if(distance<nearest){nearest=distance;best=prey;}
        }
        return best;
    }

    // ---- goals
    /** Our own animals: flee, close, bite, wander. */
    public static void goals(PathfinderMob mob,Species species,boolean leaps){
        double walk=mob.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        mob.goalSelector.addGoal(0,new FloatGoal(mob));
        mob.goalSelector.addGoal(1,new RoutGoal(mob,species.chase()/walk));
        if(leaps)mob.goalSelector.addGoal(2,new LeapAtTargetGoal(mob,.4f));
        mob.goalSelector.addGoal(3,new MeleeAttackGoal(mob,species.chase()/walk,true));
        mob.goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(mob,.8));
        mob.goalSelector.addGoal(6,new LookAtPlayerGoal(mob,net.minecraft.world.entity.player.Player.class,8f));
        mob.goalSelector.addGoal(7,new RandomLookAroundGoal(mob));
        mob.targetSelector.addGoal(1,new HurtByTargetGoal(mob));
        mob.targetSelector.addGoal(2,new HuntGoal(mob,species));
    }
    public static final class HuntGoal extends TargetGoal {
        private final Species species;private LivingEntity quarry;
        public HuntGoal(PathfinderMob mob,Species species){super(mob,false);this.species=species;setFlags(EnumSet.of(Flag.TARGET));}
        @Override public boolean canUse(){
            if(mob.getRandom().nextInt(reducedTickDelay(20))!=0||mob.getTarget()!=null&&mob.getTarget().isAlive())return false;
            quarry=choose((PathfinderMob)mob,species,getServerLevel(mob));return quarry!=null;
        }
        @Override public void start(){mob.setTarget(quarry);super.start();}
        @Override public boolean canContinueToUse(){
            LivingEntity target=mob.getTarget();if(target==null||!target.isAlive()||routed(mob))return false;
            double range=mob.getAttributeValue(Attributes.FOLLOW_RANGE)*1.5;if(mob.distanceToSqr(target)>range*range)return false;
            // The quarry that reaches a fire or its companions is let go, unless the animal is already on it.
            if(mob.tickCount%20==0&&SurvivalApi.isHumanoid(target)&&mob.distanceTo(target)>species.surprise()+3&&deterred(getServerLevel(mob),target,species))return false;
            return true;
        }
    }
    public static final class RoutGoal extends Goal {
        private final PathfinderMob mob;private final double speed;private Vec3 refuge;
        public RoutGoal(PathfinderMob mob,double speed){this.mob=mob;this.speed=speed;setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){
            if(!routed(mob))return false;
            var data=mob.getPersistentData();Vec3 threat=new Vec3(data.getDoubleOr("spawnRoutX",mob.getX()),data.getDoubleOr("spawnRoutY",mob.getY()),data.getDoubleOr("spawnRoutZ",mob.getZ()));
            refuge=DefaultRandomPos.getPosAway(mob,16,7,threat);if(refuge==null)refuge=DefaultRandomPos.getPos(mob,16,7);
            return refuge!=null;
        }
        @Override public void start(){mob.getNavigation().moveTo(refuge.x,refuge.y,refuge.z,speed);}
        @Override public boolean canContinueToUse(){return routed(mob)&&!mob.getNavigation().isDone();}
    }

    // ---- events
    private static void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof PathfinderMob mob))return;
        Species species=species(mob);if(species==null||species.gait()==0||ATTACHED.putIfAbsent(mob,true)!=null)return;
        // Animals the game already has keep their own goals and gain the hunger-driven ones.
        mob.goalSelector.addGoal(1,new RoutGoal(mob,species.chase()/species.gait()));
        mob.targetSelector.addGoal(4,new HuntGoal(mob,species));
        // A wolf's own habit of killing every sheep in sight gives way to hunger.
        if(species.id().equals("minecraft:wolf"))mob.targetSelector.removeAllGoals(goal->goal instanceof NonTameRandomTargetGoal);
        if(species.id().equals("minecraft:polar_bear")){
            // 450 kg against the game's 30 health and 6 damage.
            var health=mob.getAttribute(Attributes.MAX_HEALTH);if(health!=null&&!health.hasModifier(BULK)){health.addPermanentModifier(new AttributeModifier(BULK,14,AttributeModifier.Operation.ADD_VALUE));mob.setHealth(mob.getMaxHealth());}
            var bite=mob.getAttribute(Attributes.ATTACK_DAMAGE);if(bite!=null&&!bite.hasModifier(BULK))bite.addPermanentModifier(new AttributeModifier(BULK,4,AttributeModifier.Operation.ADD_VALUE));
        }
    }
    private static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof PathfinderMob mob)||!(mob.level() instanceof ServerLevel level)||mob.tickCount%20!=0||!mob.isAlive())return;
        Species species=species(mob);if(species==null||tame(mob))return;
        boolean routed=routed(mob);
        if(routed){mob.setTarget(null);mob.setLastHurtByMob(null);if(mob instanceof NeutralMob neutral)neutral.stopBeingAngry();}
        if(species.gait()>0){
            // Their own attack goal runs at the game's pace; in pursuit or flight they move as the real animal does.
            var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if(speed!=null){
                speed.removeModifier(CHASE);
                if(mob.getTarget()!=null||routed)speed.addTransientModifier(new AttributeModifier(CHASE,species.chase()/species.gait()-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if(routed||mob.getTarget()!=null||mob.isBaby())return;
        long now=SurvivalApi.calendar(level);if(appetite(mob,species,now)<1)return;
        // A hungry animal comes to meat left lying: a carcass, a bait, an unguarded larder.
        ItemEntity meat=null;double nearest=Double.MAX_VALUE;
        for(ItemEntity drop:level.getEntitiesOfClass(ItemEntity.class,mob.getBoundingBox().inflate(10),e->e.isAlive()&&MEAT.contains(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath()))){
            double distance=mob.distanceToSqr(drop);if(distance<nearest&&!fire(level,drop.blockPosition(),6)){nearest=distance;meat=drop;}
        }
        if(meat==null)return;
        if(nearest>6){mob.getNavigation().moveTo(meat,1);return;}
        int eaten=Math.min(species.mealItems(),meat.getItem().getCount());
        meat.getItem().shrink(eaten);if(meat.getItem().isEmpty())meat.discard();
        feed(mob,species,now,(double)eaten/species.mealItems());
    }
    private static void hurt(LivingDamageEvent.Post event){
        if(!(event.getEntity() instanceof PathfinderMob mob)||mob.level().isClientSide()||event.getHealthDamage()<=0)return;
        Species species=species(mob);if(species==null||tame(mob)||mob.getHealth()>mob.getMaxHealth()*species.rout())return;
        // Badly hurt, a wild animal leaves: a wound that stops it hunting is a death sentence.
        var data=mob.getPersistentData();data.putLong(ROUTED,mob.level().getGameTime()+1200);
        Vec3 threat=event.getSource().getSourcePosition()!=null?event.getSource().getSourcePosition():mob.position();
        data.putDouble("spawnRoutX",threat.x);data.putDouble("spawnRoutY",threat.y);data.putDouble("spawnRoutZ",threat.z);
        mob.setTarget(null);
    }
    private static void kill(LivingDeathEvent event){
        if(!(event.getSource().getEntity() instanceof PathfinderMob killer)||!(killer.level() instanceof ServerLevel level))return;
        Species species=species(killer);if(species==null||tame(killer))return;
        long now=SurvivalApi.calendar(level);
        // The pack feeds together at the kill.
        for(Mob mate:level.getEntitiesOfClass(Mob.class,killer.getBoundingBox().inflate(16),e->e.isAlive()&&e.getType()==killer.getType()&&!tame(e)))mate.getPersistentData().putLong(FED,now);
    }
    private static void carcass(LivingDropsEvent event){
        if(!(event.getSource().getEntity() instanceof PathfinderMob killer)||species(killer)==null||tame(killer)||SurvivalApi.isHumanoid(event.getEntity()))return;
        // What the animal killed it eats: the hide and bones are left.
        event.getDrops().removeIf(drop->drop.getItem().has(DataComponents.FOOD));
    }
}
