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
    public static volatile boolean openHealth;
    public record Snapshot(int entityId,byte[] state,long calendar,int seasonDays,float health,float maxHealth,boolean open) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath("spawn_9b5fbb9","state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
            (buffer,data)->{buffer.writeVarInt(data.entityId);buffer.writeByteArray(data.state);buffer.writeVarLong(data.calendar);buffer.writeVarInt(data.seasonDays);buffer.writeFloat(data.health);buffer.writeFloat(data.maxHealth);buffer.writeBoolean(data.open);},
            buffer->new Snapshot(buffer.readVarInt(),buffer.readByteArray(8192),buffer.readVarLong(),buffer.readVarInt(),buffer.readFloat(),buffer.readFloat(),buffer.readBoolean()));
        @Override public Type<Snapshot> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToClient(Snapshot.TYPE,Snapshot.CODEC,(payload,context)->context.enqueueWork(()->{
            var decoded=SurvivalState.decode(payload.state);if(entityStates.size()>512)entityStates.clear();entityStates.put(payload.entityId,decoded);
            if(payload.entityId==context.player().getId()){
                clientState=decoded;clientCalendar=payload.calendar;clientSeasonDays=Math.max(4,Math.min(112,payload.seasonDays));
                clientHealth=payload.health;clientMaxHealth=payload.maxHealth;if(payload.open)openHealth=true;
            }
        }));
    }
    public static void send(ServerPlayer player,SurvivalState state,boolean open){
        if(player.connection!=null&&player.connection.hasChannel(Snapshot.TYPE))PacketDistributor.sendToPlayer(player,snapshot(player,state,open));
    }
    public static Snapshot snapshot(net.minecraft.world.entity.LivingEntity actor,SurvivalState state,boolean open){return new Snapshot(actor.getId(),state.encode(),SurvivalApi.calendar((net.minecraft.server.level.ServerLevel)actor.level()),dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get(),actor.getHealth(),actor.getMaxHealth(),open);}
    public static void tracking(net.minecraft.world.entity.LivingEntity actor,SurvivalState state){
        var payload=snapshot(actor,state,false);
        for(ServerPlayer player:((net.minecraft.server.level.ServerLevel)actor.level()).players()){
            if(player.connection!=null&&player.connection.hasChannel(Snapshot.TYPE)&&player.distanceToSqr(actor)<=128*128)
                PacketDistributor.sendToPlayer(player,payload);
        }
    }
}
