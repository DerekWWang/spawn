package dev.qube.spawn_9b5fbb9.controllers;

import com.google.gson.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.qube.spawn_9b5fbb9.content.*;
import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;
import static net.minecraft.commands.Commands.*;

/** Observations of actual loaded blocks and real samples; never reads the generation seed. */
public final class ResourceActions {
    private ResourceActions(){}
    private record Observation(BlockPos pos,String identity,String kind,String host,boolean ready,String method){}
    public static void init(){NeoForge.EVENT_BUS.addListener(ResourceActions::commands);}
    private static boolean observer(LivingEntity actor){return actor!=null&&actor.isAlive()&&actor.level() instanceof ServerLevel&&SurvivalApi.isHumanoid(actor)&&!SurvivalApi.state(actor).blind();}
    private static boolean covered(net.minecraft.world.level.Level level,BlockPos beside){return level.hasChunkAt(beside)&&level.getBlockState(beside).isCollisionShapeFullBlock(level,beside);}
    /** Has at least one face that is not hard against a solid block. */
    public static boolean exposed(net.minecraft.world.level.Level level,BlockPos pos){for(Direction face:Direction.values())if(!covered(level,pos.relative(face)))return true;return false;}
    public static boolean visible(LivingEntity actor,BlockPos pos){
        if(!actor.level().hasChunkAt(pos))return false;
        // Walled in on every side it shows no face to anyone, and six looks at its neighbours spare the rays: under
        // a turf there are thousands of such blocks in range of every survey.
        if(!exposed(actor.level(),pos))return false;
        var eye=actor.getEyePosition();
        if(sighted(actor,eye,Vec3.atCenterOf(pos),pos))return true;
        // The heart of a block is hidden behind its neighbours' edges on level ground, where only the few underfoot would
        // ever count as seen. A block is in sight if any face turned towards the eye can be reached through open air.
        for(Direction face:Direction.values()){
            Vec3 out=new Vec3(pos.getX()+.5+face.getStepX()*.51,pos.getY()+.5+face.getStepY()*.51,pos.getZ()+.5+face.getStepZ()*.51);
            if((eye.x-out.x)*face.getStepX()+(eye.y-out.y)*face.getStepY()+(eye.z-out.z)*face.getStepZ()<=0||covered(actor.level(),pos.relative(face)))continue;
            if(sighted(actor,eye,out,null))return true;
        }
        return false;
    }
    /** One ray: true if it reaches the target through open air, or ends on the block it was aimed at. */
    private static boolean sighted(LivingEntity actor,Vec3 eye,Vec3 target,BlockPos aim){
        // Bound every traversed chunk before invoking the world's ray tracer.
        int samples=(int)Math.ceil(eye.distanceTo(target)*2);
        for(int i=0;i<=samples;i++)if(!actor.level().hasChunkAt(BlockPos.containing(eye.lerp(target,samples==0?0:i/(double)samples))))return false;
        var hit=actor.level().clip(new ClipContext(eye,target,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,actor));
        return hit.getType()==HitResult.Type.MISS||aim!=null&&hit.getBlockPos().equals(aim);
    }
    private static Observation observe(LivingEntity actor,BlockPos pos){
        var state=actor.level().getBlockState(pos);var block=state.getBlock();
        if(block instanceof GroundResourceBlock source)return new Observation(pos,source.resource(),"surface","soil or surface debris",source.harvestable(actor),"gather");
        if(block instanceof FruitTreeBlock tree)return new Observation(pos,"apple_tree","fruit tree","living tree",tree.harvestable(actor,pos),"harvest");
        String id=BuiltInRegistries.BLOCK.getKey(block).getPath();
        if(block instanceof CropBlock)return new Observation(pos,id,"crop","cultivated soil",dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.harvestReady((ServerLevel)actor.level(),pos,state),"harvest");
        String mineral=block==SurvivalContent.MALACHITE_ORE.get()?"malachite":block==SurvivalContent.CASSITERITE_ORE.get()?"cassiterite":block==SurvivalContent.HEMATITE_ORE.get()?"hematite":state.is(Blocks.COAL_ORE)||state.is(Blocks.DEEPSLATE_COAL_ORE)?"coal":state.is(Blocks.COPPER_ORE)||state.is(Blocks.DEEPSLATE_COPPER_ORE)?"copper ore":state.is(Blocks.IRON_ORE)||state.is(Blocks.DEEPSLATE_IRON_ORE)?"iron ore":state.is(SurvivalContent.TIN_ORE.get())?"tin ore":switch(id){case "galena_ore"->"galena";case "pyrite_ore"->"pyrite";case "rock_salt"->"rock salt";case "fluorite_ore"->"fluorite";case "bauxite_ore"->"bauxite";case "wolframite_ore"->"wolframite";case "tar_sand"->"oil sand";case "borax_ore"->"borax";default->null;};
        if(mineral!=null)return new Observation(pos,mineral,"mineral",mineral.equals("malachite")?"limestone":mineral.equals("cassiterite")?"granite":mineral.equals("hematite")?"ironstone":"observed host rock",true,"mine");
        if(state.is(SurvivalContent.LIMESTONE.get())||state.is(Blocks.GRANITE)||state.is(Blocks.SANDSTONE)||state.is(Blocks.RED_SANDSTONE)||state.is(Blocks.CLAY)||state.is(Blocks.GRAVEL)||state.is(Blocks.SAND))return new Observation(pos,id,"quarry material",id,true,"mine");
        return null;
    }
    public static JsonObject discover(LivingEntity actor,int radius){
        var result=new JsonObject();var resources=new JsonArray();result.addProperty("schemaVersion",1);result.add("resources",resources);
        if(!observer(actor)||radius<1||radius>16){result.addProperty("error","A sighted living server humanoid and radius 1–16 are required");return result;}
        var candidates=new ArrayList<Observation>();var center=actor.blockPosition();
        for(var mutable:BlockPos.betweenClosed(center.offset(-radius,-Math.min(8,radius),-radius),center.offset(radius,Math.min(8,radius),radius))){
            var pos=mutable.immutable();if(pos.distSqr(center)>radius*radius||!actor.level().hasChunkAt(pos))continue;
            var observation=observe(actor,pos);if(observation!=null)candidates.add(observation);
        }
        // Sight is the dear part, a ray through the world for each block, and a beach has thousands of sand blocks in
        // range. Take the nearest first and stop at the sixty-fifth seen: that is all the report shows, or needs to
        // know it was cut short. The answer is the same as looking at every one.
        candidates.sort(Comparator.comparingDouble(o->o.pos.distSqr(center)));
        var found=new ArrayList<Observation>();
        for(var observation:candidates)if(visible(actor,observation.pos)){found.add(observation);if(found.size()>64)break;}
        result.addProperty("radius",radius);result.addProperty("truncated",found.size()>64);
        for(var observation:found.subList(0,Math.min(64,found.size()))){
            var entry=new JsonObject();entry.addProperty("identity",observation.identity);entry.addProperty("kind",observation.kind);entry.addProperty("host",observation.host);entry.addProperty("x",observation.pos.getX());entry.addProperty("y",observation.pos.getY());entry.addProperty("z",observation.pos.getZ());entry.addProperty("harvestReady",observation.ready);entry.addProperty("method",observation.method);resources.add(entry);
        }
        return result;
    }
    public static SurvivalApi.ActionResult prospect(LivingEntity actor,BlockPos pos){
        if(!observer(actor))return new SurvivalApi.ActionResult(false,"A sighted living server humanoid is required");
        String identity,host;
        if(pos==null){
            var held=actor.getMainHandItem();String id=BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
            if(!held.is(SurvivalContent.item("malachite"))&&!held.is(SurvivalContent.item("cassiterite"))&&!held.is(SurvivalContent.item("hematite")))return new SurvivalApi.ActionResult(false,"Hold an extracted mineral sample, or select a nearby exposed outcrop");
            identity=id;host="sample; original host not established";
        }else{
            if(actor.distanceToSqr(Vec3.atCenterOf(pos))>16||!actor.level().hasChunkAt(pos)||!visible(actor,pos))return new SurvivalApi.ActionResult(false,"Observe a loaded outcrop within four blocks and in line of sight");
            var observed=observe(actor,pos);if(observed==null||!observed.kind.equals("mineral")&&!observed.kind.equals("quarry material"))return new SurvivalApi.ActionResult(false,"This is not an exposed mineral or quarry sample");identity=observed.identity;host=observed.host;
        }
        int skill=SkillApi.level(actor,SkillSet.Skill.MINING);int confidence=Math.min(90,50+skill*2);
        return new SurvivalApi.ActionResult(true,"Observed "+identity+" | Host: "+host+" | Identification confidence "+confidence+"% | Visible material only; buried reserves are unknown.");
    }
    private static void commands(RegisterCommandsEvent event){
        var root=literal("survival");add(root,c->c.getSource().getPlayerOrException());
        var controlled=argument("actor",EntityArgument.entity());add(controlled,c->{var entity=EntityArgument.getEntity(c,"actor");if(entity instanceof LivingEntity living&&SurvivalApi.isHumanoid(living))return living;throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Living humanoid required")).create();});
        root.then(literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(controlled));event.getDispatcher().register(root);
    }
    @FunctionalInterface private interface Lookup{LivingEntity get(CommandContext<CommandSourceStack> context)throws CommandSyntaxException;}
    private static void add(ArgumentBuilder<CommandSourceStack,?> node,Lookup actor){
        node.then(literal("resources").executes(c->report(c.getSource(),discover(actor.get(c),12))).then(argument("radius",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,16)).executes(c->report(c.getSource(),discover(actor.get(c),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"radius"))))));
        node.then(literal("prospect").executes(c->reply(c.getSource(),prospect(actor.get(c),null))).then(argument("pos",BlockPosArgument.blockPos()).executes(c->reply(c.getSource(),prospect(actor.get(c),BlockPosArgument.getLoadedBlockPos(c,"pos"))))));
    }
    private static int report(CommandSourceStack source,JsonObject result){source.sendSuccess(()->Component.literal(result.toString()),false);return result.has("error")?0:1;}
    private static int reply(CommandSourceStack source,SurvivalApi.ActionResult result){if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;}
}
