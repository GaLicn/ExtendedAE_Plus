package com.extendedae_plus.server;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.extendedae_plus.network.jei.SyncNetworkInventoryS2CPacket;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JeiSyncManager {
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final int MAX_ENTRIES_PER_PACKET = 8192;
    private static final Map<UUID, PlayerSyncState> PLAYER_STATES = new HashMap<>();

    private JeiSyncManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerSyncState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState());
        if (++state.tickCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        state.tickCounter = 0;

        var terminal = WirelessTerminalLocator.find(player);
        IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, terminal);
        if (grid == null) {
            if (!state.disconnectedStateSent) {
                // 新存档首次进入时也要清空客户端缓存，避免残留上一个存档的网络库存。
                sendClear(player);
                state.reset();
                state.disconnectedStateSent = true;
            }
            return;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        ICraftingService crafting = grid.getCraftingService();
        KeyCounter availableStacks = storage.getAvailableStacks();
        Set<AEKey> craftables = crafting.getCraftables(key -> true);
        Map<AEKey, Long> amounts = new HashMap<>();
        for (var entry : availableStacks) {
            amounts.put(entry.getKey(), entry.getLongValue());
        }
        Set<AEKey> currentKeys = new HashSet<>(amounts.keySet());
        currentKeys.addAll(craftables);

        boolean fullUpdate = !state.wasConnected;
        List<SyncNetworkInventoryS2CPacket.Entry> entries = fullUpdate
                ? buildFullUpdate(state, currentKeys, amounts, craftables)
                : buildDiff(state, currentKeys, amounts, craftables);
        sendEntries(player, fullUpdate, entries);
        state.wasConnected = true;
        state.disconnectedStateSent = false;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_STATES.remove(event.getEntity().getUUID());
    }

    private static List<SyncNetworkInventoryS2CPacket.Entry> buildFullUpdate(PlayerSyncState state, Set<AEKey> keys,
                                                                               Map<AEKey, Long> amounts, Set<AEKey> craftables) {
        state.resetSnapshot();
        List<SyncNetworkInventoryS2CPacket.Entry> entries = new ArrayList<>(keys.size());
        for (AEKey key : keys) {
            long amount = amounts.getOrDefault(key, 0L);
            boolean craftable = craftables.contains(key);
            long serial = state.nextSerial++;
            state.serials.put(key, serial);
            state.amounts.put(key, amount);
            if (craftable) {
                state.craftables.add(key);
            }
            entries.add(new SyncNetworkInventoryS2CPacket.Entry(serial, key, amount, craftable));
        }
        return entries;
    }

    private static List<SyncNetworkInventoryS2CPacket.Entry> buildDiff(PlayerSyncState state, Set<AEKey> keys,
                                                                         Map<AEKey, Long> amounts, Set<AEKey> craftables) {
        List<SyncNetworkInventoryS2CPacket.Entry> entries = new ArrayList<>();
        for (AEKey key : keys) {
            long amount = amounts.getOrDefault(key, 0L);
            boolean craftable = craftables.contains(key);
            Long serial = state.serials.get(key);
            if (serial == null) {
                serial = state.nextSerial++;
                state.serials.put(key, serial);
                entries.add(new SyncNetworkInventoryS2CPacket.Entry(serial, key, amount, craftable));
            } else if (state.amounts.getOrDefault(key, 0L) != amount || state.craftables.contains(key) != craftable) {
                entries.add(new SyncNetworkInventoryS2CPacket.Entry(serial, null, amount, craftable));
            }
            state.amounts.put(key, amount);
            if (craftable) {
                state.craftables.add(key);
            } else {
                state.craftables.remove(key);
            }
        }

        Iterator<Map.Entry<AEKey, Long>> iterator = state.serials.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!keys.contains(entry.getKey())) {
                entries.add(new SyncNetworkInventoryS2CPacket.Entry(entry.getValue(), null, 0, false));
                state.amounts.remove(entry.getKey());
                state.craftables.remove(entry.getKey());
                iterator.remove();
            }
        }
        return entries;
    }

    private static void sendEntries(ServerPlayer player, boolean fullUpdate, List<SyncNetworkInventoryS2CPacket.Entry> entries) {
        if (entries.isEmpty()) {
            if (fullUpdate) {
                sendClear(player);
            }
            return;
        }
        for (int start = 0; start < entries.size(); start += MAX_ENTRIES_PER_PACKET) {
            int end = Math.min(start + MAX_ENTRIES_PER_PACKET, entries.size());
            PacketDistributor.sendToPlayer(player, new SyncNetworkInventoryS2CPacket(fullUpdate && start == 0, List.copyOf(entries.subList(start, end))));
        }
    }

    private static void sendClear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncNetworkInventoryS2CPacket(true, List.of()));
    }

    private static final class PlayerSyncState {
        private int tickCounter;
        private boolean wasConnected;
        private boolean disconnectedStateSent;
        private long nextSerial = 1;
        private final Map<AEKey, Long> serials = new HashMap<>();
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final Set<AEKey> craftables = new HashSet<>();

        private void reset() {
            wasConnected = false;
            resetSnapshot();
        }

        private void resetSnapshot() {
            serials.clear();
            amounts.clear();
            craftables.clear();
            nextSerial = 1;
        }
    }
}
