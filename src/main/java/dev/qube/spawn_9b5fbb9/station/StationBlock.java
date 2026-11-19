package dev.qube.spawn_9b5fbb9.station;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The controller of a multiblock station or a piece of infrastructure. It does nothing on its own:
 * the structure around it must be built (see {@link StructureValidator}) before it runs.
 */
public class StationBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private final String stationId;

    public StationBlock(Properties properties, String stationId) {
        super(properties);
        this.stationId = stationId;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    public String stationId() {
        return stationId;
    }

    public TechTree.StationDef def() {
        return TechTree.get().stationsById.get(stationId);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof StationBlockEntity be) {
            be.setOperator(Society.keyOf(player));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof StationBlockEntity be) {
            sp.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel)) {
            return null;
        }
        return createTickerHelper(type, ModContent.STATION_BE.get(),
                (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl, pos, st));
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof StationBlockEntity be && be.isRunning() ? 15 : 0;
    }

    /** Describes the structure for tooltips and the station screen. */
    public static void describe(TechTree.StationDef def, Consumer<String> out) {
        switch (def.shape()) {
            case "single" -> out.accept("Stands alone: no structure needed.");
            case "area", "shaft" -> {
                if (def.note() != null) {
                    out.accept(def.note());
                }
            }
            default -> {
                StringBuilder sizes = new StringBuilder();
                for (TechTree.Tier t : def.tiers()) {
                    if (sizes.length() > 0) {
                        sizes.append(", ");
                    }
                    sizes.append(t.w()).append("w x ").append(t.h()).append("h x ").append(t.d()).append("d");
                    if (def.tiers().size() > 1) {
                        sizes.append(" (").append(t.batch()).append(t.batch() == 1 ? " batch)" : " batches)");
                    }
                }
                out.accept(("centered".equals(def.shape()) ? "Built around the controller: " : "Built behind the controller: ") + sizes);
                out.accept("Walls: " + StructureValidator.casingName(def.casing())
                        + ("air".equals(def.interior()) ? "; hollow inside" : "") + (def.openTop() ? "; open top" : "")
                        + (def.chimney() ? "; needs open sky above" : ""));
                if (def.note() != null) {
                    out.accept(def.note());
                }
            }
        }
        if (def.fuelTier() > 0) {
            out.accept("Fuel: " + (def.fuelTier() >= 2 ? "charcoal, coal or coke" : "wood or better")
                    + " unless the charge carries its own fuel.");
        }
        if ("grid".equals(def.power())) {
            out.accept("Power: must be inside Electric grid coverage.");
        } else if ("mechanical".equals(def.power())) {
            out.accept("Power: a running water wheel, windmill or engine nearby, or the grid.");
        }
    }
}
