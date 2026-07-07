package com.extendedae_plus.menu;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.network.SuperAssemblerMatrixStatsS2CPacket;
import com.extendedae_plus.network.SuperAssemblerMatrixUpdateS2CPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SuperAssemblerMatrixMenu extends AEBaseMenu {

    private final List<PatternSlotTracker> trackers = new ArrayList<>();
    private final Long2ReferenceMap<PatternSlotTracker> trackerMap = new Long2ReferenceOpenHashMap<>();
    private final SuperAssemblerMatrixBlockEntity host;
    private long concurrentExecutions = -1;
    private int statsSyncTicks;

    public SuperAssemblerMatrixMenu(int id, Inventory playerInventory, SuperAssemblerMatrixBlockEntity host) {
        super(ModMenuTypes.SUPER_ASSEMBLER_MATRIX.get(), id, playerInventory, host);
        this.host = host;
        this.setupPatternInventory();
        this.createPlayerInventorySlots(playerInventory);
    }

    public void handleAction(String action) {
        var cluster = this.host.eap$getSuperMatrixCluster();
        if ("cancel".equals(action) && cluster != null) {
            cluster.cancelWork();
        }
    }

    @Override
    protected int transferStackToMenu(ItemStack input) {
        var slot = this.getAvailableSlot();
        if (slot != null) {
            return input.getCount() - slot.addItems(input).getCount();
        }
        return 0;
    }

    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        var tracker = this.trackerMap.get(id);
        if (tracker == null || slot < 0 || slot >= tracker.server.size()) {
            return;
        }

        var patternSlot = new FilteredInternalInventory(
                tracker.server.getSlotInv(slot),
                new PatternCorePlusBlockEntity.Filter(() -> this.host.getLevel())
        );
        var stackInSlot = tracker.server.getStackInSlot(slot);
        var carried = this.getCarried();

        switch (action) {
            case PICKUP_OR_SET_DOWN -> {
                if (!carried.isEmpty()) {
                    var existing = patternSlot.getStackInSlot(0);
                    if (existing.isEmpty()) {
                        this.setCarried(patternSlot.addItems(carried));
                    } else {
                        var oldSlot = existing.copy();
                        var oldHand = carried.copy();
                        patternSlot.setItemDirect(0, ItemStack.EMPTY);
                        this.setCarried(ItemStack.EMPTY);
                        this.setCarried(patternSlot.addItems(oldHand.copy()));
                        if (this.getCarried().isEmpty()) {
                            this.setCarried(oldSlot);
                        } else {
                            this.setCarried(oldHand);
                            patternSlot.setItemDirect(0, oldSlot);
                        }
                    }
                } else {
                    this.setCarried(patternSlot.getStackInSlot(0));
                    patternSlot.setItemDirect(0, ItemStack.EMPTY);
                }
            }
            case SPLIT_OR_PLACE_SINGLE -> {
                if (!carried.isEmpty()) {
                    var single = carried.split(1);
                    if (!single.isEmpty()) {
                        single = patternSlot.addItems(single);
                    }
                    if (!single.isEmpty()) {
                        carried.grow(single.getCount());
                    }
                } else if (!stackInSlot.isEmpty()) {
                    this.setCarried(patternSlot.extractItem(0, (stackInSlot.getCount() + 1) / 2, false));
                }
            }
            case PICKUP_SINGLE -> {
                if (carried.isEmpty() && !stackInSlot.isEmpty()) {
                    this.setCarried(patternSlot.extractItem(0, 1, false));
                }
            }
            case SHIFT_CLICK -> {
                var stack = patternSlot.getStackInSlot(0).copy();
                if (!player.getInventory().add(stack)) {
                    patternSlot.setItemDirect(0, stack);
                } else {
                    patternSlot.setItemDirect(0, ItemStack.EMPTY);
                }
            }
            case MOVE_REGION -> {
                for (int i = 0; i < tracker.server.size(); i++) {
                    var regionSlot = new FilteredInternalInventory(
                            tracker.server.getSlotInv(i),
                            new PatternCorePlusBlockEntity.Filter(() -> this.host.getLevel())
                    );
                    var stack = tracker.server.getStackInSlot(i);
                    if (!player.getInventory().add(stack)) {
                        regionSlot.setItemDirect(0, stack);
                    } else {
                        regionSlot.setItemDirect(0, ItemStack.EMPTY);
                    }
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.getAbilities().instabuild && carried.isEmpty()) {
                    this.setCarried(stackInSlot.isEmpty() ? ItemStack.EMPTY : stackInSlot.copy());
                }
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.isClientSide()) {
            return;
        }
        super.broadcastChanges();
        if (this.getPlayer() instanceof ServerPlayer player) {
            for (var tracker : this.trackers) {
                if (tracker.initialized) {
                    var packet = tracker.createPacket();
                    if (packet != null) {
                        player.connection.send(packet);
                    }
                } else {
                    tracker.initialized = true;
                    player.connection.send(tracker.fullPacket());
                }
            }
            var cluster = this.host.eap$getSuperMatrixCluster();
            long current = cluster == null ? 0 : cluster.getConcurrentExecutions();
            // 并发执行数可能只持续很短时间，给 UI 一个轻量心跳避免错过瞬时变化。
            if (this.concurrentExecutions != current || ++this.statsSyncTicks >= 5) {
                this.concurrentExecutions = current;
                this.statsSyncTicks = 0;
                player.connection.send(new SuperAssemblerMatrixStatsS2CPacket(current));
            }
        }
    }

    @Nullable
    private InternalInventory getAvailableSlot() {
        for (long id : this.trackerMap.keySet().longStream().sorted().toArray()) {
            var tracker = this.trackerMap.get(id);
            for (int i = 0; i < tracker.server.size(); i++) {
                if (tracker.server.getStackInSlot(i).isEmpty()) {
                    return new FilteredInternalInventory(
                            tracker.server.getSlotInv(i),
                            new PatternCorePlusBlockEntity.Filter(() -> this.host.getLevel())
                    );
                }
            }
        }
        return null;
    }

    private void setupPatternInventory() {
        if (this.isClientSide()) {
            return;
        }
        this.trackers.clear();
        this.trackerMap.clear();
        var cluster = this.host.eap$getSuperMatrixCluster();
        if (cluster == null || cluster.isDestroyed()) {
            return;
        }
        for (var patternCore : cluster.getPatternCores()) {
            var tracker = new PatternSlotTracker(patternCore);
            this.trackers.add(tracker);
            this.trackerMap.put(patternCore.getLocateID(), tracker);
        }
    }

    private static class PatternSlotTracker {
        private final PatternCorePlusBlockEntity host;
        private final InternalInventory client;
        private final InternalInventory server;
        private final Int2ObjectMap<ItemStack> changed = new Int2ObjectOpenHashMap<>();
        private boolean initialized;

        private PatternSlotTracker(PatternCorePlusBlockEntity host) {
            this.host = host;
            this.client = new AppEngInternalInventory(host.getPatternInventory().size());
            this.server = host.getPatternInventory();
        }

        @Nullable
        private SuperAssemblerMatrixUpdateS2CPacket createPacket() {
            var map = this.getChangedMap();
            return map.isEmpty() ? null : new SuperAssemblerMatrixUpdateS2CPacket(
                    this.host.getLocateID(), this.server.size(), map);
        }

        private SuperAssemblerMatrixUpdateS2CPacket fullPacket() {
            return new SuperAssemblerMatrixUpdateS2CPacket(this.host.getLocateID(), this.server.size(),
                    this.getFullMap());
        }

        private Int2ObjectMap<ItemStack> getChangedMap() {
            this.changed.clear();
            for (int i = 0; i < this.server.size(); i++) {
                var serverStack = this.server.getStackInSlot(i);
                var clientStack = this.client.getStackInSlot(i);
                if (!ItemStack.isSameItemSameComponents(serverStack, clientStack)) {
                    this.changed.put(i, serverStack.copy());
                    this.client.setItemDirect(i, serverStack.copy());
                }
            }
            return this.changed;
        }

        private Int2ObjectMap<ItemStack> getFullMap() {
            this.changed.clear();
            for (int i = 0; i < this.server.size(); i++) {
                this.changed.put(i, this.server.getStackInSlot(i).copy());
            }
            return this.changed;
        }
    }
}
