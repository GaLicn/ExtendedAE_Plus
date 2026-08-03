package com.extendedae_plus.client.jei;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.network.jei.SyncNetworkInventoryS2CPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkItemCache {

    public static final NetworkItemCache INSTANCE = new NetworkItemCache();

    private final Long2ObjectOpenHashMap<CacheEntry> bySerial = new Long2ObjectOpenHashMap<>();
    private final Map<AEKey, Long> keyToSerial = new HashMap<>();
    private volatile boolean connected = false;

    public void handleUpdate(boolean fullUpdate, List<SyncNetworkInventoryS2CPacket.Entry> entries) {
        if (fullUpdate) {
            clear();
        }
        for (var entry : entries) {
            if (entry.amount == 0 && !entry.craftable) {
                // removal
                CacheEntry existing = bySerial.remove(entry.serial);
                if (existing != null) {
                    keyToSerial.remove(existing.what);
                }
            } else if (entry.what != null) {
                // new key or full update entry
                CacheEntry prev = bySerial.get(entry.serial);
                if (prev != null && prev.what != null) {
                    keyToSerial.remove(prev.what);
                }
                CacheEntry ce = new CacheEntry(entry.what, entry.amount, entry.craftable);
                bySerial.put(entry.serial, ce);
                keyToSerial.put(entry.what, entry.serial);
            } else {
                // incremental update (key already known by serial)
                CacheEntry existing = bySerial.get(entry.serial);
                if (existing != null) {
                    existing.amount = entry.amount;
                    existing.craftable = entry.craftable;
                }
            }
        }
        connected = true;
    }

    public long getAmount(AEKey key) {
        Long serial = keyToSerial.get(key);
        if (serial == null) return 0;
        CacheEntry entry = bySerial.get(serial.longValue());
        return entry != null ? entry.amount : 0;
    }

    public boolean isCraftable(AEKey key) {
        Long serial = keyToSerial.get(key);
        if (serial == null) return false;
        CacheEntry entry = bySerial.get(serial.longValue());
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

    public static class CacheEntry {
        public final AEKey what;
        public long amount;
        public boolean craftable;

        public CacheEntry(AEKey what, long amount, boolean craftable) {
            this.what = what;
            this.amount = amount;
            this.craftable = craftable;
        }
    }
}
