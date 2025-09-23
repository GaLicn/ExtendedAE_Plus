package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.util.ExtendedAELogger;
import com.extendedae_plus.wireless.IWirelessEndpoint;
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
        ExtendedAELogger.LOGGER.info("[服务端] InterfaceLogicChannelCardMixin 已加载");
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
        ExtendedAELogger.LOGGER.debug("[服务端] Interface gridChanged 触发，设置延迟初始化");
        eap$lastChannel = -1;
        eap$hasInitialized = false;
        eap$delayedInitTicks = 10; // 适当增加延迟tick，等待网格完成引导
        // 尝试唤醒设备，确保后续还能继续tick
        if (mainNode != null) {
            mainNode.ifPresent((grid, node) -> {
                try {
                    grid.getTickManager().wakeDevice(node);
                } catch (Throwable t) {
                    // 防御性日志，避免因这里的异常影响主流程
                    ExtendedAELogger.LOGGER.debug("[服务端] Interface 唤醒设备失败: {}", t.toString());
                }
            });
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void eap$afterReadNBT(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
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
        ExtendedAELogger.LOGGER.debug("[服务端] Interface eap$initializeChannelLink 被调用");
        
        // 防止在客户端执行
        if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
            ExtendedAELogger.LOGGER.debug("[服务端] Interface 在客户端，跳过初始化");
            return;
        }
        
        // 检查是否已经初始化过
        if (eap$hasInitialized) {
            ExtendedAELogger.LOGGER.debug("[服务端] Interface 已经初始化过，跳过");
            return;
        }
        
        // 优先等待网格完成引导（比仅检查 isActive 更可靠）
        if (!mainNode.hasGridBooted()) {
            ExtendedAELogger.LOGGER.debug("[服务端] Interface 网格未完成引导(boot)，等待后再初始化: ready={}, active={}, online={}",
                mainNode.isReady(), mainNode.isActive(), mainNode.isOnline());
            return;
        }
        
        try {
            var inv = getUpgrades();
            long channel = 0L;
            boolean found = false;
            for (ItemStack stack : inv) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                    channel = ChannelCardItem.getChannel(stack);
                    found = true;
                    break;
                }
            }
            
            ExtendedAELogger.LOGGER.debug("[服务端] Interface 初始化频道链接: found={}, channel={}", found, channel);
            
            if (!found) {
                // 无频道卡则断开
                if (eap$link != null) {
                    eap$link.setFrequency(0L);
                    eap$link.updateStatus();
                    ExtendedAELogger.LOGGER.debug("[服务端] Interface 断开频道链接");
                }
                eap$hasInitialized = true; // 无频道卡也算初始化完成
                return;
            }
            
            if (eap$link == null) {
                // 使用mainNode而不是getActionableNode，因为后者可能返回null
                IWirelessEndpoint endpoint = new InterfaceNodeEndpointImpl(host, () -> mainNode.getNode());
                eap$link = new WirelessSlaveLink(endpoint);
                ExtendedAELogger.LOGGER.debug("[服务端] Interface 创建新的无线链接");
            }
            
            eap$link.setFrequency(channel);
            eap$link.updateStatus();
            
            // 调试信息：检查网格节点状态
            var gridNode = mainNode.getNode();
            var isActive = mainNode.isActive();
            ExtendedAELogger.LOGGER.debug("[服务端] Interface 设置频道: {}, 连接状态: {}, 网格节点: {}, 激活: {}, 在线: {}", 
                channel, eap$link.isConnected(), 
                gridNode != null ? "exists" : "null", 
                isActive,
                gridNode != null ? gridNode.isOnline() : "N/A");
            
            if (eap$link.isConnected()) {
                eap$hasInitialized = true; // 设置初始化完成标志
                ExtendedAELogger.LOGGER.debug("[服务端] Interface 无线链接建立成功");
            } else {
                ExtendedAELogger.LOGGER.warn("[服务端] Interface 无线链接建立失败，将继续重试");
                // 不标记为完成，允许后续tick重试
                eap$hasInitialized = false;
                // 设置一个短延迟窗口，避免每tick刷屏
                eap$delayedInitTicks = Math.max(eap$delayedInitTicks, 5);
                try {
                    mainNode.ifPresent((grid, node) -> {
                        try {
                            grid.getTickManager().wakeDevice(node);
                        } catch (Throwable t) {
                            ExtendedAELogger.LOGGER.debug("[服务端] Interface 初始化失败后唤醒设备失败: {}", t.toString());
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("[服务端] Interface 初始化频道链接失败", e);
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
            if (!mainNode.hasGridBooted()) {
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
                            } catch (Throwable t) {
                                ExtendedAELogger.LOGGER.debug("[服务端] Interface 延迟等待期间唤醒设备失败: {}", t.toString());
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                    ExtendedAELogger.LOGGER.debug("[服务端] Interface 网格仍在引导，继续等待: ready={}, active={}, online={}",
                            mainNode.isReady(), mainNode.isActive(), mainNode.isOnline());
                }
            } else {
                // 网格已引导完成，执行初始化
                ExtendedAELogger.LOGGER.debug("[服务端] Interface 延迟初始化触发（网格已完成引导）");
                eap$initializeChannelLink();
            }
        }
    }
    
    // eap$initializeChannelLink方法已在上面实现
}
