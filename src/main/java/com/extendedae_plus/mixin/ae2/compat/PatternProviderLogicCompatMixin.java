package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.networking.IManagedGridNode;
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
@Mixin(value = PatternProviderLogic.class, remap = false)
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

    @Unique
    private int eap$compatDelayedInitTicks = 0;

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
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades = UpgradeInventories.forMachine(
                        host.getTerminalIcon().getItem(), 1, this::eap$compatOnUpgradesChanged);
                ExtendedAELogger.LOGGER.debug("[样板供应器] 初始化自带升级槽 (未安装 appflux)");
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
                eap$compatLastChannel = -1;
                eap$compatHasInitialized = false;
                eap$compatInitializeChannelLink();
            }
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
            eap$compatDelayedInitTicks = 10;
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
                    if ((Object) this instanceof IUpgradeableObject uo) {
                        upgrades = uo.getUpgrades();
                        ExtendedAELogger.LOGGER.debug("[样板供应器] 使用 appflux 提供的升级槽: {}", upgrades != null);
                    }
                } catch (Throwable t) {
                    ExtendedAELogger.LOGGER.error("[样板供应器] 获取 appflux 升级槽失败，回退到兼容槽", t);
                    // 如果获取AppliedFlux升级槽失败，回退到我们的兼容槽
                    upgrades = this.eap$compatUpgrades;
                }
            } else {
                // 未安装appflux：使用我们的兼容升级槽
                upgrades = this.eap$compatUpgrades;
                ExtendedAELogger.LOGGER.debug("[样板供应器] 使用自带升级槽（未安装 appflux）: {}", upgrades != null);
            }
            
            // 双重保险：如果主要方式失败，尝试备用方式
            if (upgrades == null || eap$isUpgradeInventoryEmpty(upgrades)) {
                ExtendedAELogger.LOGGER.debug("[样板供应器] 主升级槽为空，尝试备用方式");
                
                if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                    // 如果我们的槽为空，尝试检查是否有AppliedFlux的槽
                    try {
                        if ((Object) this instanceof IUpgradeableObject uo) {
                            IUpgradeInventory backupUpgrades = uo.getUpgrades();
                            if (backupUpgrades != null && !eap$isUpgradeInventoryEmpty(backupUpgrades)) {
                                upgrades = backupUpgrades;
                                ExtendedAELogger.LOGGER.debug("[样板供应器] 使用备用 appflux 升级槽");
                            }
                        }
                    } catch (Throwable t) {
                        ExtendedAELogger.LOGGER.debug("[样板供应器] 备用升级槽检查失败: {}", t.getMessage());
                    }
                } else {
                    // 如果AppliedFlux的槽为空，尝试我们的兼容槽
                    if (this.eap$compatUpgrades != null && !eap$isUpgradeInventoryEmpty(this.eap$compatUpgrades)) {
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
                eap$compatHasInitialized = true;
                try { host.saveChanges(); } catch (Throwable ignored) {}
                return;
            }

            if (eap$compatLink == null) {
                var endpoint = new GenericNodeEndpointImpl(() -> host.getBlockEntity(), () -> this.mainNode.getNode());
                eap$compatLink = new WirelessSlaveLink(endpoint);
            }

            eap$compatLink.setFrequency(channel);
            eap$compatLink.updateStatus();
            ExtendedAELogger.LOGGER.debug("[样板供应器] 设置频道={} 连接状态={}", channel, eap$compatLink.isConnected());
            try { host.saveChanges(); } catch (Throwable ignored) {}
            mainNode.ifPresent((grid, node) -> {
                try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
            });

            if (eap$compatLink.isConnected()) {
                eap$compatHasInitialized = true;
            } else {
                eap$compatHasInitialized = false;
                eap$compatDelayedInitTicks = Math.max(eap$compatDelayedInitTicks, 5);
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

    // CompatUpgradeProvider 实现：仅在未安装 appflux 时由我们提供升级槽
    @Unique
    private boolean eap$isUpgradeInventoryEmpty(IUpgradeInventory inventory) {
        if (inventory == null) {
            return true;
        }
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public IUpgradeInventory eap$getCompatUpgrades() {
        return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : UpgradeInventories.empty();
    }
}
