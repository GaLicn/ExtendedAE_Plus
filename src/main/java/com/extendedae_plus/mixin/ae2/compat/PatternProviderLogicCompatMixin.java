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
 * 优先级设置为500，避免与appflux冲突
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
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return;
        }
        
        try {
            this.host.saveChanges();
            // 升级变更，重置并尝试初始化
            eap$compatLastChannel = -1;
            eap$compatHasInitialized = false;
            eap$compatInitializeChannelLink();
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级变更处理失败", e);
        }
    }

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$compatInitUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades = UpgradeInventories.forMachine(
                    host.getTerminalIcon().getItem(), 
                    1, 
                    this::eap$compatOnUpgradesChanged
                );
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级初始化失败", e);
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$compatSaveUpgrades(CompoundTag tag, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades.writeToNBT(tag, "compat_upgrades");
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级保存失败", e);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$compatLoadUpgrades(CompoundTag tag, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades.readFromNBT(tag, "compat_upgrades");
                // 从 NBT 加载后，重置并尝试初始化
                eap$compatLastChannel = -1;
                eap$compatHasInitialized = false;
                eap$compatInitializeChannelLink();
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级加载失败", e);
        }
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$compatDropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
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
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades.clear();
            }
        } catch (Exception e) {
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.error("兼容性升级清理失败", e);
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return this.eap$compatUpgrades;
        }
        return UpgradeInventories.empty();
    }

    @Override
    public void eap$updateWirelessLink() {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
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
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
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
            for (ItemStack stack : this.eap$compatUpgrades) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                    channel = ChannelCardItem.getChannel(stack);
                    found = true;
                    break;
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
        if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            eap$compatClientConnected = connected;
        }
    }

    @Override
    public boolean eap$isWirelessConnected() {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
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
        if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return eap$compatHasInitialized;
        }
        return true;
    }

    @Override
    public void eap$setTickInitialized(boolean initialized) {
        if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            eap$compatHasInitialized = initialized;
        }
    }

    @Override
    public void eap$handleDelayedInit() {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
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
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
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
