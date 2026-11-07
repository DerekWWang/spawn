package dev.qube.spawn_9b5fbb9.content;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.LaborGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** The input/output is real persisted inventory; unfinished processing never releases a finished item. */
public class WorkstationEntity extends BlockEntity {
    public ItemStack input=ItemStack.EMPTY, output=ItemStack.EMPTY;
    public String recipe="";
    public String worker="";
    public double workRemainder;
    public long elapsed, last, required;
    public int fuel, wear;
    public WorkstationEntity(BlockPos pos,BlockState state){super(SurvivalContent.WORKSTATION_ENTITY.get(),pos,state);}
    public static void tick(Level world,BlockPos pos,BlockState state,WorkstationEntity be){
        if(!(world instanceof ServerLevel level)||level.getGameTime()%20!=0)return;
        be.advance(level,SurvivalApi.calendar(level));
    }
    public void advance(ServerLevel level,long now){
        long dt=last==0?20:Math.max(0,now-last);last=now;
        if(!input.isEmpty() && fuel>0 && required>0 && output.isEmpty()){
            double capacity=1;
            net.minecraft.world.entity.LivingEntity workerEntity=null;
            dev.qube.spawn_9b5fbb9.survival.SurvivalState workerBody=null;
            if(!worker.isEmpty()){
                java.util.UUID workerId;try{workerId=java.util.UUID.fromString(worker);}catch(IllegalArgumentException invalid){return;}
                var entity=level.getEntity(workerId);
                if(!(entity instanceof net.minecraft.world.entity.LivingEntity actor)||!actor.isAlive()||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition))>36)return;
                var body=SurvivalApi.state(actor);capacity=Math.min(body.manipulation(),body.mental());if(capacity<=.05)return;
                if(!LaborGuard.mayAdvanceForge(actor,this))return;
                dt=Math.min(dt,20);workerEntity=actor;workerBody=body;
            }
            long remainingHeat=Math.max(0,(long)Math.ceil((required-elapsed-workRemainder)/capacity));
            long used=Math.min(Math.min(dt,fuel),remainingHeat);fuel-=used;workRemainder+=used*capacity;long progress=(long)workRemainder;workRemainder-=progress;elapsed+=progress;
            if(workerBody!=null){workerBody.hunger=Math.max(0,workerBody.hunger-used*.5/1000);SurvivalApi.save(workerEntity,workerBody);if(used>0)LaborGuard.worked(workerEntity);}
            if(elapsed>=required){output=Production.finish(recipe,input,level);input=ItemStack.EMPTY;elapsed=0;required=0;wear++;}
            setChanged();
        }
    }
    @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);input=in.read("input",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);output=in.read("output",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);recipe=in.getStringOr("recipe","");worker=in.getStringOr("worker","");workRemainder=in.getDoubleOr("workRemainder",0);elapsed=in.getLongOr("elapsed",0);last=in.getLongOr("last",0);required=in.getLongOr("required",0);fuel=in.getIntOr("fuel",0);wear=in.getIntOr("wear",0);}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.store("input",ItemStack.OPTIONAL_CODEC,input);out.store("output",ItemStack.OPTIONAL_CODEC,output);out.putString("recipe",recipe);out.putString("worker",worker);out.putDouble("workRemainder",workRemainder);out.putLong("elapsed",elapsed);out.putLong("last",last);out.putLong("required",required);out.putInt("fuel",fuel);out.putInt("wear",wear);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null){Block.popResource(level,pos,input);Block.popResource(level,pos,output);input=ItemStack.EMPTY;output=ItemStack.EMPTY;}super.preRemoveSideEffects(pos,state);}
}
