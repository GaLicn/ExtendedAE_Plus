package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.ae.wireless.WirelessSlaveLink;
import com.extendedae_plus.ae.wireless.endpoint.InterfaceNodeEndpointImpl;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.materials.ChannelCardItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InterfaceLogic.class)
public abstract class InterfaceLogicChannelCardMixin implements InterfaceWirelessLinkBridge {

    static {
        // InterfaceLogicChannelCardMixin 已加载
    }

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

    @Shadow(remap = false)
    public abstract IUpgradeInventory getUpgrades();

    @Shadow(remap = false)
    public abstract appeng.api.networking.IGridNode getActionableNode();

    @Inject(method = "onUpgradesChanged", at = @At("TAIL"), remap = false)
    private void eap$onUpgradesChangedTail(CallbackInfo ci) {
        // 升级变更时重置标志并尝试初始化
        this.eap$lastChannel = -1;
        this.eap$hasInitialized = false;
        this.eap$initializeChannelLink();
    }

    @Inject(method = "gridChanged", at = @At("TAIL"), remap = false)
    private void eap$afterGridChanged(CallbackInfo ci) {
        // 网格状态变化时重置标志并设置延迟初始化
        this.eap$lastChannel = -1;
        this.eap$hasInitialized = false;
        this.eap$delayedInitTicks = 10; // 适当增加延迟tick，等待网格完成引导
        // 尝试唤醒设备，确保后续还能继续tick
        if (this.mainNode != null) {
            this.mainNode.ifPresent((grid, node) -> {
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
        this.eap$lastChannel = -1;
        this.eap$hasInitialized = false;
        // 直接尝试初始化
        this.eap$initializeChannelLink();
    }

    @Inject(method = "clearContent", at = @At("HEAD"), remap = false)
    private void eap$onClearContent(CallbackInfo ci) {
        if (this.eap$link != null) {
            this.eap$link.onUnloadOrRemove();
        }
    }

    @Override
    public void eap$updateWirelessLink() {
        if (this.eap$link != null) {
            this.eap$link.updateStatus();
        }
    }

    @Override
    public boolean eap$isWirelessConnected() {
        // InterfaceLogic没有isClientSide方法，需要通过host判断
        if (this.host.getBlockEntity() != null && this.host.getBlockEntity().getLevel() != null && this.host.getBlockEntity().getLevel().isClientSide) {
            return this.eap$clientConnected;
        } else {
            return this.eap$link != null && this.eap$link.isConnected();
        }
    }

    @Override
    public void eap$setClientWirelessState(boolean connected) {
        this.eap$clientConnected = connected;
    }

    @Override
    public boolean eap$hasTickInitialized() {
        return this.eap$hasInitialized;
    }

    @Override
    public void eap$setTickInitialized(boolean initialized) {
        this.eap$hasInitialized = initialized;
    }

    @Override
    @Unique
    public void eap$initializeChannelLink() {
        // 仅在服务端执行，避免在渲染线程/客户端触发任何初始化路径
        if (this.host.getBlockEntity() != null && this.host.getBlockEntity().getLevel() != null && this.host.getBlockEntity().getLevel().isClientSide) {
            return;
        }

        // 避免重复初始化
        if (this.eap$hasInitialized) {
            return;
        }

        // 仅要求节点对象可用；不要依赖 isActive（无线连接本身会建立连接与激活节点）
        if (this.mainNode == null || this.mainNode.getNode() == null) {
            return;
        }

        try {
            long channel = 0L;
            boolean found = false;
            for (ItemStack stack : this.getUpgrades()) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                    channel = ChannelCardItem.getChannel(stack);
                    found = true;
                    break;
                }
            }

            if (!found) {
                // 无频道卡：断开并视为初始化完成
                if (this.eap$link != null) {
                    this.eap$link.setFrequency(0L);
                    this.eap$link.updateStatus();
                }
                this.eap$hasInitialized = true;
                // 保存一次状态
                try {
                    this.host.saveChanges();
                } catch (Throwable ignored) {
                }
                // 唤醒设备以刷新客户端/邻居
                try {
                    this.mainNode.ifPresent((grid, node) -> {
                        try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored2) {}
                    });
                } catch (Throwable ignored2) {}
                return;
            }

            if (this.eap$link == null) {
                var endpoint = new InterfaceNodeEndpointImpl(this.host, () -> this.mainNode.getNode());
                this.eap$link = new WirelessSlaveLink(endpoint);
            }

            this.eap$link.setFrequency(channel);
            this.eap$link.updateStatus();
            try {
                this.host.saveChanges();
            } catch (Throwable ignored) {
            }
            // 唤醒设备，加速后续 tick 以完成连接
            try {
                this.mainNode.ifPresent((grid, node) -> {
                    try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored2) {}
                });
            } catch (Throwable ignored2) {}

            if (this.eap$link.isConnected()) {
                this.eap$hasInitialized = true; // 设置初始化完成标志
            } else {
                // 不标记为完成，允许后续tick重试
                this.eap$hasInitialized = false;
                // 设置一个短延迟窗口，避免每tick刷屏
                this.eap$delayedInitTicks = Math.max(this.eap$delayedInitTicks, 5);
                try {
                    this.mainNode.ifPresent((grid, node) -> {
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
    public void eap$handleDelayedInit() {
        // 仅在服务端执行延迟初始化，避免在渲染线程/客户端触发任何初始化路径
        if (this.host.getBlockEntity() != null && this.host.getBlockEntity().getLevel() != null && this.host.getBlockEntity().getLevel().isClientSide) {
            return;
        }

        // 若尚未初始化，则持续尝试，直到网格完成引导
        if (!this.eap$hasInitialized) {
            // 若节点对象尚未就绪，则等待；无需等待 isActive（无线接入后会激活）
            if (this.mainNode == null || this.mainNode.getNode() == null) {
                // 仍在引导，消耗计时器
                if (this.eap$delayedInitTicks > 0) {
                    this.eap$delayedInitTicks--;
                }
                if (this.eap$delayedInitTicks == 0) {
                    // 重新设定一个短延迟窗口，并唤醒设备，以保证后续还能继续 tick
                    this.eap$delayedInitTicks = 5;
                    try {
                        this.mainNode.ifPresent((grid, node) -> {
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
                this.eap$initializeChannelLink();
            }
        }
    }
    
    // eap$initializeChannelLink方法已在上面实现
}
