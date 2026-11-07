package dev.qube.spawn_9b5fbb9.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import dev.qube.spawn_9b5fbb9.survival.SurvivalState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/** A simple fabric silhouette for biped NPCs; it never occupies their armor slots. */
public final class NpcClothingLayer extends RenderLayer<LivingEntityRenderState,EntityModel<LivingEntityRenderState>> {
    private static final ContextKey<SurvivalState> CLOTHES=new ContextKey<>(Identifier.fromNamespaceAndPath("spawn_9b5fbb9","clothes"));
    private static final Identifier TEXTURE=Identifier.withDefaultNamespace("textures/block/white_wool.png");
    private final GarmentModel[] models={new GarmentModel(0),new GarmentModel(1),new GarmentModel(2)};
    @SuppressWarnings({"rawtypes","unchecked"})
    private NpcClothingLayer(LivingEntityRenderer renderer){super(renderer);}
    public static void modifiers(RegisterRenderStateModifiersEvent event){
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity,LivingEntityRenderState,?>>() {},
            (entity,state)->state.setRenderData(CLOTHES,SurvivalNetwork.entityStates.get(entity.getId())));
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void register(EntityRenderersEvent.AddLayers event){
        for(var type:event.getEntityTypes()){
            var renderer=event.getRenderer(type);
            if(renderer instanceof LivingEntityRenderer living&&!(renderer instanceof AvatarRenderer))living.addLayer(new NpcClothingLayer(living));
        }
    }
    @Override public void submit(PoseStack pose,SubmitNodeCollector nodes,int light,LivingEntityRenderState state,float yaw,float pitch){
        var body=state.getRenderData(CLOTHES);if(body==null||state.isInvisible)return;
        int upper=body.clothing[2].isEmpty()?0:2;
        int[] slots={upper,1,3};
        for(int i=0;i<3;i++)if(!body.clothing[slots[i]].isEmpty()){
            int slot=slots[i],color=body.clothingColors[slot]!=-1?body.clothingColors[slot]:body.clothing[slot].contains("wool")?0xC9B991:0xA38C65;
            renderColoredCutoutModel(models[i],TEXTURE,pose,nodes,light,state,0xFF000000|color,0);
        }
    }
    private static final class GarmentModel extends EntityModel<LivingEntityRenderState> {
        private final ModelPart left,right;
        private GarmentModel(int piece){super(mesh(piece));left=root().getChild("left_leg");right=root().getChild("right_leg");}
        private static ModelPart mesh(int piece){
            MeshDefinition mesh=new MeshDefinition();var root=mesh.getRoot();
            if(piece==0)root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-4,0,-2,8,12,4,new CubeDeformation(.18f)),PartPose.ZERO);
            for(boolean left:new boolean[]{true,false}){
                var cube=CubeListBuilder.create();
                if(piece>0)cube.texOffs(0,0).addBox(-2,piece==2?8:0,-2,4,piece==2?4:12,4,new CubeDeformation(.18f));
                root.addOrReplaceChild(left?"left_leg":"right_leg",cube,PartPose.offset(left?1.9f:-1.9f,12,0));
            }
            return LayerDefinition.create(mesh,16,16).bakeRoot();
        }
        @Override public void setupAnim(LivingEntityRenderState state){super.setupAnim(state);left.xRot=(float)Math.cos(state.walkAnimationPos*.6662)*1.4f*state.walkAnimationSpeed;right.xRot=-left.xRot;}
    }
}
