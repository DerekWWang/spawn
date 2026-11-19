package dev.qube.spawn_9b5fbb9.transport;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A deployment token: administrators and tests place a finished vehicle with it. Survival vehicles are
 * assembled from their real bill of materials and roll out of an assembly yard instead. */
public class VehicleItem extends Item {
    private final String kind;
    public VehicleItem(Properties properties,String kind){super(properties);this.kind=kind;}
    @Override public InteractionResult useOn(UseOnContext context){
        if(!(context.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;
        Vec3 at=Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        float yaw=context.getPlayer()==null?0:context.getPlayer().getYRot();
        VehicleEntity vehicle=Vehicles.spawn(level,kind,at,yaw);
        if(vehicle==null){if(context.getPlayer()!=null)context.getPlayer().sendOverlayMessage(Component.literal("Rolling stock must be set down on laid track"));return InteractionResult.FAIL;}
        context.getItemInHand().shrink(1);return InteractionResult.SUCCESS;
    }
}
