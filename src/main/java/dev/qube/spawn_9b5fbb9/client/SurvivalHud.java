package dev.qube.spawn_9b5fbb9.client;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.*;
import java.util.Locale;

public final class SurvivalHud {
    private static final net.minecraft.client.KeyMapping HEALTH=new net.minecraft.client.KeyMapping("key.spawn_9b5fbb9.health",com.mojang.blaze3d.platform.InputConstants.KEY_H,net.minecraft.client.KeyMapping.Category.MISC);
    private SurvivalHud(){}
    public static void keys(RegisterKeyMappingsEvent event){event.register(HEALTH);}
    public static void register(RegisterGuiLayersEvent event){
        event.registerAboveAll(Identifier.fromNamespaceAndPath("spawn_9b5fbb9","survival"),(graphics,delta)->{
            Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.gui.hud.isHidden()||!Config.HUD.get())return;
            SurvivalState state=SurvivalNetwork.clientState;
            graphics.fill(5,5,167,107,0xD9141D22);
            graphics.text(mc.font,"SURVIVAL",10,10,0xFFF1D5A0);
            bar(graphics,10,24,"Recreation",state.recreation,100,0xFFB99BDC,String.format(Locale.ROOT,"%.1f / %.1f h",state.recreationTicks/1000d,state.quotaTicks/1000d));
            bar(graphics,10,39,"Hunger",state.hunger,100,0xFFE6B260,String.format(Locale.ROOT,"%.0f",state.hunger));
            bar(graphics,10,54,"Thirst",state.thirst,100,0xFF76C4DE,String.format(Locale.ROOT,"%.0f",state.thirst));
            bar(graphics,10,69,"Temperature",state.temperature-28,15,state.temperature<state.rules.number("coldThreshold")?0xFF85ADD9:state.temperature>state.rules.number("hotThreshold")?0xFFE27560:0xFF90BF96,String.format(Locale.ROOT,"%.1f C",state.temperature));
            bar(graphics,10,84,"Health",SurvivalNetwork.clientHealth,SurvivalNetwork.clientMaxHealth,0xFFD97F7F,String.format(Locale.ROOT,"%.0f / %.0f",SurvivalNetwork.clientHealth,SurvivalNetwork.clientMaxHealth));
            graphics.text(mc.font,SurvivalCommands.calendarText(SurvivalNetwork.clientCalendar,SurvivalNetwork.clientSeasonDays),10,111,0xFFF0E5CD);
            var weather=LocalWeather.current();if(weather!=null)graphics.text(mc.font,String.format(Locale.ROOT,"Air %.1f C | Wind %.0f m/s | %s",weather.temperature(),weather.wind(),weather.rain()>.15?weather.snow()?"Snow":"Rain":"Dry"),10,137,0xFFD0E6E9);
            if(!state.conditions().equals("Stable"))graphics.text(mc.font,state.conditions(),10,124,0xFFFFAC92);
        });
    }
    private static void bar(GuiGraphicsExtractor g,int x,int y,String label,double amount,double max,int color,String value){
        var font=Minecraft.getInstance().font;
        g.text(font,label,x,y,0xFFE7E7DF);g.text(font,value,x+146-font.width(value),y,0xFFE7E7DF);
        g.fill(x,y+10,x+146,y+13,0xFF344047);g.fill(x,y+10,x+(int)(146*Math.max(0,Math.min(1,amount/Math.max(1,max)))),y+13,color);
    }
    public static void tick(ClientTickEvent.Post event){
        // The body is already here, sent every second: the key opens the screen without asking the server for it.
        while(HEALTH.consumeClick())if(Minecraft.getInstance().player!=null){var mc=Minecraft.getInstance();mc.setScreenAndShow(mc.gui.screen() instanceof HealthScreen?null:new HealthScreen());}
        if(SurvivalNetwork.openHealth&&Minecraft.getInstance().player!=null){SurvivalNetwork.openHealth=false;var mc=Minecraft.getInstance();mc.setScreenAndShow(mc.gui.screen() instanceof HealthScreen?null:new HealthScreen());}
        if(SurvivalNetwork.openSkills&&Minecraft.getInstance().player!=null){SurvivalNetwork.openSkills=false;Minecraft.getInstance().setScreenAndShow(new SkillsScreen());}
    }
    public static final class HealthScreen extends Screen {
        public HealthScreen(){super(Component.literal("Health, Clothing & Skills"));}
        @Override protected void init(){addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Skills and interests"),b->Minecraft.getInstance().setScreenAndShow(new SkillsScreen())).bounds(width/2-80,height-25,160,20).build());}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xF0121C22);var font=Minecraft.getInstance().font;var s=SurvivalNetwork.clientState;
            float scale=Math.min(1,Math.min((width-16)/460f,(height-16)/360f));g.pose().pushMatrix();g.pose().scale(scale,scale);
            int left=Math.max(12,(int)(width/scale/2)-225),top=12;
            g.text(font,"HEALTH, CLOTHING & SKILLS",left,top,0xFFF1D5A0);
            g.text(font,SurvivalCommands.calendarText(SurvivalNetwork.clientCalendar,SurvivalNetwork.clientSeasonDays),left,top+16,0xFFB8CCC7);
            g.text(font,String.format(Locale.ROOT,"Movement %.0f%%    Manipulation %.0f%%    Mental %.0f%%",100*s.movement(),100*s.manipulation(),100*s.mental()),left,top+31,0xFFFFFFFF);
            g.text(font,"Limbs & digits",left,top+52,0xFFF1D5A0);
            int line=top+68;
            for(int i=0;i<s.anatomy.length;i++){
                if(i>=4&&i<8)continue;
                String name=SurvivalState.Part.values()[i].name().toLowerCase(Locale.ROOT).replace('_',' ');
                int index=i<4?i:i-4;int column=index/12,row=index%12;
                g.text(font,String.format(Locale.ROOT,"%-16s %3.0f%%",name,s.anatomy[i]),left+column*150,line+row*11,s.anatomy[i]<30?0xFFFF9C8B:0xFFD7E3DE);
            }
            int right=left+305;
            g.text(font,"Cranial / sensory",right,top+52,0xFFF1D5A0);
            for(int i=4;i<8;i++)g.text(font,SurvivalState.Part.values()[i].name().toLowerCase(Locale.ROOT).replace('_',' ')+": "+Math.round(s.anatomy[i])+"%",right,top+68+(i-4)*12,0xFFD7E3DE);
            g.text(font,"Clothing beneath armor",right,top+123,0xFFF1D5A0);
            for(int i=0;i<4;i++){
                String garment=s.clothing[i].isEmpty()?"Bare":s.clothing[i].substring(s.clothing[i].indexOf(':')+1).replace('_',' ')+" "+Math.round(s.condition(i)*100)+"%";
                g.text(font,new String[]{"Shirt: ","Pants: ","Jacket: ","Shoes: "}[i]+garment,right,top+139+i*12,0xFFD7E3DE);
            }
            int figureX=right+108,figureY=top+187;
            g.fill(figureX+6,figureY,figureX+14,figureY+8,partColor(s.anatomy[4]));
            g.fill(figureX+5,figureY+9,figureX+15,figureY+23,0xFFD6C8AE);
            g.fill(figureX,figureY+10,figureX+4,figureY+24,partColor(s.anatomy[0]));
            g.fill(figureX+16,figureY+10,figureX+20,figureY+24,partColor(s.anatomy[1]));
            g.fill(figureX+5,figureY+24,figureX+9,figureY+39,partColor(s.anatomy[2]));
            g.fill(figureX+11,figureY+24,figureX+15,figureY+39,partColor(s.anatomy[3]));
            g.text(font,"Health effects",left,top+211,0xFFF1D5A0);
            g.text(font,s.conditions(),left,top+227,0xFFFFBE9D);
            g.text(font,"Practiced skills",left,top+247,0xFFF1D5A0);
            g.text(font,"Sixteen independent trades. Levels 0–20, interests and daily practice.",left,top+266,0xFFB8CCC7);
            g.text(font,"Open Skills and interests below for progress and learning details.",left,top+282,0xFFB8CCC7);
            g.text(font,"Water: /survival act drink   |   Recreation: /survival act read or play",left,top+305,0xFFB8CCC7);
            g.text(font,"Esc to return. Time continues while inspecting.",left,top+321,0xFFB8CCC7);
            g.pose().popMatrix();super.extractRenderState(g,mouseX,mouseY,partial);
        }
        private static int partColor(double condition){return condition<30?0xFFE78F80:condition<70?0xFFE2C27A:0xFF9FC2A5;}
    }
}
