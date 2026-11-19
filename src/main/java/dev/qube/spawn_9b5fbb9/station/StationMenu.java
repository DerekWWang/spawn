package dev.qube.spawn_9b5fbb9.station;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.knowledge.Requirements;
import dev.qube.spawn_9b5fbb9.network.Net;
import dev.qube.spawn_9b5fbb9.network.Payloads;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The station screen's slots. Selecting a function is a menu button click whose id is the function index. */
public class StationMenu extends AbstractContainerMenu {
    public static final int CLEAR_SELECTION = 9999;
    public static final int WIDTH = 336;
    public static final int HEIGHT = 222;
    public static final int INPUT_X = 136;
    public static final int TOOL_X = 236;
    public static final int OUTPUT_X = 292;
    public static final int SLOT_Y = 18;
    public static final int FUEL_X = 236;
    public static final int FUEL_Y = 58;
    public static final int INV_X = 88;
    public static final int INV_Y = 140;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;
    private final String stationId;
    private final Player player;
    private final @Nullable StationBlockEntity station;
    private List<String> lastSent = List.of();
    private int statusTimer;

    public StationMenu(int id, Inventory inventory, Container container, ContainerData data, BlockPos pos, String stationId) {
        super(ModContent.STATION_MENU.get(), id);
        checkContainerSize(container, StationBlockEntity.SIZE);
        checkContainerDataCount(data, StationBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.pos = pos;
        this.stationId = stationId;
        this.player = inventory.player;
        this.station = container instanceof StationBlockEntity be ? be : null;
        container.startOpen(inventory.player);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                addSlot(new Slot(container, StationBlockEntity.INPUT_START + row * 5 + col, INPUT_X + col * 18, SLOT_Y + row * 18));
            }
        }
        for (int i = 0; i < StationBlockEntity.TOOLS; i++) {
            addSlot(new Slot(container, StationBlockEntity.TOOL_START + i, TOOL_X + (i % 2) * 18, SLOT_Y + (i / 2) * 18));
        }
        addSlot(new Slot(container, StationBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return StationBlockEntity.acceptsFuel(def(), stack);
            }
        });
        for (int i = 0; i < StationBlockEntity.OUTPUTS; i++) {
            addSlot(new Slot(container, StationBlockEntity.OUTPUT_START + i, OUTPUT_X + (i % 2) * 18, SLOT_Y + (i / 2) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addStandardInventorySlots(inventory, INV_X, INV_Y);
        addDataSlots(data);
    }

    public static StationMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Level level = inventory.player.level();
        String stationId = level.getBlockState(pos).getBlock() instanceof StationBlock sb ? sb.stationId() : "";
        return new StationMenu(id, inventory, new SimpleContainer(StationBlockEntity.SIZE),
                new SimpleContainerData(StationBlockEntity.DATA_COUNT), pos, stationId);
    }

    public String stationId() {
        return stationId;
    }

    public BlockPos pos() {
        return pos;
    }

    public TechTree.@Nullable StationDef def() {
        return TechTree.get().stationsById.get(stationId);
    }

    public int data(int index) {
        return data.get(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (station == null || !(player instanceof ServerPlayer sp)) {
            return true;
        }
        String msg = station.select(sp, id == CLEAR_SELECTION ? -1 : id);
        if (msg != null) {
            sp.sendOverlayMessage(Component.literal(msg));
        }
        sendStatus(true);
        return true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (++statusTimer >= 10) {
            statusTimer = 0;
            sendStatus(false);
        }
    }

    private void sendStatus(boolean force) {
        if (station == null || !(player instanceof ServerPlayer sp)) {
            return;
        }
        List<String> lines = station.statusLines();
        if (force || !lines.equals(lastSent)) {
            lastSent = lines;
            Net.send(sp, new Payloads.StationStatus(containerId, lines));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = StationBlockEntity.SIZE;
        if (index < size) {
            if (!moveItemStackTo(stack, size, size + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (!Requirements.isWaterContainer(stack) && StationBlockEntity.acceptsFuel(def(), stack)) {
                moved = moveItemStackTo(stack, StationBlockEntity.FUEL_SLOT, StationBlockEntity.FUEL_SLOT + 1, false);
            }
            if (!moved && isToolForSelected(stack)) {
                moved = moveItemStackTo(stack, StationBlockEntity.TOOL_START, StationBlockEntity.TOOL_START + StationBlockEntity.TOOLS, false);
            }
            if (!moved && !moveItemStackTo(stack, StationBlockEntity.INPUT_START, StationBlockEntity.INPUT_START + StationBlockEntity.INPUTS, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    /** True if the selected function uses this item as a means of production (a tool, not an input). */
    private boolean isToolForSelected(ItemStack stack) {
        TechTree.FunctionDef f = TechTree.get().function(data.get(StationBlockEntity.DATA_SELECTED));
        if (f == null) {
            return false;
        }
        for (TechTree.Ref m : f.means()) {
            if ((m.kind() == TechTree.RefKind.ITEM || m.kind() == TechTree.RefKind.TAG) && Requirements.matcher(m).test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
