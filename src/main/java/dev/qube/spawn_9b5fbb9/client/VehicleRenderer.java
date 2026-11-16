package dev.qube.spawn_9b5fbb9.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.qube.spawn_9b5fbb9.industry.IndustrialContent;
import dev.qube.spawn_9b5fbb9.transport.VehicleEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws a vehicle at true scale from its sliced body models, with each wheel turned by the distance
 * rolled and coupling rods carried round on their crank pins. Layouts come from VehicleLayouts. */
public final class VehicleRenderer extends EntityRenderer<VehicleEntity,VehicleRenderer.State> {
    /** Body models are authored at 16/1.5 units per block so a model volume spans 4.5 blocks. */
    public static final float BODY_SCALE=1.5f,ROD_SCALE=4f/3f;
    public static final class State extends EntityRenderState {
        public final List<ItemStackRenderState> models=new ArrayList<>();
        public VehicleLayouts.Layout layout;
        public float yaw,roll;
    }
    private static final Map<String,ItemStack> STACKS=new HashMap<>();
    private final ItemModelResolver resolver;
    private VehicleRenderer(EntityRendererProvider.Context context){super(context);resolver=context.getItemModelResolver();shadowRadius=1.4f;}
    public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(IndustrialContent.VEHICLE.get(),VehicleRenderer::new);
        event.registerEntityRenderer(IndustrialContent.SHOT.get(),context->new ThrownItemRenderer<>(context,1.6f,false));
    }
    /** Any item can carry any item model: the part models have no items of their own. */
    private static ItemStack part(String name){
        return STACKS.computeIfAbsent(name,key->{var stack=new ItemStack(Items.STICK);stack.set(DataComponents.ITEM_MODEL,Identifier.fromNamespaceAndPath(IndustrialContent.MODID,"vehicle/"+key));return stack;});
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(VehicleEntity vehicle,State state,float partialTicks){
        super.extractRenderState(vehicle,state,partialTicks);
        state.yaw=Mth.rotLerp(partialTicks,vehicle.yRotO,vehicle.getYRot());state.roll=Mth.lerp(partialTicks,vehicle.rollO,vehicle.roll);
        var layout=VehicleLayouts.LAYOUTS.getOrDefault(vehicle.kind(),VehicleLayouts.LAYOUTS.get("cart"));state.layout=layout;
        int needed=layout.centres().length+layout.wheels().length+layout.rods().length;
        while(state.models.size()<needed)state.models.add(new ItemStackRenderState());
        int index=0;
        for(int slice=0;slice<layout.centres().length;slice++)resolver.updateForNonLiving(state.models.get(index++),part(vehicle.kind()+"_"+slice),ItemDisplayContext.NONE,vehicle);
        for(var wheel:layout.wheels())resolver.updateForNonLiving(state.models.get(index++),part(wheel.model()),ItemDisplayContext.NONE,vehicle);
        for(var rod:layout.rods())resolver.updateForNonLiving(state.models.get(index++),part(rod.model()),ItemDisplayContext.NONE,vehicle);
    }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(VehicleEntity vehicle,float partialTicks){return vehicle.getBoundingBox().inflate(4);}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        var layout=state.layout;
        if(layout!=null){
            pose.pushPose();
            // Models face north with +x to the vehicle's right; entity yaw 0 faces south.
            pose.rotate(Axis.YP.rotationDegrees(180-state.yaw));
            int index=0;
            for(float centre:layout.centres()){
                pose.pushPose();pose.translate(0,0,-centre);pose.scale(BODY_SCALE,BODY_SCALE,BODY_SCALE);pose.translate(0,1.5f,0);
                state.models.get(index++).submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();
            }
            for(var wheel:layout.wheels()){
                pose.pushPose();pose.translate(wheel.x(),wheel.radius(),-wheel.z());
                // Wheel models carry their flange on the inner face: the left-hand wheel is the same model turned about.
                if(wheel.x()<0){pose.rotate(Axis.YP.rotationDegrees(180));pose.rotate(Axis.XP.rotation(state.roll/wheel.radius()));}
                else pose.rotate(Axis.XP.rotation(-state.roll/wheel.radius()));pose.scale(wheel.radius(),wheel.radius(),wheel.radius());
                state.models.get(index++).submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();
            }
            for(var rod:layout.rods()){
                float angle=state.roll/rod.axle()+rod.phase();
                pose.pushPose();pose.translate(rod.x(),rod.axle()+rod.crank()*Mth.cos(angle),-rod.z()-rod.crank()*Mth.sin(angle));pose.scale(ROD_SCALE,ROD_SCALE,ROD_SCALE);
                state.models.get(index++).submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();
            }
            pose.popPose();
        }
        super.submit(state,pose,nodes,camera);
    }
}
