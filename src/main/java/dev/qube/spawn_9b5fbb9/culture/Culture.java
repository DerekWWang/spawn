package dev.qube.spawn_9b5fbb9.culture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What a society has made that is worth looking at. A portable work (beads, a ring, a painted jar, a tapestry) is counted
 * once, when it is finished, at the hours in it times the rarity of what it is made of, more or less by the maker's skill.
 * Worked stone (polished marble, mosaic, columns, statues) counts only while it stands together in a dedicated monument,
 * and stops counting when the monument is pulled down.
 */
public final class Culture extends SavedData {
    /** Worth of one finished work by an average hand. */
    public static final Map<String,Integer> PORTABLE;
    /** Worth of one block of worked stone standing in a monument. */
    public static final Map<String,Integer> FABRIC;
    public static final int REACH=8,STATUE_OR_COLUMNS=4,FLOOR=9;
    static {
        var portable=new LinkedHashMap<String,Integer>();
        portable.put("shell_beads",3);portable.put("painted_pot",6);portable.put("stone_figurine",8);portable.put("amber_pendant",8);portable.put("malachite_beads",8);
        portable.put("lapis_amulet",20);portable.put("topaz_ring",40);portable.put("pearl_necklace",60);portable.put("tapestry",80);
        PORTABLE=Collections.unmodifiableMap(portable);
        var fabric=new LinkedHashMap<String,Integer>();
        fabric.put("polished_marble",6);fabric.put("mosaic",6);fabric.put("marble_column",20);fabric.put("marble_statue",400);
        FABRIC=Collections.unmodifiableMap(fabric);
    }
    public record Tally(int count,int worth){
        static final Codec<Tally> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("count").forGetter(Tally::count),Codec.INT.fieldOf("worth").forGetter(Tally::worth)).apply(i,Tally::new));
    }
    public record Monument(String society,String dimension,long pos,int worth,int statues,int columns,int floor,long day){
        static final Codec<Monument> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("society").forGetter(Monument::society),Codec.STRING.fieldOf("dimension").forGetter(Monument::dimension),
            Codec.LONG.fieldOf("pos").forGetter(Monument::pos),Codec.INT.fieldOf("worth").forGetter(Monument::worth),Codec.INT.fieldOf("statues").forGetter(Monument::statues),
            Codec.INT.fieldOf("columns").forGetter(Monument::columns),Codec.INT.fieldOf("floor").forGetter(Monument::floor),Codec.LONG.fieldOf("day").forGetter(Monument::day)).apply(i,Monument::new));
    }
    /** What stands within reach of a centre. */
    public record Survey(int statues,int columns,int polished,int mosaic){
        public int floor(){return polished+mosaic;}
        public boolean stands(){return (statues>=1||columns>=STATUE_OR_COLUMNS)&&floor()>=FLOOR;}
        public int kinds(){return (statues>0?1:0)+(columns>0?1:0)+(polished>0?1:0)+(mosaic>0?1:0);}
        /** The stones at their own worth, and a tenth more for each further kind of work: an ensemble is more than its parts. */
        public int worth(){
            if(!stands())return 0;
            int stones=statues*FABRIC.get("marble_statue")+columns*FABRIC.get("marble_column")+polished*FABRIC.get("polished_marble")+mosaic*FABRIC.get("mosaic");
            return (int)Math.round(stones*(1+.1*(kinds()-1)));
        }
    }
    public static final Codec<Culture> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.unboundedMap(Codec.STRING,Codec.unboundedMap(Codec.STRING,Tally.CODEC)).optionalFieldOf("works",Map.of()).forGetter(d->d.works),
        Monument.CODEC.listOf().optionalFieldOf("monuments",List.of()).forGetter(d->d.monuments)).apply(i,Culture::new));
    public static final SavedDataType<Culture> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"culture"),Culture::new,CODEC);
    private final Map<String,Map<String,Tally>> works=new HashMap<>();
    private final List<Monument> monuments=new ArrayList<>();
    public Culture(){}
    private Culture(Map<String,Map<String,Tally>> works,List<Monument> monuments){works.forEach((society,kinds)->this.works.put(society,new LinkedHashMap<>(kinds)));this.monuments.addAll(monuments);}
    public static Culture get(MinecraftServer server){return server.getDataStorage().computeIfAbsent(TYPE);}

    /** The society a person's work is credited to: a player's own, a team's, or a lone actor's. */
    public static String society(LivingEntity actor){
        if(actor instanceof ServerPlayer player)return Society.keyOf(player);
        var team=actor.getTeam();return team!=null?"team:"+team.getName():"actor:"+actor.getStringUUID();
    }
    /** A practised hand makes a better thing: half worth from a beginner, half as much again from a master. */
    public static double quality(double skill){return skill<0?1:Math.clamp(.5+skill/20,.5,1.5);}
    public static boolean art(ItemStack stack){var id=BuiltInRegistries.ITEM.getKey(stack.getItem());return id.getNamespace().equals(SurvivalContent.MODID)&&PORTABLE.containsKey(id.getPath());}
    /** Called once when a station job is finished by a person; anything that is not a portable work of art is ignored. */
    public static void finished(LivingEntity maker,ItemStack output,double skill){
        if(output.isEmpty()||!(maker.level() instanceof ServerLevel level)||!art(output))return;
        String kind=BuiltInRegistries.ITEM.getKey(output.getItem()).getPath();
        int worth=(int)Math.max(1,Math.round(PORTABLE.get(kind)*quality(skill)))*output.getCount();
        var data=get(level.getServer());
        data.works.computeIfAbsent(society(maker),k->new LinkedHashMap<>()).merge(kind,new Tally(output.getCount(),worth),(a,b)->new Tally(a.count+b.count,a.worth+b.worth));
        data.setDirty();
    }
    public static Survey survey(ServerLevel level,BlockPos centre){
        int statues=0,columns=0,polished=0,mosaic=0;var cursor=new BlockPos.MutableBlockPos();
        for(int dx=-REACH;dx<=REACH;dx++)for(int dz=-REACH;dz<=REACH;dz++){
            cursor.set(centre.getX()+dx,centre.getY(),centre.getZ()+dz);if(!level.hasChunkAt(cursor))continue;
            for(int dy=-3;dy<=REACH;dy++){
                var state=level.getBlockState(cursor.set(centre.getX()+dx,centre.getY()+dy,centre.getZ()+dz));
                if(state.is(SurvivalContent.MARBLE_STATUE.get()))statues++;else if(state.is(SurvivalContent.MARBLE_COLUMN.get()))columns++;
                else if(state.is(SurvivalContent.POLISHED_MARBLE.get()))polished++;else if(state.is(SurvivalContent.MOSAIC.get()))mosaic++;
            }
        }
        return new Survey(statues,columns,polished,mosaic);
    }
    private static boolean near(Monument monument,String dimension,BlockPos pos){var at=BlockPos.of(monument.pos);return monument.dimension.equals(dimension)&&Math.abs(at.getX()-pos.getX())<=2*REACH&&Math.abs(at.getZ()-pos.getZ())<=2*REACH;}
    /** Found a monument where the person stands, or take a fresh count of the one already there. Monuments keep their distance, so no stone is counted twice. */
    public static SurvivalApi.ActionResult dedicate(LivingEntity actor){
        if(!(actor.level() instanceof ServerLevel level)||!SurvivalApi.isHumanoid(actor)||!actor.isAlive())return new SurvivalApi.ActionResult(false,"A living person must stand at the monument");
        var data=get(level.getServer());String society=society(actor),dimension=level.dimension().identifier().toString();BlockPos centre=actor.blockPosition();Monument existing=null;
        for(var monument:data.monuments)if(near(monument,dimension,centre)){
            if(!monument.society.equals(society))return new SurvivalApi.ActionResult(false,"Another people's monument already stands here");
            existing=monument;centre=BlockPos.of(monument.pos);break;
        }
        var found=survey(level,centre);
        if(!found.stands())return new SurvivalApi.ActionResult(false,"A monument needs a statue or "+STATUE_OR_COLUMNS+" columns, and "+FLOOR+" square metres of polished marble or mosaic, within "+REACH+" blocks of where you stand: found "+found.statues+" statues, "+found.columns+" columns, "+found.floor()+" of floor");
        if(existing!=null)data.monuments.remove(existing);
        data.monuments.add(new Monument(society,dimension,centre.asLong(),found.worth(),found.statues,found.columns,found.floor(),Math.floorDiv(SurvivalApi.calendar(level),24000L)));data.setDirty();
        return new SurvivalApi.ActionResult(true,"Monument dedicated, worth "+found.worth()+": "+found.statues+" statues, "+found.columns+" columns, "+found.floor()+" of floor");
    }
    /** Forget every monument near a place, whoever founded it. For tests and operators. */
    public static void forgetNear(ServerLevel level,BlockPos pos){var data=get(level.getServer());String dimension=level.dimension().identifier().toString();if(data.monuments.removeIf(m->near(m,dimension,pos)))data.setDirty();}
    /** The society's works and monuments. A monument whose ground is loaded is counted afresh, so one pulled down stops counting. */
    public static JsonObject score(LivingEntity actor){
        var json=new JsonObject();if(!(actor.level() instanceof ServerLevel level)){json.addProperty("error","Server actor required");return json;}
        var data=get(level.getServer());String society=society(actor),dimension=level.dimension().identifier().toString();json.addProperty("society",society);
        int worksWorth=0,worksCount=0;var kinds=new JsonObject();
        for(var entry:data.works.getOrDefault(society,Map.of()).entrySet()){var kind=new JsonObject();kind.addProperty("count",entry.getValue().count);kind.addProperty("worth",entry.getValue().worth);kinds.add(entry.getKey(),kind);worksWorth+=entry.getValue().worth;worksCount+=entry.getValue().count;}
        int monumentWorth=0;var list=new JsonArray();
        for(int i=0;i<data.monuments.size();i++){
            var monument=data.monuments.get(i);if(!monument.society.equals(society))continue;var at=BlockPos.of(monument.pos);
            if(monument.dimension.equals(dimension)&&level.hasChunkAt(at)){
                var now=survey(level,at);
                if(now.worth()!=monument.worth||now.statues!=monument.statues||now.columns!=monument.columns||now.floor()!=monument.floor){monument=new Monument(society,dimension,monument.pos,now.worth(),now.statues,now.columns,now.floor(),monument.day);data.monuments.set(i,monument);data.setDirty();}
            }
            var entry=new JsonObject();entry.addProperty("x",at.getX());entry.addProperty("y",at.getY());entry.addProperty("z",at.getZ());entry.addProperty("worth",monument.worth);entry.addProperty("standing",monument.worth>0);
            entry.addProperty("statues",monument.statues);entry.addProperty("columns",monument.columns);entry.addProperty("floor",monument.floor);entry.addProperty("dedicatedDay",monument.day);list.add(entry);monumentWorth+=monument.worth;
        }
        json.addProperty("culture",worksWorth+monumentWorth);
        var works=new JsonObject();works.addProperty("count",worksCount);works.addProperty("worth",worksWorth);works.add("kinds",kinds);json.add("works",works);
        var monuments=new JsonObject();monuments.addProperty("worth",monumentWorth);monuments.add("list",list);json.add("monuments",monuments);
        var values=new JsonObject();var portable=new JsonObject();PORTABLE.forEach(portable::addProperty);values.add("finishedWork",portable);var fabric=new JsonObject();FABRIC.forEach(fabric::addProperty);values.add("stoneInAMonument",fabric);
        values.addProperty("skill","half worth from a beginner, half as much again from a master");
        values.addProperty("monument","a statue or "+STATUE_OR_COLUMNS+" columns and "+FLOOR+" of polished marble or mosaic within "+REACH+" blocks, founded with the dedicate command; a tenth more for each further kind of work");
        json.add("values",values);return json;
    }
}
