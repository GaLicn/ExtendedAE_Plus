package com.extendedae_plus.wireless;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.extendedae_plus.config.ModConfigs;

/**
 * 无线主端注册中心：按 维度 + 频率 唯一注册一个主收发器端点。
 * 从端通过本注册中心按频率查找主端，实现一对多连接。
 */
public final class WirelessMasterRegistry {
    private WirelessMasterRegistry() {}

    private static final Map<Key, WeakReference<IWirelessEndpoint>> MASTERS = new HashMap<>();

    public static synchronized boolean register(ServerLevel level, long frequency, IWirelessEndpoint endpoint) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(endpoint, "endpoint");
        if (frequency == 0L) return false;
        final Key key = new Key(useGlobal() ? null : level.dimension(), frequency);
        cleanupIfCleared(key);
        var existing = MASTERS.get(key);
        var existingVal = existing == null ? null : existing.get();
        if (existingVal != null && !existingVal.isEndpointRemoved()) {
            // 同维度同频率已经有主端
            return false;
        }
        MASTERS.put(key, new WeakReference<>(endpoint));
        return true;
    }

    public static synchronized void unregister(ServerLevel level, long frequency, IWirelessEndpoint endpoint) {
        if (frequency == 0L || level == null) return;
        final Key key = new Key(useGlobal() ? null : level.dimension(), frequency);
        var ref = MASTERS.get(key);
        if (ref != null) {
            var cur = ref.get();
            if (cur == null || cur == endpoint) {
                MASTERS.remove(key);
            }
        }
    }

    public static synchronized IWirelessEndpoint get(ServerLevel level, long frequency) {
        if (frequency == 0L || level == null) return null;
        final Key key = new Key(useGlobal() ? null : level.dimension(), frequency);
        cleanupIfCleared(key);
        var ref = MASTERS.get(key);
        return ref == null ? null : ref.get();
    }

    private static void cleanupIfCleared(Key key) {
        var ref = MASTERS.get(key);
        if (ref != null && ref.get() == null) {
            MASTERS.remove(key);
        }
    }

    private static boolean useGlobal() {
        return ModConfigs.WIRELESS_CROSS_DIM_ENABLE.get();
    }

    private record Key(ResourceKey<Level> dim, long freq) {
        @Override public String toString() {
            return (dim == null ? "*" : dim.location().toString()) + "#" + freq;
        }
    }
}
