package dev.qube.spawn_9b5fbb9.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import dev.qube.spawn_9b5fbb9.survival.SurvivalState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Clothing is a separate visual layer; armor occupies and covers its ordinary slots. */
public final class ClothingLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    private final HumanoidArmorLayer<AvatarRenderState,PlayerModel,PlayerModel> clothing;
    private ClothingLayer(AvatarRenderer<?> renderer,EntityRenderersEvent.AddLayers event,boolean slim){
        super(renderer);
        clothing=new HumanoidArmorLayer<>(renderer,ArmorModelSet.bake(slim?ModelLayers.PLAYER_SLIM_ARMOR:ModelLayers.PLAYER_ARMOR,event.getEntityModels(),part->new PlayerModel(part,slim)),event.getContext().getEquipmentRenderer());
    }
    public static void register(EntityRenderersEvent.AddLayers event){
        for(var skin:event.getSkins()){var renderer=event.getPlayerRenderer(skin);if(renderer!=null)renderer.addLayer(new ClothingLayer(renderer,event,skin.toString().toLowerCase(java.util.Locale.ROOT).contains("slim")));}
    }
    private static ItemStack piece(String garment,Item shape,int color){
        if(garment.isEmpty())return ItemStack.EMPTY;
        ItemStack item=new ItemStack(shape);item.set(DataComponents.DYED_COLOR,new DyedItemColor(color!=-1?color:garment.contains("wool")?0xC9B991:0xA38C65));return item;
    }
    @Override public void submit(PoseStack pose,SubmitNodeCollector nodes,int light,AvatarRenderState state,float yaw,float pitch){
        SurvivalState body=SurvivalNetwork.entityStates.get(state.id);if(body==null||state.isInvisible)return;
        ItemStack chest=state.chestEquipment,legs=state.legsEquipment,feet=state.feetEquipment,head=state.headEquipment;
        try{
            int upper=body.clothing[2].isEmpty()?0:2;
            state.chestEquipment=chest.isEmpty()?piece(body.clothing[upper],Items.LEATHER_CHESTPLATE,body.clothingColors[upper]):ItemStack.EMPTY;
            state.legsEquipment=legs.isEmpty()?piece(body.clothing[1],Items.LEATHER_LEGGINGS,body.clothingColors[1]):ItemStack.EMPTY;
            state.feetEquipment=feet.isEmpty()?piece(body.clothing[3],Items.LEATHER_BOOTS,body.clothingColors[3]):ItemStack.EMPTY;
            state.headEquipment=ItemStack.EMPTY;
            clothing.submit(pose,nodes,light,state,yaw,pitch);
        }finally{state.chestEquipment=chest;state.legsEquipment=legs;state.feetEquipment=feet;state.headEquipment=head;}
    }
}
