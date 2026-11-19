package dev.qube.spawn_9b5fbb9.environment;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Each client receives its own observation; actor tracking never changes local weather. */
public final class WeatherNetwork {
    public record Local(String dimension,String profile,long calendar,int x,int y,int z,float temperature,float rain,float wind,boolean snow) implements CustomPacketPayload {
        public static final Type<Local> TYPE=new Type<>(Identifier.fromNamespaceAndPath("spawn_9b5fbb9","weather"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Local> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.dimension,128);b.writeUtf(p.profile,16);b.writeLong(p.calendar);b.writeInt(p.x);b.writeInt(p.y);b.writeInt(p.z);b.writeFloat(p.temperature);b.writeFloat(p.rain);b.writeFloat(p.wind);b.writeBoolean(p.snow);},b->new Local(b.readUtf(128),b.readUtf(16),b.readLong(),b.readInt(),b.readInt(),b.readInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean()));
        public Local {if(!Float.isFinite(temperature)||!Float.isFinite(rain)||!Float.isFinite(wind)||rain<0||rain>1||wind<0||wind>24)throw new IllegalArgumentException("Invalid local weather");}
        public boolean applicable(String currentDimension,int currentX,int currentY,int currentZ,long ageMillis){return !profile.equals("legacy")&&dimension.equals(currentDimension)&&ageMillis>=0&&ageMillis<5000&&Math.abs((long)currentX-x)<=192&&Math.abs((long)currentY-y)<=32&&Math.abs((long)currentZ-z)<=192;}
        @Override public Type<Local> type(){return TYPE;}
    }
    public static volatile Local client;
    public static volatile long receivedNanos;
    public static volatile float previousRain;
    private WeatherNetwork(){}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("4").playToClient(Local.TYPE,Local.CODEC,(packet,context)->context.enqueueWork(()->{previousRain=client==null||!client.dimension.equals(packet.dimension)?packet.rain:client.rain;client=packet;receivedNanos=System.nanoTime();}));}
    public static Local snapshot(ServerPlayer player){var level=player.level();var pos=player.blockPosition();var settings=WorldEnvironment.settings(level);var w=WorldEnvironment.active(level)?EnvironmentApi.at(level,pos):new EnvironmentModel.Weather(0,0,0,0,0,false);return new Local(level.dimension().identifier().toString(),WorldEnvironment.active(level)?settings.profile():"legacy",dev.qube.spawn_9b5fbb9.survival.SurvivalApi.calendar(level),pos.getX(),pos.getY(),pos.getZ(),(float)w.temperature(),(float)w.rain(),(float)w.wind(),w.snow());}
    public static void tick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event){if(event.getEntity() instanceof ServerPlayer player&&player.tickCount%20==0&&player.connection!=null&&player.connection.hasChannel(Local.TYPE))PacketDistributor.sendToPlayer(player,snapshot(player));}
}
