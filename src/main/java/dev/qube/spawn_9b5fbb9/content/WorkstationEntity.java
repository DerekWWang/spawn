package dev.qube.spawn_9b5fbb9.content;

import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.List;

/** Inputs, recipe requirements and output plan are reserved and persisted before work begins. */
public class WorkstationEntity extends BlockEntity {
    public ItemStack input=ItemStack.EMPTY,secondary=ItemStack.EMPTY,output=ItemStack.EMPTY,pendingOutput=ItemStack.EMPTY;
    public String recipe="",worker="",jobStation="",jobSkill="";
    public double workRemainder,jobPractice,jobMinimumManipulation=.05,qualityScore=-1;
    public long elapsed,last,required;
    public int fuel,wear,jobMinimumSkill;
    public boolean configured,legacy,jobManual,jobHeated=true,practiceAwarded;
    public WorkstationEntity(BlockPos pos,BlockState state){super(SurvivalContent.WORKSTATION_ENTITY.get(),pos,state);}
    public boolean hasUsableHeat(){if(configured)return !jobHeated||fuel>0;var r=Production.RECIPES.get(recipe);return !legacyShape(r)&&r!=null&&!r.heated()||fuel>0;}
    private boolean legacyShape(Production.Recipe r){return r==null||!r.input().equals(BuiltInRegistries.ITEM.getKey(input.getItem()).toString())||!r.station().equals(Production.stationId(this));}
    private Skill skill(){try{return jobSkill.isEmpty()?null:Skill.valueOf(jobSkill);}catch(IllegalArgumentException invalid){return null;}}
    public void configure(Production.Recipe r,LivingEntity actor){
        configured=true;legacy=false;jobStation=r.station();jobSkill=r.skill()==null?"":r.skill().name();jobManual=r.manual();jobHeated=r.heated();jobMinimumSkill=r.minimumSkill();jobMinimumManipulation=r.minimumManipulation();jobPractice=r.practice();practiceAwarded=false;
        worker=r.manual()?actor.getUUID().toString():"";qualityScore=r.skill()==Skill.POTTERY?SkillApi.effective(actor,Skill.POTTERY):-1;
        pendingOutput=new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(r.output())),r.amount());
    }
    private void migrate(LivingEntity actor){
        var r=Production.RECIPES.get(recipe);
        if(legacyShape(r)){configured=true;legacy=true;jobManual=!worker.isEmpty();jobHeated=true;jobStation=Production.stationId(this);jobSkill="";jobMinimumSkill=0;jobMinimumManipulation=.05;jobPractice=0;qualityScore=-1;return;}
        if(actor!=null){configure(r,actor);return;}
        // A historical passive reservation remains passive and earns no retroactive practice.
        configured=true;legacy=true;jobManual=false;jobHeated=true;jobStation=Production.stationId(this);jobSkill="";jobPractice=0;jobMinimumSkill=0;jobMinimumManipulation=.05;qualityScore=-1;
    }
    public static void tick(Level world,BlockPos pos,BlockState state,WorkstationEntity be){if(world instanceof ServerLevel level&&level.getGameTime()%20==0)be.advance(level,SurvivalApi.calendar(level));}
    public void advance(ServerLevel level,long now){
        long dt=last==0?20:Math.max(0,now-last);last=now;
        if(input.isEmpty()||required<=0||!output.isEmpty())return;
        LivingEntity actor=null;
        if(!worker.isEmpty()){try{var entity=level.getEntity(java.util.UUID.fromString(worker));if(entity instanceof LivingEntity living)actor=living;}catch(IllegalArgumentException invalid){return;}}
        if(!configured)migrate(actor);
        if(!legacy&&!jobStation.equals(Production.stationId(this)))return;
        if(!hasUsableHeat())return;
        Skill learned=skill();double rate=1;SurvivalState body=null;
        if(jobManual){
            if(actor==null||!actor.isAlive()||actor.level()!=level||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition))>36)return;
            body=SurvivalApi.state(actor);
            if(body.manipulation()<jobMinimumManipulation||body.mental()<=.05||(learned!=null&&SkillApi.level(actor,learned)<jobMinimumSkill))return;
            if(!LaborGuard.mayAdvanceForge(actor,this))return;
            rate=Math.min(body.manipulation(),body.mental())*(learned==null?1:SkillApi.workRate(actor,learned));dt=Math.min(dt,20);
        }
        long remaining=Math.max(0,(long)Math.ceil((required-elapsed-workRemainder)/rate));long used=Math.min(dt,remaining);if(jobHeated)used=Math.min(used,fuel);
        if(used<=0)return;
        if(jobHeated)fuel-=used;workRemainder+=used*rate;long progress=(long)workRemainder;workRemainder-=progress;elapsed+=progress;
        if(jobManual){body.hunger=Math.max(0,body.hunger-used*.5/1000);SurvivalApi.save(actor,body);LaborGuard.worked(actor);if(learned==Skill.POTTERY)qualityScore=qualityScore<0?SkillApi.effective(actor,learned):Math.min(qualityScore,SkillApi.effective(actor,learned));}
        if(elapsed>=required){
            ItemStack consumed=input;output=pendingOutput.isEmpty()?Production.finish(recipe,consumed,level):pendingOutput.copy();ItemAging.inherit(output,List.of(consumed,secondary),level);
            if(!legacy&&learned==Skill.POTTERY&&output.is(SurvivalContent.item("fired_pot"))){var data=ItemAging.data(output);data.putString("potQuality",qualityScore>=60?"fine":qualityScore>=25?"standard":"rough");ItemAging.data(output,data);}
            input=ItemStack.EMPTY;secondary=ItemStack.EMPTY;pendingOutput=ItemStack.EMPTY;elapsed=0;required=0;wear++;
            // Save hunger before the skill adapter reloads/saves the body; never overwrite its award.
            if(!practiceAwarded&&jobManual&&actor!=null&&learned!=null&&jobPractice>0){practiceAwarded=true;setChanged();SkillApi.award(actor,learned,jobPractice);}
        }
        setChanged();
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);input=in.read("input",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);secondary=in.read("secondary",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);output=in.read("output",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);pendingOutput=in.read("pendingOutput",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        recipe=in.getStringOr("recipe","");worker=in.getStringOr("worker","");workRemainder=in.getDoubleOr("workRemainder",0);elapsed=in.getLongOr("elapsed",0);last=in.getLongOr("last",0);required=in.getLongOr("required",0);fuel=in.getIntOr("fuel",0);wear=in.getIntOr("wear",0);
        configured=in.getBooleanOr("configured",false);legacy=in.getBooleanOr("legacy",false);jobStation=in.getStringOr("jobStation","");jobSkill=in.getStringOr("jobSkill","");jobManual=in.getBooleanOr("jobManual",!worker.isEmpty());jobHeated=in.getBooleanOr("jobHeated",true);jobMinimumSkill=in.getIntOr("jobMinimumSkill",0);jobMinimumManipulation=in.getDoubleOr("jobMinimumManipulation",.05);jobPractice=in.getDoubleOr("jobPractice",0);qualityScore=in.getDoubleOr("qualityScore",-1);practiceAwarded=in.getBooleanOr("practiceAwarded",false);
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);out.store("input",ItemStack.OPTIONAL_CODEC,input);out.store("secondary",ItemStack.OPTIONAL_CODEC,secondary);out.store("output",ItemStack.OPTIONAL_CODEC,output);out.store("pendingOutput",ItemStack.OPTIONAL_CODEC,pendingOutput);out.putString("recipe",recipe);out.putString("worker",worker);out.putDouble("workRemainder",workRemainder);out.putLong("elapsed",elapsed);out.putLong("last",last);out.putLong("required",required);out.putInt("fuel",fuel);out.putInt("wear",wear);
        out.putBoolean("configured",configured);out.putBoolean("legacy",legacy);out.putString("jobStation",jobStation);out.putString("jobSkill",jobSkill);out.putBoolean("jobManual",jobManual);out.putBoolean("jobHeated",jobHeated);out.putInt("jobMinimumSkill",jobMinimumSkill);out.putDouble("jobMinimumManipulation",jobMinimumManipulation);out.putDouble("jobPractice",jobPractice);out.putDouble("qualityScore",qualityScore);out.putBoolean("practiceAwarded",practiceAwarded);
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null){Block.popResource(level,pos,input);Block.popResource(level,pos,secondary);Block.popResource(level,pos,output);input=ItemStack.EMPTY;secondary=ItemStack.EMPTY;output=ItemStack.EMPTY;pendingOutput=ItemStack.EMPTY;}super.preRemoveSideEffects(pos,state);}
}
