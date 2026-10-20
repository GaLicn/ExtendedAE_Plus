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
    /**
     * 主键（物品 / 流体）→ 该主键下精确 key 到条目的映射。
     * <p>样板输出的 key 可能带有 JEI / EMI 条目所没有的数据组件（如拆装过的方块物品），
     * 此时精确查找会落空，需要按主键回退到同物品的其它变体。</p>
     */
    private final Map<Object, Map<AEKey, CacheEntry>> byPrimaryKey = new HashMap<>();
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
                    removeByPrimaryKey(removed);
                }
            } else if (entry.key() != null) {
                CacheEntry updated = new CacheEntry(entry.key(), entry.amount(), entry.craftable());
                CacheEntry previous = bySerial.put(entry.serial(), updated);
                if (previous != null) {
                    keyToSerial.remove(previous.key);
                    removeByPrimaryKey(previous);
                }
                keyToSerial.put(entry.key(), entry.serial());
                byPrimaryKey.computeIfAbsent(entry.key().getPrimaryKey(), k -> new HashMap<>())
                        .put(entry.key(), updated);
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
        CacheEntry entry = find(key);
        return entry == null ? 0 : entry.amount;
    }

    public boolean isCraftable(AEKey key) {
        CacheEntry entry = find(key);
        return entry != null && entry.craftable;
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

    /**
     * 先按精确 key 查找；未命中时回退到同一主键下的变体，可合成的变体优先。
     */
    private CacheEntry find(AEKey key) {
        Long serial = keyToSerial.get(key);
        if (serial != null) {
            CacheEntry entry = bySerial.get(serial.longValue());
            if (entry != null) {
                return entry;
            }
        }

        Map<AEKey, CacheEntry> variants = byPrimaryKey.get(key.getPrimaryKey());
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        CacheEntry first = null;
        for (CacheEntry variant : variants.values()) {
            if (variant.craftable) {
                return variant;
            }
            if (first == null) {
                first = variant;
            }
        }
        return first;
    }

    private void removeByPrimaryKey(CacheEntry entry) {
        Object primaryKey = entry.key.getPrimaryKey();
        Map<AEKey, CacheEntry> variants = byPrimaryKey.get(primaryKey);
        if (variants == null) {
            return;
        }
        variants.remove(entry.key);
        if (variants.isEmpty()) {
            byPrimaryKey.remove(primaryKey);
        }
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
