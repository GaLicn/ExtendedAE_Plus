package com.extendedae_plus.ae.wireless;

import com.extendedae_plus.util.wireless.WirelessTeamUtil;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
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
    @Nullable
    private UUID registeredOwnerKey; // 实际注册时使用的所有者键

    public WirelessMasterLink(IWirelessEndpoint host) {
        this.host = host;
    }
    
    public void setPlacerId(@Nullable UUID placerId) {
        if (!Objects.equals(this.placerId, placerId)) {
            if (this.registered) {
                this.unregister();
            }
            this.placerId = placerId;
            // 所有者变更后立即按新键恢复注册。
            if (this.frequency != 0L && !this.host.isEndpointRemoved()) {
                this.register();
            }
        }
    }

    public long getFrequency() {return this.frequency;}

    public void setFrequency(long frequency) {
        // 如果频率发生变化，先撤销旧频率的注册
        if (this.frequency != frequency) {
            if (this.registered) {
                this.unregister();
            }
            this.frequency = frequency;
        }

        // 频率未变的情况下也要校正注册状态：
        // - 当从"从端"切回"主端"时，registered 可能为 false，需要重新注册；
        // - 当频率为 0 或端点被移除时，确保处于未注册。
        if (frequency != 0L && !this.host.isEndpointRemoved()) {
            if (!this.registered) {
                this.register();
            }
        } else {
            if (this.registered) {
                this.unregister();
            }
        }
    }

    public boolean register() {
        ServerLevel level = this.host.getServerLevel();
        if (level == null || this.frequency == 0L) return false;

        UUID ownerKey = this.getOwnerKey(level);
        if (this.registered && Objects.equals(this.registeredOwnerKey, ownerKey)) {
            return true;
        }
        if (this.registered) {
            this.unregister();
        }

        // 传入已解析的键，确保记录的键与注册中心使用的键一致。
        boolean ok = WirelessMasterRegistry.registerWithOwnerKey(level, this.frequency, ownerKey, this.host);
        this.registered = ok;
        this.registeredOwnerKey = ok ? ownerKey : null;
        return ok;
    }

    /**
     * 供主机 tick 调用：尝试恢复未注册状态，并处理团队所有者键变化。
     */
    public void updateStatus() {
        if (this.frequency == 0L || this.host.isEndpointRemoved()) return;

        ServerLevel level = this.host.getServerLevel();
        if (level == null) return;
        if (this.registered && !Objects.equals(this.registeredOwnerKey, this.getOwnerKey(level))) {
            this.unregister();
        }
        if (!this.registered) {
            this.register();
        }
    }

    private void unregister() {
        ServerLevel level = this.host.getServerLevel();
        if (!this.registered || level == null || this.frequency == 0L) return;
        // 使用注册时的键，避免 FTB Teams 延迟加载后注销到错误网络。
        WirelessMasterRegistry.unregisterWithOwnerKey(level, this.frequency, this.registeredOwnerKey, this.host);
        this.registered = false;
        this.registeredOwnerKey = null;
    }

    private UUID getOwnerKey(ServerLevel level) {
        return this.placerId != null
                ? WirelessTeamUtil.getNetworkOwnerUUID(level, this.placerId)
                : WirelessMasterRegistry.PUBLIC_NETWORK_UUID;
    }

    public void onUnloadOrRemove() {
        this.unregister();
    }
}
