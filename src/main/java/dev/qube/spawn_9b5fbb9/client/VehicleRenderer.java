package dev.qube.spawn_9b5fbb9.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws a vehicle from its kind's block-built item model, one and a half times model size, so the
 * 48-unit model volume holds anything up to four and a half blocks long. */
public final class VehicleRenderer extends EntityRenderer<VehicleEntity,VehicleRenderer.State> {
    public static final float SCALE=1.5f;
    public static final class State extends EntityRenderState {
        public final ItemStackRenderState model=new ItemStackRenderState();
        public float yaw;
    }
    private final ItemModelResolver models;
    private VehicleRenderer(EntityRendererProvider.Context context){super(context);models=context.getItemModelResolver();shadowRadius=1.2f;}
    public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(IndustrialContent.VEHICLE.get(),VehicleRenderer::new);
        event.registerEntityRenderer(IndustrialContent.SHOT.get(),context->new ThrownItemRenderer<>(context,1.6f,false));
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(VehicleEntity vehicle,State state,float partialTicks){
        super.extractRenderState(vehicle,state,partialTicks);
        state.yaw=Mth.rotLerp(partialTicks,vehicle.yRotO,vehicle.getYRot());
        models.updateForNonLiving(state.model,new ItemStack(SurvivalContent.item(vehicle.kind())),ItemDisplayContext.NONE,vehicle);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        pose.pushPose();
        // Models are authored facing north with their wheels at y = 0; entity yaw 0 faces south.
        pose.rotate(Axis.YP.rotationDegrees(180-state.yaw));
        pose.scale(SCALE,SCALE,SCALE);
        pose.translate(0,.5,0);
        state.model.submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);
        pose.popPose();
        super.submit(state,pose,nodes,camera);
    }
}
