package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.bridge.CompatUpgradeProvider;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.wireless.WirelessSlaveLink;
import com.extendedae_plus.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 样板供应器频道卡兼容实现：
 * - 未安装 appflux 时，提供 1 个升级槽并读取频道卡；
 * - 安装 appflux 时，优先从 appflux 提供的升级槽读取频道卡；
 * - 建立到无线主站的网格连接。
 */
@Mixin(value = PatternProviderLogic.class, priority = 900, remap = false)
public abstract class PatternProviderLogicCompatMixin implements CompatUpgradeProvider, InterfaceWirelessLinkBridge {

    @Unique
    private IUpgradeInventory eap$compatUpgrades = UpgradeInventories.empty();

    @Unique
    private WirelessSlaveLink eap$compatLink;

    @Unique
    private long eap$compatLastChannel = -1;

    @Unique
    private boolean eap$compatClientConnected = false;

    @Unique
    private boolean eap$compatHasInitialized = false;

    @Final
    @Shadow
    private PatternProviderLogicHost host;

    @Final
    @Shadow
    private IManagedGridNode mainNode;

    @Final
    @Shadow
    private IActionSource actionSource;

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$compatInit(IManagedGridNode mainNode, PatternProviderLogicHost host, int size, CallbackInfo ci) {
        try {
            ExtendedAELogger.LOGGER.debug("[样板供应器] 初始化兼容升级槽，shouldEnableUpgradeSlots: {}", UpgradeSlotCompat.shouldEnableUpgradeSlots());
            ExtendedAELogger.LOGGER.debug("[样板供应器] this instanceof IUpgradeableObject: {}", this instanceof IUpgradeableObject);
            
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                // 未安装AppliedFlux，我们需要提供升级槽
                this.eap$compatUpgrades = UpgradeInventories.forMachine(
                        host.getTerminalIcon().getItem(), 1, this::eap$compatOnUpgradesChanged);
                ExtendedAELogger.LOGGER.debug("[样板供应器] 初始化自带升级槽 (未安装 appflux)");
            } else {
                // 安装了AppliedFlux，我们不提供升级槽，但保留空的兼容槽用于备用
                this.eap$compatUpgrades = UpgradeInventories.empty();
                ExtendedAELogger.LOGGER.debug("[样板供应器] 跳过初始化升级槽 (已安装 appflux)");
                
                // 尝试监听AppliedFlux的升级变更
                eap$tryHookAppliedFluxUpgradeChanges();
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 初始化兼容升级槽失败", t);
        }
    }

    @Unique
    private void eap$compatOnUpgradesChanged() {
        try {
            this.host.saveChanges();
            eap$compatLastChannel = -1;
            eap$compatHasInitialized = false;
            ExtendedAELogger.LOGGER.debug("[样板供应器] 升级变更 -> 触发初始化");
            eap$compatInitializeChannelLink();
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 兼容升级变更处理失败", t);
        }
    }

    /**
     * 尝试监听AppliedFlux的升级变更
     */
    @Unique
    private void eap$tryHookAppliedFluxUpgradeChanges() {
        try {
            if (this instanceof IUpgradeableObject upgradeableObject) {
                IUpgradeInventory afUpgrades = upgradeableObject.getUpgrades();
                if (afUpgrades != null) {
                    ExtendedAELogger.LOGGER.debug("[样板供应器] 尝试监听AppliedFlux升级变更");
                    // 我们不能直接修改AppliedFlux的升级槽回调，但我们可以定期检查
                    // 这里我们先记录一下，实际的检查会在tick中进行
                }
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.debug("[样板供应器] 监听AppliedFlux升级变更失败: {}", t.getMessage());
        }
    }


    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$compatWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades.writeToNBT(tag, "compat_upgrades", registries);
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 保存兼容升级失败", t);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$compatRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades.readFromNBT(tag, "compat_upgrades", registries);
            }
            // 无论哪种模式都重新初始化
            eap$compatLastChannel = -1;
            eap$compatHasInitialized = false;
            eap$compatInitializeChannelLink();
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 读取兼容升级失败", t);
        }
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$compatDrops(List<ItemStack> drops, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                for (var s : this.eap$compatUpgrades) {
                    if (!s.isEmpty()) drops.add(s);
                }
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 掉落兼容升级失败", t);
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$compatClear(CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades.clear();
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 清理兼容升级失败", t);
        }
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void eap$compatOnNodeChange(CallbackInfo ci) {
        try {
            eap$compatLastChannel = -1;
            eap$compatHasInitialized = false;
            // 直接初始化，不使用延迟
            eap$compatInitializeChannelLink();
            mainNode.ifPresent((grid, node) -> {
                try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 主节点状态变更处理失败", t);
        }
    }


    

    @Override
    public void eap$updateWirelessLink() {
        if (eap$compatLink != null) {
            eap$compatLink.updateStatus();
        }
    }

    @Unique
    public void eap$compatInitializeChannelLink() {
        try {
            // 客户端早退
            if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
                return;
            }
            if (eap$compatHasInitialized) {
                return;
            }
            if (mainNode == null || mainNode.getNode() == null) {
                ExtendedAELogger.LOGGER.debug("[样板供应器] 初始化跳过：mainNode 或 Node 不可用");
                return;
            }

            long channel = 0L;
            boolean found = false;

            IUpgradeInventory upgrades = null;
            
            // 优先尝试从AppliedFlux获取升级槽（如果安装了的话）
            if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                // 安装了appflux：优先使用appflux的升级槽
                try {
                    // 更安全的方式获取AppliedFlux升级槽
                    upgrades = eap$getAppliedFluxUpgrades();
                    if (upgrades != null) {
                        ExtendedAELogger.LOGGER.debug("[样板供应器] 成功获取 appflux 升级槽，大小: {}", upgrades.size());
                    } else {
                        ExtendedAELogger.LOGGER.warn("[样板供应器] 无法获取 appflux 升级槽，回退到兼容槽");
                        upgrades = this.eap$compatUpgrades;
                    }
                } catch (Throwable t) {
                    ExtendedAELogger.LOGGER.error("[样板供应器] 获取 appflux 升级槽失败，回退到兼容槽", t);
                    upgrades = this.eap$compatUpgrades;
                }
            } else {
                // 未安装appflux：使用我们的兼容升级槽
                upgrades = this.eap$compatUpgrades;
                ExtendedAELogger.LOGGER.debug("[样板供应器] 使用自带升级槽（未安装 appflux）: {}", upgrades != null);
            }
            
            // 双重保险：如果主要方式失败，尝试备用方式
            if (upgrades == null || !eap$hasChannelCard(upgrades)) {
                ExtendedAELogger.LOGGER.debug("[样板供应器] 主升级槽无频道卡，尝试备用方式");
                
                if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                    // 如果我们的槽无频道卡，尝试检查是否有AppliedFlux的槽
                    try {
                        IUpgradeInventory backupUpgrades = eap$getAppliedFluxUpgrades();
                        if (backupUpgrades != null && eap$hasChannelCard(backupUpgrades)) {
                            upgrades = backupUpgrades;
                            ExtendedAELogger.LOGGER.debug("[样板供应器] 使用备用 appflux 升级槽");
                        }
                    } catch (Throwable t) {
                        ExtendedAELogger.LOGGER.debug("[样板供应器] 备用升级槽检查失败: {}", t.getMessage());
                    }
                } else {
                    // 如果AppliedFlux的槽无频道卡，尝试我们的兼容槽
                    if (this.eap$compatUpgrades != null && eap$hasChannelCard(this.eap$compatUpgrades)) {
                        upgrades = this.eap$compatUpgrades;
                        ExtendedAELogger.LOGGER.debug("[样板供应器] 使用备用兼容升级槽");
                    }
                }
            }

            if (upgrades != null) {
                for (ItemStack stack : upgrades) {
                    if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                        channel = ChannelCardItem.getChannel(stack);
                        found = true;
                        ExtendedAELogger.LOGGER.debug("[样板供应器] 检测到频道卡，频道={} ", channel);
                        break;
                    }
                }
            }

            if (!found) {
                ExtendedAELogger.LOGGER.debug("[样板供应器] 未发现频道卡 -> 断开无线");
                if (eap$compatLink != null) {
                    eap$compatLink.setFrequency(0L);
                    eap$compatLink.updateStatus();
                }
                eap$compatLastChannel = 0L;
                eap$compatHasInitialized = true;
                try { host.saveChanges(); } catch (Throwable ignored) {}
                // 唤醒节点，加速 AE2 感知到连接断开
                mainNode.ifPresent((grid, node) -> {
                    try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                    // 兜底：如仍存在针对无线主端的直连（非 in-world），强制销毁
                    try {
                        for (IGridConnection gc : node.getConnections()) {
                            if (gc != null && !gc.isInWorld()) {
                                var other = gc.getOtherSide(node);
                                if (other != null && other.getOwner() instanceof com.extendedae_plus.wireless.IWirelessEndpoint) {
                                    ExtendedAELogger.LOGGER.debug("[样板供应器] 兜底销毁残留无线直连: {} -> {}", node, other);
                                    gc.destroy();
                                    try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored2) {}
                                    try { if (other.getGrid() != null) { other.getGrid().getTickManager().wakeDevice(other); } } catch (Throwable ignored2) {}
                                }
                            }
                        }
                    } catch (Throwable ignored2) {}
                });
                return;
            }

            if (eap$compatLink == null) {
                var endpoint = new GenericNodeEndpointImpl(() -> host.getBlockEntity(), () -> this.mainNode.getNode());
                eap$compatLink = new WirelessSlaveLink(endpoint);
            }

            eap$compatLink.setFrequency(channel);
            eap$compatLink.updateStatus();
            eap$compatLastChannel = channel; // 记录当前频道
            ExtendedAELogger.LOGGER.debug("[样板供应器] 设置频道={} 连接状态={}", channel, eap$compatLink.isConnected());
            try { host.saveChanges(); } catch (Throwable ignored) {}
            mainNode.ifPresent((grid, node) -> {
                try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
            });

            if (eap$compatLink.isConnected()) {
                eap$compatHasInitialized = true;
            } else {
                eap$compatHasInitialized = false;
                // 如果连接失败，唤醒设备以便稍后重试
                mainNode.ifPresent((grid, node) -> {
                    try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 初始化频道链接失败", t);
        }
    }

    @Override
    public void eap$setClientWirelessState(boolean connected) {
        eap$compatClientConnected = connected;
    }

    @Override
    public boolean eap$isWirelessConnected() {
        if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
            return eap$compatClientConnected;
        } else {
            return eap$compatLink != null && eap$compatLink.isConnected();
        }
    }

    @Override
    public boolean eap$hasTickInitialized() {
        return eap$compatHasInitialized;
    }

    @Override
    public void eap$setTickInitialized(boolean initialized) {
        eap$compatHasInitialized = initialized;
    }

    @Override
    public void eap$handleDelayedInit() {
        // 如果还未初始化，或者需要重新检查AppliedFlux升级槽
        if (!eap$compatHasInitialized) {
            eap$compatInitializeChannelLink();
        } else if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            // 安装了AppliedFlux时，定期检查升级槽变化
            try {
                IUpgradeInventory afUpgrades = eap$getAppliedFluxUpgrades();
                if (afUpgrades != null && eap$hasChannelCard(afUpgrades)) {
                    // 检查频道是否发生变化
                    for (ItemStack stack : afUpgrades) {
                        if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                            long newChannel = ChannelCardItem.getChannel(stack);
                            if (newChannel != eap$compatLastChannel) {
                                ExtendedAELogger.LOGGER.debug("[样板供应器] 检测到频道变化: {} -> {}", eap$compatLastChannel, newChannel);
                                eap$compatLastChannel = -1; // 强制重新初始化
                                eap$compatHasInitialized = false;
                                eap$compatInitializeChannelLink();
                            }
                            break;
                        }
                    }
                } else if (eap$compatLastChannel != 0L) {
                    // 频道卡被移除
                    ExtendedAELogger.LOGGER.debug("[样板供应器] 频道卡被移除");
                    eap$compatLastChannel = -1;
                    eap$compatHasInitialized = false;
                    eap$compatInitializeChannelLink();
                }
            } catch (Throwable t) {
                ExtendedAELogger.LOGGER.debug("[样板供应器] 延迟初始化检查失败: {}", t.getMessage());
            }
        }
    }

    // CompatUpgradeProvider 实现：仅在未安装 appflux 时由我们提供升级槽
    @Unique
    private boolean eap$hasChannelCard(IUpgradeInventory inventory) {
        if (inventory == null) {
            return false;
        }
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 安全地获取AppliedFlux提供的升级槽
     */
    @Unique
    private IUpgradeInventory eap$getAppliedFluxUpgrades() {
        try {
            ExtendedAELogger.LOGGER.debug("[样板供应器] 尝试获取AppliedFlux升级槽");
            ExtendedAELogger.LOGGER.debug("[样板供应器] this instanceof IUpgradeableObject: {}", this instanceof IUpgradeableObject);
            
            // 检查当前对象是否实现了IUpgradeableObject接口
            if (this instanceof IUpgradeableObject upgradeableObject) {
                IUpgradeInventory upgrades = upgradeableObject.getUpgrades();
                ExtendedAELogger.LOGGER.debug("[样板供应器] AppliedFlux升级槽: {}", upgrades);
                ExtendedAELogger.LOGGER.debug("[样板供应器] 兼容升级槽: {}", this.eap$compatUpgrades);
                ExtendedAELogger.LOGGER.debug("[样板供应器] 是否为同一对象: {}", upgrades == this.eap$compatUpgrades);
                
                // 确保这不是我们自己的兼容升级槽
                if (upgrades != null && upgrades != this.eap$compatUpgrades) {
                    ExtendedAELogger.LOGGER.debug("[样板供应器] 成功获取AppliedFlux升级槽，大小: {}", upgrades.size());
                    return upgrades;
                } else {
                    ExtendedAELogger.LOGGER.debug("[样板供应器] AppliedFlux升级槽无效或与兼容槽相同");
                }
            } else {
                ExtendedAELogger.LOGGER.debug("[样板供应器] 当前对象未实现IUpgradeableObject接口");
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器] 获取AppliedFlux升级槽时出错", t);
        }
        return null;
    }

    @Override
    public IUpgradeInventory eap$getCompatUpgrades() {
        return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : UpgradeInventories.empty();
    }
}
