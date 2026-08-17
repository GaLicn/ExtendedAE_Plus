package com.extendedae_plus.util.wireless;

import appeng.api.upgrades.IUpgradeInventory;
import com.extendedae_plus.ae.wireless.IWirelessEndpoint;
import com.extendedae_plus.ae.wireless.WirelessSlaveLink;
import java.util.UUID;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 频道卡从端的统一生命周期控制器。
 * 宿主只需提供升级槽、无线端点和节点唤醒回调，连接状态不再散落在各个 Mixin 中。
 */
public final class ChannelCardConnectionController {
    private static final Map<BlockEntity, Set<ChannelCardConnectionController>> REGISTRY =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private final Supplier<IUpgradeInventory> upgrades;
    private final Supplier<UUID> fallbackOwner;
    private final Supplier<IWirelessEndpoint> endpoint;
    private final Runnable stateChanged;
    private final Runnable wakeNode;
    private final BooleanSupplier clientSide;

    @Nullable
    private WirelessSlaveLink link;
    private long lastChannel = -1L;
    @Nullable
    private UUID lastOwner;
    private boolean initialized;
    private boolean clientConnected;
    private boolean closed;

    public ChannelCardConnectionController(
            Supplier<IUpgradeInventory> upgrades,
            Supplier<UUID> fallbackOwner,
            Supplier<IWirelessEndpoint> endpoint,
            Runnable stateChanged,
            Runnable wakeNode,
            BooleanSupplier clientSide) {
        this.upgrades = upgrades;
        this.fallbackOwner = fallbackOwner;
        this.endpoint = endpoint;
        this.stateChanged = stateChanged;
        this.wakeNode = wakeNode;
        this.clientSide = clientSide;
    }

    /** 升级槽变化后立即失效旧目标，并唤醒宿主节点。 */
    public void onUpgradesChanged() {
        resetTarget();
        initialize();
        wakeNode.run();
    }

    /** AE 节点重建后必须重新寻找目标。 */
    public void onNodeChanged() {
        resetTarget();
        wakeNode.run();
    }

    /** NBT 加载或部件重新加入世界后调用。 */
    public void onLoaded() {
        closed = false;
        resetTarget();
        initialize();
        wakeNode.run();
    }

    /** 区块卸载、部件移除或方块实体销毁时调用。 */
    public void onUnloaded() {
        closed = true;
        initialized = true;
        disconnect();
    }

    /** 将逻辑控制器绑定到实际方块实体，供 AE2 统一卸载回调清理。 */
    public static void register(BlockEntity owner, ChannelCardConnectionController controller) {
        synchronized (REGISTRY) {
            REGISTRY.computeIfAbsent(owner, ignored ->
                    Collections.newSetFromMap(new IdentityHashMap<>())).add(controller);
        }
    }

    /** 方块实体卸载时一次性关闭该实体创建的全部无线连接。 */
    public static void unloadFor(BlockEntity owner) {
        Set<ChannelCardConnectionController> controllers;
        synchronized (REGISTRY) {
            controllers = REGISTRY.remove(owner);
        }
        if (controllers != null) {
            for (var controller : controllers) {
                controller.onUnloaded();
            }
        }
    }

    /** 由宿主 ticker 调用，负责首次初始化和连接保活。 */
    public void tick() {
        if (closed || clientSide.getAsBoolean()) {
            return;
        }
        if (!initialized || targetChanged()) {
            initialize();
        } else {
            updateWirelessLink();
        }
    }

    public void initialize() {
        if (closed || clientSide.getAsBoolean()) {
            return;
        }

        var target = ChannelCardLinkHelper.findBoundChannel(upgrades.get(), fallbackOwner);
        if (target == null) {
            disconnect();
            lastChannel = 0L;
            lastOwner = null;
            initialized = true;
            return;
        }

        if (link == null) {
            link = new WirelessSlaveLink(endpoint.get());
        }
        if (!targetChanged(target)) {
            link.updateStatus();
            initialized = link.isConnected();
            return;
        }

        link.setPlacerId(target.owner());
        link.setFrequency(target.channel());
        link.updateStatus();
        lastChannel = target.channel();
        lastOwner = target.owner();
        initialized = link.isConnected();
        stateChanged.run();
    }

    public boolean shouldKeepTicking() {
        return !initialized || ChannelCardLinkHelper.hasChannelCard(upgrades.get())
                || ChannelCardLinkHelper.hasActiveLink(link);
    }

    public void updateWirelessLink() {
        if (closed || clientSide.getAsBoolean() || link == null) {
            return;
        }
        boolean before = link.isConnected();
        link.updateStatus();
        if (before != link.isConnected()) {
            stateChanged.run();
        }
    }

    public boolean isConnected() {
        return clientSide.getAsBoolean() ? clientConnected : link != null && link.isConnected();
    }

    public void setClientConnected(boolean connected) {
        clientConnected = connected;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    private boolean targetChanged() {
        var target = ChannelCardLinkHelper.findBoundChannel(upgrades.get(), fallbackOwner);
        return target != null ? targetChanged(target) : lastChannel != 0L || lastOwner != null;
    }

    private boolean targetChanged(ChannelCardLinkHelper.BoundChannel target) {
        return !ChannelCardLinkHelper.sameTarget(lastChannel, lastOwner, target);
    }

    private void resetTarget() {
        initialized = false;
        lastChannel = -1L;
        lastOwner = null;
    }

    private void disconnect() {
        ChannelCardLinkHelper.disconnect(link);
        // 卸载阶段宿主已经无效，不再触发保存或客户端同步回调。
        if (link != null && !closed) {
            stateChanged.run();
        }
    }
}
