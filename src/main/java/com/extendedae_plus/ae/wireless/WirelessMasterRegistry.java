package com.extendedae_plus.ae.wireless;

import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.util.WirelessTeamUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 无线主端注册中心：按 维度 + 频率 + 所有者 唯一注册一个主收发器端点。
 * 从端通过本注册中心按频率查找主端，实现一对多连接。
 * 所有者隔离：有FTBTeams时同队共享，没有时每个玩家独立。
 * 公共模式：placerId为null时使用公共UUID，所有人都能访问（向下兼容旧版本）。
 */
public final class WirelessMasterRegistry {
    private WirelessMasterRegistry() {}

    private static final Map<Key, WeakReference<IWirelessEndpoint>> MASTERS = new HashMap<>();
    
    /**
     * 公共收发器UUID（用于没有设置所有者的收发器）
     * 所有placerId为null的收发器都使用这个UUID，实现公共访问
     */
    public static final UUID PUBLIC_NETWORK_UUID = new UUID(0, 0);

    public static synchronized boolean register(ServerLevel level, long frequency, @Nullable UUID placerId, IWirelessEndpoint endpoint) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(endpoint, "endpoint");
        if (frequency == 0L) return false;
        
        // 获取网络所有者UUID
        // placerId为null时使用公共UUID（向下兼容旧版本收发器）
        UUID ownerUUID = placerId != null 
            ? WirelessTeamUtil.getNetworkOwnerUUID(level, placerId)
            : PUBLIC_NETWORK_UUID;
        
        final Key key = new Key(useGlobal() ? null : level.dimension(), frequency, ownerUUID);
        
        cleanupIfCleared(key);
        var existing = MASTERS.get(key);
        var existingVal = existing == null ? null : existing.get();
        if (existingVal != null && !existingVal.isEndpointRemoved()) {
            // 同维度同频率同所有者已经有主端
            return false;
        }
        MASTERS.put(key, new WeakReference<>(endpoint));
        return true;
    }

    public static synchronized void unregister(ServerLevel level, long frequency, @Nullable UUID placerId, IWirelessEndpoint endpoint) {
        if (frequency == 0L || level == null) return;
        
        UUID ownerUUID = placerId != null 
            ? WirelessTeamUtil.getNetworkOwnerUUID(level, placerId)
            : PUBLIC_NETWORK_UUID;
        
        final Key key = new Key(useGlobal() ? null : level.dimension(), frequency, ownerUUID);
        
        var ref = MASTERS.get(key);
        if (ref != null) {
            var cur = ref.get();
            if (cur == null || cur == endpoint) {
                MASTERS.remove(key);
            }
        }
    }

    public static synchronized IWirelessEndpoint get(ServerLevel level, long frequency, @Nullable UUID placerId) {
        if (frequency == 0L || level == null) return null;
        
        UUID ownerUUID = placerId != null 
            ? WirelessTeamUtil.getNetworkOwnerUUID(level, placerId)
            : PUBLIC_NETWORK_UUID;
        
        final Key key = new Key(useGlobal() ? null : level.dimension(), frequency, ownerUUID);
        
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
        return ModConfig.INSTANCE.wirelessCrossDimEnable;
    }

    private record Key(@Nullable ResourceKey<Level> dim, long freq, UUID owner) {
        @Override 
        public String toString() {
            return (dim == null ? "*" : dim.location().toString()) 
                + "#" + freq 
                + "@" + owner;
        }
    }
}
