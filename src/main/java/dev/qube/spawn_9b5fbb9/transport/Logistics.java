package dev.qube.spawn_9b5fbb9.transport;

import java.util.*;
import dev.qube.spawn_9b5fbb9.industry.Ledger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Where a working actor's materials physically are: on the person, in containers within reach, or in
 * the bed of a vehicle drawn up alongside. Field work such as track laying is supplied by wagon. */
public final class Logistics {
    private Logistics(){}
    private interface Source{long count(String id);long take(String id,long amount);}
    private static List<Source> sources(LivingEntity actor){
        var out=new ArrayList<Source>();
        if(actor instanceof Player player)out.add(container(player.getInventory()));
        else for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND})out.add(new Source(){
            public long count(String id){ItemStack held=actor.getItemBySlot(slot);return !held.isEmpty()&&Ledger.id(held).equals(id)?held.getCount():0;}
            public long take(String id,long amount){ItemStack held=actor.getItemBySlot(slot);if(held.isEmpty()||!Ledger.id(held).equals(id))return 0;int used=(int)Math.min(amount,held.getCount());held.shrink(used);return used;}
        });
        if(actor.level() instanceof ServerLevel level){
            for(BlockPos pos:BlockPos.betweenClosed(actor.blockPosition().offset(-3,-2,-3),actor.blockPosition().offset(3,2,3)))
                if(level.hasChunkAt(pos)&&actor.distanceToSqr(Vec3.atCenterOf(pos))<=9&&level.getBlockEntity(pos) instanceof Container container)out.add(container(container));
            for(VehicleEntity vehicle:level.getEntitiesOfClass(VehicleEntity.class,actor.getBoundingBox().inflate(6)))out.add(new Source(){
                public long count(String id){return vehicle.cargo.getOrDefault(id,0L);}
                public long take(String id,long amount){return Ledger.take(vehicle.cargo,id,amount);}
            });
        }
        return out;
    }
    private static Source container(Container container){
        return new Source(){
            public long count(String id){long sum=0;for(int i=0;i<container.getContainerSize();i++){ItemStack stack=container.getItem(i);if(!stack.isEmpty()&&Ledger.id(stack).equals(id))sum+=stack.getCount();}return sum;}
            public long take(String id,long amount){
                long left=amount;for(int i=0;i<container.getContainerSize()&&left>0;i++){ItemStack stack=container.getItem(i);if(stack.isEmpty()||!Ledger.id(stack).equals(id))continue;int used=(int)Math.min(left,stack.getCount());stack.shrink(used);left-=used;}
                if(left<amount)container.setChanged();return amount-left;
            }
        };
    }
    public static long available(LivingEntity actor,String id){long sum=0;for(Source source:sources(actor))sum+=source.count(id);return sum;}
    /** Names the first shortfall, or returns an empty string when the whole bill is within reach. */
    public static String missing(LivingEntity actor,Map<String,Integer> bill){
        var sources=sources(actor);
        for(var need:bill.entrySet()){long have=0;for(Source source:sources)have+=source.count(need.getKey());if(have<need.getValue())return (need.getValue()-have)+" more "+need.getKey().substring(need.getKey().indexOf(':')+1);}
        return "";
    }
    /** All or nothing: consumes the bill only if every item is fully available. */
    public static boolean consume(LivingEntity actor,Map<String,Integer> bill){
        if(!missing(actor,bill).isEmpty())return false;var sources=sources(actor);
        for(var need:bill.entrySet()){long left=need.getValue();for(Source source:sources){if(left<=0)break;left-=source.take(need.getKey(),left);}}
        return true;
    }
}
