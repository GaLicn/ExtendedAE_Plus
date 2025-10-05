package com.extendedae_plus.wireless;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 主收发器端逻辑：负责在频率变化/加载时向注册中心登记唯一主端，卸载时反注册。
 * 方块实体应在合适的生命周期中调用 register/unregister。
 */
public class WirelessMasterLink {
    private final IWirelessEndpoint host;
    private long frequency; // 0 为未设置
    private boolean registered;
    @Nullable
    private UUID placerId; // 放置者UUID

    public WirelessMasterLink(IWirelessEndpoint host) {
        this.host = host;
    }
    
    public void setPlacerId(@Nullable UUID placerId) {
        this.placerId = placerId;
    }

    public long getFrequency() { return frequency; }

    public void setFrequency(long frequency) {
        // 如果频率发生变化，先撤销旧频率的注册
        if (this.frequency != frequency) {
            if (registered) {
                unregister();
            }
            this.frequency = frequency;
        }

        // 频率未变的情况下也要校正注册状态：
        // - 当从"从端"切回"主端"时，registered 可能为 false，需要重新注册；
        // - 当频率为 0 或端点被移除时，确保处于未注册。
        if (frequency != 0L && !host.isEndpointRemoved()) {
            if (!registered) {
                register();
            }
        } else {
            if (registered) {
                unregister();
            }
        }
    }

    public boolean register() {
        ServerLevel level = host.getServerLevel();
        if (level == null || frequency == 0L) return false;
        // placerId可以为null（公共收发器模式）
        boolean ok = WirelessMasterRegistry.register(level, frequency, placerId, host);
        this.registered = ok;
        return ok;
    }

    public void unregister() {
        ServerLevel level = host.getServerLevel();
        if (!registered || level == null || frequency == 0L) return;
        // placerId可以为null（公共收发器模式）
        WirelessMasterRegistry.unregister(level, frequency, placerId, host);
        registered = false;
    }

    public void onUnloadOrRemove() {
        unregister();
    }
}
