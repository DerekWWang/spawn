package dev.qube.spawn_9b5fbb9.industry;

import java.util.*;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Site rules. A works is only a tool; what it can make depends on the plant physically built around
 * it. How a building looks is the builder's business, but a furnace needs its refractory hearth and
 * flue, a machine shop its engine, an electric process a running power station on the end of a cable,
 * and semiconductor work a sealed, filtered, powered cleanroom of sufficient size. */
public final class Facility {
    private Facility(){}
    private static Block plant(String id){return IndustrialContent.PLANT_BLOCKS.get(id).get();}
    private static int count(ServerLevel level,BlockPos centre,Block block,int radius){
        int found=0;for(BlockPos pos:BlockPos.betweenClosed(centre.offset(-radius,-radius,-radius),centre.offset(radius,radius,radius)))if(level.getBlockState(pos).is(block))found++;return found;
    }
    private static BlockPos nearest(ServerLevel level,BlockPos centre,Block block,int radius){
        BlockPos best=null;double distance=Double.MAX_VALUE;
        for(BlockPos pos:BlockPos.betweenClosed(centre.offset(-radius,-radius,-radius),centre.offset(radius,radius,radius)))if(level.getBlockState(pos).is(block)&&pos.distSqr(centre)<distance){distance=pos.distSqr(centre);best=pos.immutable();}
        return best;
    }
    /** Nothing with a collision shape in this cell or the 64 above it. Scanned directly: the light engine lags behind building. */
    public static boolean open(ServerLevel level,BlockPos cell){
        var cursor=cell.mutable();
        for(int rise=0;rise<=64&&cursor.getY()<=level.getMaxY();rise++,cursor.move(Direction.UP))if(!level.getBlockState(cursor).getCollisionShape(level,cursor).isEmpty())return false;
        return true;
    }
    public static boolean roofed(ServerLevel level,BlockPos pos){return !open(level,pos.above());}
    public static boolean water(ServerLevel level,BlockPos centre,int radius){
        for(BlockPos pos:BlockPos.betweenClosed(centre.offset(-radius,-radius,-radius),centre.offset(radius,radius,radius)))if(level.getFluidState(pos).is(FluidTags.WATER))return true;return false;
    }
    /** Height of the tallest chimney stack standing within three blocks whose top is open to the sky. */
    public static int chimney(ServerLevel level,BlockPos centre){
        Block chimney=plant("chimney");int tallest=0;
        for(BlockPos pos:BlockPos.betweenClosed(centre.offset(-3,-3,-3),centre.offset(3,3,3))){
            if(!level.getBlockState(pos).is(chimney)||level.getBlockState(pos.below()).is(chimney))continue;
            int height=1;BlockPos top=pos.immutable();while(height<64&&level.getBlockState(top.above()).is(chimney)){top=top.above();height++;}
            if(open(level,top.above()))tallest=Math.max(tallest,height);
        }
        return tallest;
    }
    /** A stationary steam engine within six blocks, with its boiler, a flue for the boiler and feed water. */
    public static String engine(ServerLevel level,BlockPos centre){
        BlockPos engine=nearest(level,centre,plant("stationary_engine"),6);if(engine==null)return "a stationary steam engine within six blocks";
        BlockPos boiler=nearest(level,engine,plant("steam_boiler"),3);if(boiler==null)return "a steam boiler within three blocks of the engine";
        if(chimney(level,boiler)<4)return "a chimney stack at least 4 high, open to the sky, within three blocks of the boiler";
        if(!water(level,boiler,6))return "feed water within six blocks of the boiler";
        return "";
    }
    // ---- Electricity ------------------------------------------------------------------------------------
    public static final int GENERATOR_KW=35,CABLE_LIMIT=1600,CABLE_LOSS_RUN=5000;
    /** A power station on the end of a cable run, and the length of that run in blocks. */
    public record Feed(WorksEntity station,int length){}
    /** Follows cable ducts from a consumer to the nearest power station switchboard, at most a mile of cable. */
    public static Feed feed(ServerLevel level,BlockPos consumer){
        Block cable=plant("power_cable");var queue=new ArrayDeque<BlockPos>();var depth=new HashMap<BlockPos,Integer>();queue.add(consumer);depth.put(consumer,0);
        while(!queue.isEmpty()&&depth.size()<12000){
            BlockPos at=queue.poll();int length=depth.get(at);if(length>=CABLE_LIMIT)continue;
            for(Direction direction:Direction.values()){
                BlockPos next=at.relative(direction);if(depth.containsKey(next)||!level.hasChunkAt(next))continue;
                BlockState state=level.getBlockState(next);
                if(state.is(cable)){depth.put(next,length+1);queue.add(next);}
                else if(level.getBlockEntity(next) instanceof WorksEntity works&&works.station().equals("power_station"))return new Feed(works,length);
            }
        }
        return null;
    }
    public static WorksEntity station(ServerLevel level,BlockPos consumer){Feed feed=feed(level,consumer);return feed==null?null:feed.station();}
    /** Generating sets: a dynamo with a fifty-horsepower stationary engine to turn it, both within three blocks of the switchboard. */
    public static int generators(ServerLevel level,WorksEntity station){return Math.min(count(level,station.getBlockPos(),plant("generator"),3),count(level,station.getBlockPos(),plant("stationary_engine"),3));}
    /** Why a power station cannot run; empty when it can. */
    public static String stationFault(ServerLevel level,WorksEntity station){
        BlockPos pos=station.getBlockPos();
        if(count(level,pos,plant("generator"),3)==0)return "the power station has no generator within three blocks";
        if(generators(level,station)==0)return "each generator needs a stationary engine to turn it, within three blocks of the switchboard";
        BlockPos boiler=nearest(level,pos,plant("steam_boiler"),4);if(boiler==null)return "the power station has no steam boiler within four blocks";
        if(chimney(level,boiler)<6)return "the power station boiler needs a chimney stack at least 6 high, open to the sky";
        if(!water(level,boiler,6))return "the power station boiler has no feed water within six blocks";
        if(station.fuel<=0)return "the power station has no fuel";
        return "";
    }
    /** Draws one second of load in kilowatts. Each generating set gives 35 kW, what a fifty-horsepower engine can turn; low-voltage direct current loses about two percent
     * in every hundred metres of feeder, so a station serves the mile around it and no more. Early steam plant burns 2.5 kg of coal to the kilowatt-hour, which is
     * 16 fuel ticks a second per kilowatt. {@code share} scales the energy (not the capacity) to the work done this second. */
    public static boolean draw(ServerLevel level,BlockPos consumer,int kilowatts,double share){
        Feed feed=feed(level,consumer);if(feed==null||!stationFault(level,feed.station()).isEmpty())return false;
        WorksEntity station=feed.station();int demand=(int)Math.ceil(kilowatts*(1+feed.length()/(double)CABLE_LOSS_RUN));
        long stamp=level.getGameTime()/20;if(station.loadStamp!=stamp){station.loadStamp=stamp;station.loadUsed=0;}
        long burn=Math.max(1,Math.round(16d*demand*Math.clamp(share,0d,10d)));
        if(station.loadUsed+demand>GENERATOR_KW*generators(level,station)||station.fuel<burn)return false;
        station.loadUsed+=demand;station.fuel-=burn;station.setChanged();return true;
    }
    public static boolean draw(ServerLevel level,BlockPos consumer,int kilowatts){return draw(level,consumer,kilowatts,1);}
    /** Roofed working floor joined to the works: cells at its level with solid ground beneath, standing room or plant on
     * them, and cover overhead. Machines stand on floor too. Counting stops once {@code enough} is found. */
    public static int floor(ServerLevel level,BlockPos origin,int enough){
        var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();seen.add(origin);queue.add(origin);int area=0;
        while(!queue.isEmpty()&&area<enough){
            BlockPos at=queue.poll();
            if(!at.equals(origin)){
                if(Math.abs(at.getX()-origin.getX())>12||Math.abs(at.getZ()-origin.getZ())>12||!level.hasChunkAt(at))continue;
                BlockState state=level.getBlockState(at);boolean plant=state.getBlock() instanceof WorksBlock||IndustrialContent.PLANT_BLOCKS.values().stream().anyMatch(holder->holder.get()==state.getBlock());
                if(!plant&&(!state.getCollisionShape(level,at).isEmpty()||!level.getBlockState(at.above()).getCollisionShape(level,at.above()).isEmpty()))continue;
                if(!level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP)||!roofed(level,plant?at:at.above()))continue;
            }else if(!roofed(level,origin))continue;
            area++;
            for(Direction direction:Direction.Plane.HORIZONTAL){BlockPos next=at.relative(direction);if(seen.add(next))queue.add(next);}
        }
        return area;
    }
    // ---- Cleanroom --------------------------------------------------------------------------------------
    public record Cleanroom(List<String> faults,boolean doorOpen,int unsuited,int floorArea,int filters){}
    private static boolean interior(ServerLevel level,BlockPos pos,BlockState state){
        if(state.isAir()||state.getCollisionShape(level,pos).isEmpty()&&!(state.getBlock() instanceof DoorBlock))return true;
        Block block=state.getBlock();if(block instanceof WorksBlock)return true;
        for(var entry:IndustrialContent.PLANT_BLOCKS.entrySet())if(entry.getValue().get()==block)return !entry.getKey().equals("cleanroom_panel")&&!entry.getKey().equals("filter_unit")&&!entry.getKey().equals("power_cable");
        return false;
    }
    /** Surveys the room a works stands in: sealed shell of clean materials, size, ceiling filter coverage, airlock and occupants. */
    public static Cleanroom cleanroom(ServerLevel level,BlockPos origin){
        var faults=new ArrayList<String>();var inside=new HashSet<BlockPos>();var shell=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();
        inside.add(origin);queue.add(origin);boolean leak=false;
        while(!queue.isEmpty()&&!leak){
            BlockPos at=queue.poll();
            for(Direction direction:Direction.values()){
                BlockPos next=at.relative(direction);if(inside.contains(next))continue;
                if(!interior(level,next,level.getBlockState(next))){shell.add(next);continue;}
                if(Math.abs(next.getX()-origin.getX())>14||Math.abs(next.getZ()-origin.getZ())>14||Math.abs(next.getY()-origin.getY())>7||inside.size()>2400){leak=true;break;}
                inside.add(next);queue.add(next);
            }
        }
        if(leak){faults.add("the room is not sealed (or is larger than 15 x 15 x 7)");return new Cleanroom(faults,false,0,0,0);}
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(BlockPos pos:inside){minX=Math.min(minX,pos.getX());maxX=Math.max(maxX,pos.getX());minY=Math.min(minY,pos.getY());maxY=Math.max(maxY,pos.getY());minZ=Math.min(minZ,pos.getZ());maxZ=Math.max(maxZ,pos.getZ());}
        int dx=maxX-minX+1,dy=maxY-minY+1,dz=maxZ-minZ+1,area=dx*dz;
        if(dx<5||dz<5||dy<3)faults.add("the clean space must be at least 5 x 5 and 3 high inside (it is "+dx+" x "+dz+" x "+dy+")");
        Block panel=plant("cleanroom_panel"),filter=plant("filter_unit");int filters=0,doors=0,dirty=0;boolean open=false;String example="";
        for(BlockPos pos:shell){
            BlockState state=level.getBlockState(pos);
            if(state.is(filter)){if(pos.getY()>maxY)filters++;continue;}
            // Cable ducts pass through the shell as sealed penetrations.
            if(state.is(panel)||state.is(BlockTags.IMPERMEABLE)||state.is(plant("power_cable")))continue;
            if(state.is(Blocks.IRON_DOOR)){doors++;open|=state.getValue(DoorBlock.OPEN);continue;}
            if(dirty++==0)example=state.getBlock().getName().getString()+" at "+pos.toShortString();
        }
        if(dirty>0)faults.add(dirty+" shell blocks shed particles (for example "+example+"); walls, floor and ceiling must be cleanroom panel, glass, filter units or an iron door");
        int needed=(area+3)/4;
        if(filters<needed)faults.add("ceiling filter coverage too low: "+filters+" filter units for "+area+" square blocks of floor (needs "+needed+")");
        if(doors==0)faults.add("no iron door: a cleanroom is entered through a closed airlock door");
        int unsuited=0;var suit=SurvivalContent.item("cleanroom_suit");
        for(LivingEntity person:level.getEntitiesOfClass(LivingEntity.class,new AABB(minX,minY,minZ,maxX+1,maxY+1,maxZ+1),SurvivalApi::isHumanoid))if(!person.getItemBySlot(EquipmentSlot.CHEST).is(suit)&&!SurvivalApi.exempt(person))unsuited++;
        return new Cleanroom(faults,open,unsuited,area,filters);
    }
    // ---- A works' whole site ------------------------------------------------------------------------------
    /** Everything the selected recipe needs from the site that is not there. Cleanliness is tracked by the works itself. */
    public static List<String> unmet(ServerLevel level,WorksEntity works,Industry.Recipe recipe){
        var missing=new ArrayList<String>();BlockPos pos=works.getBlockPos();
        for(String need:recipe.needs()){
            int colon=need.indexOf(':');String kind=colon<0?need:need.substring(0,colon),argument=colon<0?"":need.substring(colon+1);
            switch(kind){
                case "roof"->{if(!roofed(level,pos))missing.add("a roof overhead");}
                case "floor"->{int required=Integer.parseInt(argument),have=floor(level,pos,required);if(have<required)missing.add(required+" square metres of roofed working floor around the works (has "+have+")");}
                case "water"->{if(!water(level,pos,6))missing.add("water within six blocks");}
                case "blast"->{if(!water(level,pos,4)&&nearest(level,pos,plant("stationary_engine"),6)==null)missing.add("blast: a water wheel (water within four blocks) or a stationary engine");}
                case "engine"->{String fault=engine(level,pos);if(!fault.isEmpty())missing.add(fault);}
                case "chimney"->{int height=chimney(level,pos),required=Integer.parseInt(argument);if(height<required)missing.add("a chimney stack at least "+required+" high within three blocks, open to the sky (tallest is "+height+")");}
                case "firebrick","lead_chamber"->{int have=count(level,pos,plant(kind),3),required=Integer.parseInt(argument);if(have<required)missing.add(required+" "+kind.replace('_',' ')+" blocks within three blocks (has "+have+")");}
                case "equip"->{if(nearest(level,pos,plant(argument),5)==null)missing.add("a "+argument.replace('_',' ')+" within five blocks");}
                case "electric"->{
                    WorksEntity station=station(level,pos);
                    if(station==null)missing.add("a cable duct run to a power station switchboard");
                    else{String fault=stationFault(level,station);if(!fault.isEmpty())missing.add(fault);}
                }
                case "cleanroom"->{
                    if(!works.cleanFaults.isEmpty())missing.addAll(works.cleanFaults);
                    else if(works.cleanliness<95)missing.add("cleanliness "+Math.round(works.cleanliness)+"% (lithography needs 95%): keep the door shut, the filters powered and everyone inside suited");
                }
                default->missing.add("unknown site requirement "+need);
            }
        }
        return missing;
    }
}
