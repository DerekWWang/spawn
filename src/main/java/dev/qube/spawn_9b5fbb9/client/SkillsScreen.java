package dev.qube.spawn_9b5fbb9.client;

import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Separate skills view; pagination keeps text legible at small GUI resolutions. */
public final class SkillsScreen extends Screen {
    private int first,selected;
    public SkillsScreen(){super(Component.literal("Skills"));}
    private int rows(){return Math.max(3,Math.min(11,(height-150)/18));}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        int middle=width/2;
        addRenderableWidget(Button.builder(Component.literal("Previous"),b->{first=Math.max(0,first-rows());selected=first;}).bounds(middle-128,height-25,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Health"),b->Minecraft.getInstance().setScreenAndShow(new SurvivalHud.HealthScreen())).bounds(middle-40,height-25,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"),b->{first=Math.min(Math.max(0,11-rows()),first+rows());selected=first;}).bounds(middle+48,height-25,80,20).build());
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){first=Math.clamp(first-(int)Math.signum(dy),0,Math.max(0,11-rows()));selected=first;return true;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xF0121C22);var font=Minecraft.getInstance().font;var body=SurvivalNetwork.clientState;
        int panel=Math.min(480,width-20),left=(width-panel)/2,shown=Math.min(rows(),11-first),top=54;
        g.text(font,"SKILLS",left,12,0xFFF1D5A0);
        g.text(font,"Useful work teaches; interests affect learning.",left,28,0xFFB8CCC7);
        g.text(font,"Trade",left,42,0xFFB8CCC7);g.text(font,"Level",left+94,42,0xFFB8CCC7);g.text(font,"Progress",left+136,42,0xFFB8CCC7);g.text(font,"Interest",left+panel-72,42,0xFFB8CCC7);
        for(int index=0;index<shown;index++){
            int number=first+index,y=top+index*18;var skill=SkillSet.Skill.values()[number];
            if(mouseX>=left&&mouseX<=left+panel&&mouseY>=y-2&&mouseY<y+16)selected=number;
            if(selected==number)g.fill(left-3,y-2,left+panel,y+15,0xFF26383C);
            var passion=body.skills.passion(skill);int color=passion==SkillSet.Passion.BURNING?0xFFF0AB5D:passion==SkillSet.Passion.INTERESTED?0xFFE4C88A:0xFF9EAFAD;
            g.text(font,skill.label(),left,y,0xFFE6E9DF);g.text(font,body.skills.level(skill)+"/20",left+94,y,0xFFE6E9DF);
            int barLeft=left+136,barWidth=Math.min(102,panel-220);double fraction=Math.clamp(body.skills.levelExperience(skill)/body.skills.levelCost(skill),0,1);
            g.fill(barLeft,y+2,barLeft+barWidth,y+9,0xFF34494B);g.fill(barLeft,y+2,barLeft+(int)(barWidth*fraction),y+9,0xFF8AB7A3);
            g.text(font,passion==SkillSet.Passion.NONE?"None":passion==SkillSet.Passion.INTERESTED?"Interested":"Burning",left+panel-72,y,color);
        }
        var skill=SkillSet.Skill.values()[selected];int detail=top+shown*18+7;
        g.text(font,skill.label()+" — "+(body.skills.level(skill)==20?"Mastery":String.format(Locale.ROOT,"%.0f XP to next level",body.skills.untilNext(skill))),left,detail,0xFFF1D5A0);
        g.text(font,String.format(Locale.ROOT,"Learning %.0f%% | Today %.0f / 4000 XP",body.skills.learningMultiplier(skill)*100,body.skills.dailyNetExperience(skill)),left,detail+13,0xFFD7E3DE);
        g.text(font,String.format(Locale.ROOT,"Decay %.0f XP/day | Effective %.1f/20",body.skills.decayPerDay(skill),body.skills.level(skill)*Math.min(body.manipulation(),body.mental())),left,detail+26,0xFFB8CCC7);
        g.text(font,"Hover a trade; scroll or use the page buttons.",left,detail+39,0xFF9EAFAD);
        super.extractRenderState(g,mouseX,mouseY,partial);
    }
}
