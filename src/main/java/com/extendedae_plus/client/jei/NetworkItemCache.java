package com.extendedae_plus.client.jei;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.network.jei.SyncNetworkInventoryS2CPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NetworkItemCache {
    public static final NetworkItemCache INSTANCE = new NetworkItemCache();

    private final Long2ObjectOpenHashMap<CacheEntry> bySerial = new Long2ObjectOpenHashMap<>();
    private final Map<AEKey, Long> keyToSerial = new HashMap<>();
    private boolean connected;

    private NetworkItemCache() {
    }

    public void handleUpdate(boolean fullUpdate, List<SyncNetworkInventoryS2CPacket.Entry> entries) {
        if (fullUpdate) {
            clear();
        }
        for (var entry : entries) {
            if (entry.amount() == 0 && !entry.craftable()) {
                CacheEntry removed = bySerial.remove(entry.serial());
                if (removed != null) {
                    keyToSerial.remove(removed.key);
                }
            } else if (entry.key() != null) {
                CacheEntry previous = bySerial.put(entry.serial(), new CacheEntry(entry.key(), entry.amount(), entry.craftable()));
                if (previous != null) {
                    keyToSerial.remove(previous.key);
                }
                keyToSerial.put(entry.key(), entry.serial());
            } else {
                CacheEntry existing = bySerial.get(entry.serial());
                if (existing != null) {
                    existing.amount = entry.amount();
                    existing.craftable = entry.craftable();
                }
            }
        }
        connected = true;
    }

    public long getAmount(AEKey key) {
        Long serial = keyToSerial.get(key);
        CacheEntry entry = serial == null ? null : bySerial.get(serial.longValue());
        return entry == null ? 0 : entry.amount;
    }

    public boolean isCraftable(AEKey key) {
        Long serial = keyToSerial.get(key);
        CacheEntry entry = serial == null ? null : bySerial.get(serial.longValue());
        return entry != null && entry.craftable;
    }

    public boolean isConnected() {
        return connected;
    }

    public void clear() {
        bySerial.clear();
        keyToSerial.clear();
        connected = false;
    }

    private static final class CacheEntry {
        private final AEKey key;
        private long amount;
        private boolean craftable;

        private CacheEntry(AEKey key, long amount, boolean craftable) {
            this.key = key;
            this.amount = amount;
            this.craftable = craftable;
        }
    }
}
