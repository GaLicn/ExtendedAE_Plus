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
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.wireless.WirelessSlaveLink;
import com.extendedae_plus.wireless.endpoint.GenericNodeEndpointImpl;
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
 * PatternProviderLogic的兼容性Mixin
 * 优先级设置为1500，在appflux之后应用
 * 根据appflux是否存在来决定是否实现IUpgradeableObject接口
 */
@Mixin(value = PatternProviderLogic.class, priority = 500, remap = false)
public abstract class PatternProviderLogicCompatMixin implements IUpgradeableObject, InterfaceWirelessLinkBridge {
    
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

    @Unique
    private void eap$compatOnUpgradesChanged() {
        try {
            this.host.saveChanges();
            // 频道卡功能独立于升级槽功能，总是处理
            if (UpgradeSlotCompat.shouldEnableChannelCard()) {
                // 升级变更，重置并尝试初始化频道卡
                eap$compatLastChannel = -1;
                eap$compatHasInitialized = false;
                eap$compatInitializeChannelLink();
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级变更处理失败", e);
        }
    }
    
    // 监听appflux的升级变化 - 通过注入到appflux的af_$onUpgradesChanged方法
    @Inject(method = "af_$onUpgradesChanged", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onAppfluxUpgradesChanged(CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableChannelCard()) {
                // 升级变更，重置并尝试初始化频道卡
                eap$compatLastChannel = -1;
                eap$compatHasInitialized = false;
                eap$compatInitializeChannelLink();
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("监听appflux升级变化失败", e);
        }
    }

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$compatInitUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        try {
            
            boolean upgradeSlots = UpgradeSlotCompat.shouldEnableUpgradeSlots();
            boolean channelCard = UpgradeSlotCompat.shouldEnableChannelCard();
            
            
            if (upgradeSlots) {
                // 只有在升级槽功能启用时才创建升级槽
                this.eap$compatUpgrades = UpgradeInventories.forMachine(
                    host.getTerminalIcon().getItem(), 
                    1, 
                    this::eap$compatOnUpgradesChanged
                );
            } else if (channelCard) {
                // 如果装了appflux，我们不创建自己的升级槽，而是监听appflux的升级槽
            } else {
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级初始化失败", e);
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$compatSaveUpgrades(CompoundTag tag, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots() || UpgradeSlotCompat.shouldEnableChannelCard()) {
                this.eap$compatUpgrades.writeToNBT(tag, "compat_upgrades");
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级保存失败", e);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$compatLoadUpgrades(CompoundTag tag, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots() || UpgradeSlotCompat.shouldEnableChannelCard()) {
                this.eap$compatUpgrades.readFromNBT(tag, "compat_upgrades");
                // 从 NBT 加载后，重置并尝试初始化频道卡
                if (UpgradeSlotCompat.shouldEnableChannelCard()) {
                    eap$compatLastChannel = -1;
                    eap$compatHasInitialized = false;
                    eap$compatInitializeChannelLink();
                }
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级加载失败", e);
        }
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$compatDropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots() || UpgradeSlotCompat.shouldEnableChannelCard()) {
                for (var stack : this.eap$compatUpgrades) {
                    if (!stack.isEmpty()) {
                        drops.add(stack);
                    }
                }
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级掉落失败", e);
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$compatClearUpgrades(CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots() || UpgradeSlotCompat.shouldEnableChannelCard()) {
                this.eap$compatUpgrades.clear();
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级清理失败", e);
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            // 不装appflux时，返回我们自己的升级槽
            return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : UpgradeInventories.empty();
        } else {
            // 装了appflux时，这个方法不应该被调用，因为appflux的Mixin会覆盖它
            // 但是为了安全起见，返回空的升级槽
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.debug("装了appflux时getUpgrades被调用，这不应该发生");
            return UpgradeInventories.empty();
        }
    }

    @Override
    public void eap$updateWirelessLink() {
        if (!UpgradeSlotCompat.shouldEnableChannelCard()) {
            return;
        }
        
        try {
            if (eap$compatLink != null) {
                eap$compatLink.updateStatus();
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性无线链接更新失败", e);
        }
    }

    @Unique
    public void eap$compatInitializeChannelLink() {
        if (!UpgradeSlotCompat.shouldEnableChannelCard()) {
            return;
        }
        
        try {
            // 客户端早退
            if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
                return;
            }

            // 避免重复初始化
            if (eap$compatHasInitialized) {
                return;
            }

            // 等待网格完成引导
            if (!mainNode.hasGridBooted()) {
                eap$compatDelayedInitTicks = Math.max(eap$compatDelayedInitTicks, 5);
                try {
                    mainNode.ifPresent((grid, node) -> {
                        try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}
                return;
            }

            long channel = 0L;
            boolean found = false;
            
            // 获取升级槽 - 如果装了appflux则从appflux获取，否则从我们自己的获取
            IUpgradeInventory upgrades = null;
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                // 不装appflux时使用我们自己的升级槽
                upgrades = this.eap$compatUpgrades;
            } else if (UpgradeSlotCompat.shouldEnableChannelCard()) {
                // 装了appflux时，尝试从PatternProviderLogic获取升级槽
                try {
                    if (this instanceof IUpgradeableObject) {
                        IUpgradeableObject upgradeableThis = (IUpgradeableObject) this;
                        upgrades = upgradeableThis.getUpgrades();
                        com.extendedae_plus.util.ExtendedAELogger.LOGGER.debug("从appflux获取到升级槽: {}", upgrades != null);
                    }
                } catch (Exception e) {
                    com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("获取appflux升级槽失败", e);
                }
            }
            
            if (upgrades != null) {
                for (ItemStack stack : upgrades) {
                    if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                        channel = ChannelCardItem.getChannel(stack);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                // 无频道卡：断开并视为初始化完成
                if (eap$compatLink != null) {
                    eap$compatLink.setFrequency(0L);
                    eap$compatLink.updateStatus();
                }
                eap$compatHasInitialized = true;
                return;
            }

            if (eap$compatLink == null) {
                var endpoint = new GenericNodeEndpointImpl(() -> host.getBlockEntity(), () -> this.mainNode.getNode());
                eap$compatLink = new WirelessSlaveLink(endpoint);
            }

            eap$compatLink.setFrequency(channel);
            eap$compatLink.updateStatus();

            if (eap$compatLink.isConnected()) {
                eap$compatHasInitialized = true;
            } else {
                eap$compatHasInitialized = false;
                eap$compatDelayedInitTicks = Math.max(eap$compatDelayedInitTicks, 5);
                try {
                    mainNode.ifPresent((grid, node) -> {
                        try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性频道链接初始化失败", e);
        }
    }

    @Override
    public void eap$setClientWirelessState(boolean connected) {
        if (UpgradeSlotCompat.shouldEnableChannelCard()) {
            eap$compatClientConnected = connected;
        }
    }

    @Override
    public boolean eap$isWirelessConnected() {
        if (!UpgradeSlotCompat.shouldEnableChannelCard()) {
            return false;
        }
        
        try {
            if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
                return eap$compatClientConnected;
            } else {
                return eap$compatLink != null && eap$compatLink.isConnected();
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("检查兼容性无线连接状态失败", e);
            return false;
        }
    }

    @Override
    public boolean eap$hasTickInitialized() {
        if (UpgradeSlotCompat.shouldEnableChannelCard()) {
            return eap$compatHasInitialized;
        }
        return true;
    }

    @Override
    public void eap$setTickInitialized(boolean initialized) {
        if (UpgradeSlotCompat.shouldEnableChannelCard()) {
            eap$compatHasInitialized = initialized;
        }
    }

    @Override
    public void eap$handleDelayedInit() {
        if (!UpgradeSlotCompat.shouldEnableChannelCard()) {
            return;
        }
        
        try {
            // 仅服务端
            if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
                return;
            }
            if (!eap$compatHasInitialized) {
                if (!mainNode.hasGridBooted()) {
                    if (eap$compatDelayedInitTicks > 0) {
                        eap$compatDelayedInitTicks--;
                    }
                    if (eap$compatDelayedInitTicks == 0) {
                        eap$compatDelayedInitTicks = 5;
                        try {
                            mainNode.ifPresent((grid, node) -> {
                                try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                            });
                        } catch (Throwable ignored) {}
                    }
                } else {
                    eap$compatInitializeChannelLink();
                }
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性延迟初始化失败", e);
        }
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void eap$compatOnMainNodeStateChangedTail(CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldEnableChannelCard()) {
            return;
        }
        
        try {
            eap$compatLastChannel = -1;
            eap$compatHasInitialized = false;
            eap$compatDelayedInitTicks = 10;
            try {
                mainNode.ifPresent((grid, node) -> {
                    try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性主节点状态变更处理失败", e);
        }
    }
}
