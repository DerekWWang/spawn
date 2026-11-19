package dev.qube.spawn_9b5fbb9.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Standard-gauge permanent way. One metre of line is three blocks wide: a centre block carrying the
 * sleepers and a rail-bearing shoulder on each side. It is laid by timed labour, never placed by hand. */
public class RailTrackBlock extends Block {
    public enum Part implements StringRepresentable {
        CENTER("center"),NEG("neg"),POS("pos");
        private final String name;
        Part(String name){this.name=name;}
        @Override public String getSerializedName(){return name;}
    }
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<Part> PART=EnumProperty.create("part",Part.class);
    private static final VoxelShape SHAPE=Block.box(0,0,0,16,2,16);
    public RailTrackBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(AXIS,Direction.Axis.X).setValue(PART,Part.CENTER));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(AXIS,PART);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    /** The direction from a shoulder back to its centre block. */
    public static Direction inward(BlockState state){
        boolean alongX=state.getValue(AXIS)==Direction.Axis.X;
        return switch(state.getValue(PART)){case NEG->alongX?Direction.SOUTH:Direction.EAST;case POS->alongX?Direction.NORTH:Direction.WEST;default->Direction.UP;};
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return false;
        if(state.getValue(PART)==Part.CENTER)return true;
        BlockState centre=level.getBlockState(pos.relative(inward(state)));
        return centre.getBlock()==this&&centre.getValue(PART)==Part.CENTER;
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos neighborPos,BlockState neighborState,RandomSource random){
        return canSurvive(state,level,pos)?state:Blocks.AIR.defaultBlockState();
    }
}
