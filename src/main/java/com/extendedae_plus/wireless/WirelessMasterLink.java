package com.extendedae_plus.wireless;

import net.minecraft.server.level.ServerLevel;

/**
 * 主收发器端逻辑：负责在频率变化/加载时向注册中心登记唯一主端，卸载时反注册。
 * 方块实体应在合适的生命周期中调用 register/unregister。
 */
public class WirelessMasterLink {
    private final IWirelessEndpoint host;
    private long frequency; // 0 为未设置
    private boolean registered;

    public WirelessMasterLink(IWirelessEndpoint host) {
        this.host = host;
    }

    public long getFrequency() { return frequency; }

    public void setFrequency(long frequency) {
        if (this.frequency == frequency) return;
        // 先反注册旧频率
        if (registered) {
            unregister();
        }
        this.frequency = frequency;
        // 再尝试注册新频率
        if (frequency != 0L && !host.isEndpointRemoved()) {
            register();
        }
    }

    public boolean register() {
        ServerLevel level = host.getServerLevel();
        if (level == null || frequency == 0L) return false;
        boolean ok = WirelessMasterRegistry.register(level, frequency, host);
        this.registered = ok;
        return ok;
    }

    public void unregister() {
        ServerLevel level = host.getServerLevel();
        if (!registered || level == null || frequency == 0L) return;
        WirelessMasterRegistry.unregister(level, frequency, host);
        registered = false;
    }

    public void onUnloadOrRemove() {
        unregister();
    }
}
