package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.wireless.WirelessSlaveLink;
import com.extendedae_plus.wireless.endpoint.InterfaceNodeEndpointImpl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InterfaceLogic.class)
public abstract class InterfaceLogicChannelCardMixin implements InterfaceWirelessLinkBridge {

    @Shadow(remap = false) public abstract IUpgradeInventory getUpgrades();

    @Shadow(remap = false) public abstract appeng.api.networking.IGridNode getActionableNode();

    @Shadow(remap = false) protected InterfaceLogicHost host;
    
    @Shadow(remap = false) protected appeng.api.networking.IManagedGridNode mainNode;

    @Unique
    private WirelessSlaveLink eap$link;
    
    @Unique
    private long eap$lastChannel = -1;
    
    @Unique
    private boolean eap$clientConnected = false;
    
    @Unique
    private boolean eap$hasInitialized = false;
    
    @Unique
    private int eap$delayedInitTicks = 0;
    
    static {
        // InterfaceLogicChannelCardMixin 已加载
    }

    @Inject(method = "onUpgradesChanged", at = @At("TAIL"), remap = false)
    private void eap$onUpgradesChangedTail(CallbackInfo ci) {
        // 升级变更时重置标志并尝试初始化
        eap$lastChannel = -1;
        eap$hasInitialized = false;
        eap$initializeChannelLink();
    }

    @Inject(method = "gridChanged", at = @At("TAIL"), remap = false)
    private void eap$afterGridChanged(CallbackInfo ci) {
        // 网格状态变化时重置标志并设置延迟初始化
        eap$lastChannel = -1;
        eap$hasInitialized = false;
        eap$delayedInitTicks = 10; // 适当增加延迟tick，等待网格完成引导
        // 尝试唤醒设备，确保后续还能继续tick
        if (mainNode != null) {
            mainNode.ifPresent((grid, node) -> {
                try {
                    grid.getTickManager().wakeDevice(node);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void eap$afterReadNBT(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        // 从 NBT加载时重置标志
        eap$lastChannel = -1;
        eap$hasInitialized = false;
        // 直接尝试初始化
        eap$initializeChannelLink();
    }

    @Inject(method = "clearContent", at = @At("HEAD"), remap = false)
    private void eap$onClearContent(CallbackInfo ci) {
        if (eap$link != null) {
            eap$link.onUnloadOrRemove();
        }
    }

    @Unique
    public void eap$initializeChannelLink() {
        // 仅在服务端执行，避免在渲染线程/客户端触发任何初始化路径
        if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
            return;
        }

        // 避免重复初始化
        if (eap$hasInitialized) {
            return;
        }

        // 检查网格节点是否在线（1.21.1版本使用isActive替代hasGridBooted）
        if (mainNode == null || !mainNode.isActive()) {
            return;
        }

        try {
            long channel = 0L;
            boolean found = false;
            for (ItemStack stack : getUpgrades()) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                    channel = ChannelCardItem.getChannel(stack);
                    found = true;
                    break;
                }
            }

            if (!found) {
                // 无频道卡：断开并视为初始化完成
                if (eap$link != null) {
                    eap$link.setFrequency(0L);
                    eap$link.updateStatus();
                }
                eap$hasInitialized = true;
                return;
            }

            if (eap$link == null) {
                var endpoint = new InterfaceNodeEndpointImpl(host, () -> this.mainNode.getNode());
                eap$link = new WirelessSlaveLink(endpoint);
            }

            eap$link.setFrequency(channel);
            eap$link.updateStatus();
            
            if (eap$link.isConnected()) {
                eap$hasInitialized = true; // 设置初始化完成标志
            } else {
                // 不标记为完成，允许后续tick重试
                eap$hasInitialized = false;
                // 设置一个短延迟窗口，避免每tick刷屏
                eap$delayedInitTicks = Math.max(eap$delayedInitTicks, 5);
                try {
                    mainNode.ifPresent((grid, node) -> {
                        try {
                            grid.getTickManager().wakeDevice(node);
                        } catch (Throwable ignored) {
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void eap$updateWirelessLink() {
        if (eap$link != null) {
            eap$link.updateStatus();
        }
    }
    
    @Override
    public boolean eap$isWirelessConnected() {
        // InterfaceLogic没有isClientSide方法，需要通过host判断
        if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
            return eap$clientConnected;
        } else {
            return eap$link != null && eap$link.isConnected();
        }
    }
    
    @Override
    public void eap$setClientWirelessState(boolean connected) {
        eap$clientConnected = connected;
    }
    
    @Override
    public boolean eap$hasTickInitialized() {
        return eap$hasInitialized;
    }
    
    @Override
    public void eap$setTickInitialized(boolean initialized) {
        eap$hasInitialized = initialized;
    }
    
    @Override
    public void eap$handleDelayedInit() {
        // 仅在服务端执行延迟初始化，避免在渲染线程/客户端触发任何初始化路径
        if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
            return;
        }

        // 若尚未初始化，则持续尝试，直到网格完成引导
        if (!eap$hasInitialized) {
            if (mainNode == null || !mainNode.isActive()) {
                // 仍在引导，消耗计时器
                if (eap$delayedInitTicks > 0) {
                    eap$delayedInitTicks--;
                }
                if (eap$delayedInitTicks == 0) {
                    // 重新设定一个短延迟窗口，并唤醒设备，以保证后续还能继续 tick
                    eap$delayedInitTicks = 5;
                    try {
                        mainNode.ifPresent((grid, node) -> {
                            try {
                                grid.getTickManager().wakeDevice(node);
                            } catch (Throwable ignored) {
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                // 网格已引导完成，执行初始化
                eap$initializeChannelLink();
            }
        }
    }
    
    // eap$initializeChannelLink方法已在上面实现
}
