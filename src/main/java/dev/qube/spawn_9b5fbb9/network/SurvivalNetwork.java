package dev.qube.spawn_9b5fbb9.network;

import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SurvivalNetwork {
    public static volatile SurvivalState clientState=new SurvivalState();
    public static volatile long clientCalendar;
    public static volatile int clientSeasonDays=28;
    public static final java.util.Map<Integer,SurvivalState> entityStates=new java.util.concurrent.ConcurrentHashMap<>();
    public static volatile float clientHealth=20,clientMaxHealth=20;
    public static volatile boolean openHealth,openSkills;
    public record Snapshot(int entityId,byte[] state,long calendar,int seasonDays,float health,float maxHealth,boolean open,int screenMode) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath("spawn_9b5fbb9","state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
            (buffer,data)->{buffer.writeVarInt(data.entityId);buffer.writeByteArray(data.state);buffer.writeVarLong(data.calendar);buffer.writeVarInt(data.seasonDays);buffer.writeFloat(data.health);buffer.writeFloat(data.maxHealth);buffer.writeBoolean(data.open);buffer.writeVarInt(data.screenMode);},
            buffer->new Snapshot(buffer.readVarInt(),buffer.readByteArray(8192),buffer.readVarLong(),buffer.readVarInt(),buffer.readFloat(),buffer.readFloat(),buffer.readBoolean(),buffer.readVarInt()));
        @Override public Type<Snapshot> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("4").playToClient(Snapshot.TYPE,Snapshot.CODEC,(payload,context)->context.enqueueWork(()->{
            var decoded=SurvivalState.decode(payload.state);if(entityStates.size()>4096)entityStates.clear();entityStates.put(payload.entityId,decoded);
            if(payload.entityId==context.player().getId()){
                clientState=decoded;clientCalendar=payload.calendar;clientSeasonDays=Math.max(4,Math.min(112,payload.seasonDays));
                clientHealth=payload.health;clientMaxHealth=payload.maxHealth;if(payload.open){if(payload.screenMode==1)openSkills=true;else openHealth=true;}
            }
        }));
    }
    public static void send(ServerPlayer player,SurvivalState state,boolean open){
        if(player.connection!=null&&player.connection.hasChannel(Snapshot.TYPE))PacketDistributor.sendToPlayer(player,snapshot(player,state,open));
    }
    public static void sendSkills(ServerPlayer player,SurvivalState state){if(player.connection!=null&&player.connection.hasChannel(Snapshot.TYPE)){var current=snapshot(player,state,true);PacketDistributor.sendToPlayer(player,new Snapshot(current.entityId,current.state,current.calendar,current.seasonDays,current.health,current.maxHealth,true,1));}}
    public static Snapshot snapshot(net.minecraft.world.entity.LivingEntity actor,SurvivalState state,boolean open){return new Snapshot(actor.getId(),state.encode(),SurvivalApi.calendar((net.minecraft.server.level.ServerLevel)actor.level()),dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get(),actor.getHealth(),actor.getMaxHealth(),open,0);}
    // Every actor's whole body used to go to every player within 128 blocks, every second: a hundred people in one
    // settlement were ten thousand packets a second, and each told its reader a neighbour's hunger, illness and skill.
    // A player is sent his own body. Of anyone else he is sent what they wear, when it changes or comes into view.
    private static final java.util.Map<net.minecraft.world.entity.LivingEntity,String> SHOWN=new java.util.WeakHashMap<>();
    private static boolean listening(ServerPlayer player){return player.connection!=null&&player.connection.hasChannel(Snapshot.TYPE);}
    private static String worn(SurvivalState state){return String.join("|",state.clothing)+java.util.Arrays.toString(state.clothingColors);}
    private static Snapshot appearance(net.minecraft.world.entity.LivingEntity actor,SurvivalState state){
        var shown=new SurvivalState();System.arraycopy(state.clothing,0,shown.clothing,0,4);System.arraycopy(state.clothingColors,0,shown.clothingColors,0,4);
        return new Snapshot(actor.getId(),shown.encode(),0,28,0,0,false,0);
    }
    public static void tracking(net.minecraft.world.entity.LivingEntity actor,SurvivalState state){
        if(actor instanceof ServerPlayer self)send(self,state,false);
        String worn=worn(state);if(worn.equals(SHOWN.put(actor,worn)))return;
        var payload=appearance(actor,state);
        for(ServerPlayer player:((net.minecraft.server.level.ServerLevel)actor.level()).players())
            if(player!=actor&&listening(player)&&player.distanceToSqr(actor)<=128*128)PacketDistributor.sendToPlayer(player,payload);
    }
    /** A player who comes within sight of an actor is shown what it wears; the unclothed need no telling. */
    public static void appear(net.minecraft.world.entity.LivingEntity actor,ServerPlayer viewer){
        if(viewer==actor||!listening(viewer)||!SurvivalApi.isHumanoid(actor)||!actor.getPersistentData().contains(SurvivalApi.KEY))return;
        var state=SurvivalApi.state(actor);for(String garment:state.clothing)if(!garment.isEmpty()){PacketDistributor.sendToPlayer(viewer,appearance(actor,state));return;}
    }
    /** What the client held of the server it has left: entity numbers start again on the next one. */
    public static void forget(){entityStates.clear();clientState=new SurvivalState();clientCalendar=0;clientHealth=20;clientMaxHealth=20;openHealth=false;openSkills=false;}
}
