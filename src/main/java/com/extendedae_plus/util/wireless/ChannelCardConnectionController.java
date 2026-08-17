package com.extendedae_plus.util.wireless;

import appeng.api.upgrades.IUpgradeInventory;
import com.extendedae_plus.ae.wireless.IWirelessEndpoint;
import com.extendedae_plus.ae.wireless.WirelessSlaveLink;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** 统一管理频道卡连接的初始化、重连和卸载。 */
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

    /** 升级槽改变后清除旧目标并唤醒节点。 */
    public void onUpgradesChanged() {
        resetTarget();
        initialize();
        wakeNode.run();
    }

    /** AE 节点重建后强制重新绑定频道。 */
    public void onNodeChanged() {
        resetTarget();
        wakeNode.run();
    }

    /** 方块实体加载或部件重新加入世界。 */
    public void onLoaded() {
        closed = false;
        resetTarget();
        initialize();
        wakeNode.run();
    }

    /** 方块实体/部件卸载时断开无线从端。 */
    public void onUnloaded() {
        closed = true;
        initialized = true;
        disconnect();
    }

    public static void register(BlockEntity owner, ChannelCardConnectionController controller) {
        synchronized (REGISTRY) {
            REGISTRY.computeIfAbsent(owner, ignored ->
                    Collections.newSetFromMap(new IdentityHashMap<>())).add(controller);
        }
    }

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

    /** 由宿主 ticker 调用；节点未启动时会继续保持低频 tick。 */
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

    public boolean shouldKeepTicking() {
        return !initialized || ChannelCardLinkHelper.hasChannelCard(upgrades.get())
                || ChannelCardLinkHelper.hasActiveLink(link);
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
        if (link != null && !closed) {
            stateChanged.run();
        }
    }
}
