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
    /** 同一主键的缓存条目索引，仅用于数据差异下的可合成状态查询。 */
    private final Map<Object, Map<AEKey, CacheEntry>> byPrimaryKey = new HashMap<>();
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
                    removeByPrimaryKey(existing);
                }
            } else if (entry.what != null) {
                // new key or full update entry
                CacheEntry ce = new CacheEntry(entry.what, entry.amount, entry.craftable);
                CacheEntry prev = bySerial.put(entry.serial, ce);
                if (prev != null) {
                    keyToSerial.remove(prev.what);
                    removeByPrimaryKey(prev);
                }
                keyToSerial.put(entry.what, entry.serial);
                byPrimaryKey.computeIfAbsent(entry.what.getPrimaryKey(), k -> new HashMap<>())
                        .put(entry.what, ce);
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
        CacheEntry entry = serial == null ? null : bySerial.get(serial.longValue());
        return entry != null ? entry.amount : 0;
    }

    public boolean isCraftable(AEKey key) {
        Long serial = keyToSerial.get(key);
        CacheEntry entry = serial == null ? null : bySerial.get(serial.longValue());
        if (entry != null && entry.craftable) return true;
        Map<AEKey, CacheEntry> variants = byPrimaryKey.get(key.getPrimaryKey());
        if (variants == null) return false;
        for (CacheEntry variant : variants.values()) {
            if (variant.craftable) return true;
        }
        return false;
    }

    public boolean isConnected() {
        return connected;
    }

    public void clear() {
        bySerial.clear();
        keyToSerial.clear();
        byPrimaryKey.clear();
        connected = false;
    }

    private void removeByPrimaryKey(CacheEntry entry) {
        Object primaryKey = entry.what.getPrimaryKey();
        Map<AEKey, CacheEntry> variants = byPrimaryKey.get(primaryKey);
        if (variants == null) {
            return;
        }
        variants.remove(entry.what);
        if (variants.isEmpty()) {
            byPrimaryKey.remove(primaryKey);
        }
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
