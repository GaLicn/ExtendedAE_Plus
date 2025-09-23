package com.extendedae_plus.mixin.ae2.parts.automation;

import appeng.api.networking.security.IActionHost;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.InterfaceLogicHost;
import appeng.parts.automation.IOBusPart;
import net.minecraft.nbt.CompoundTag;
import com.extendedae_plus.util.ExtendedAELogger;
import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.wireless.WirelessSlaveLink;
import com.extendedae_plus.wireless.endpoint.GenericNodeEndpointImpl;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 AE2 的 I/O 总线注入频道卡联动：在升级变更时读取频道并更新无线链接。
 */
@Mixin(value = IOBusPart.class, remap = false)
public abstract class IOBusPartChannelCardMixin implements InterfaceWirelessLinkBridge, IUpgradeableObject {

    @Unique
    private WirelessSlaveLink extendedae_plus$link;
    
    @Unique
    private long extendedae_plus$lastChannel = -1;
    
    @Unique
    private boolean extendedae_plus$clientConnected = false;
    
    @Unique
    private boolean extendedae_plus$hasTickInitialized = false;

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void extendedae_plus$onUpgradesChanged(CallbackInfo ci) {
        // 只在服务端初始化频道链接
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            extendedae_plus$initializeChannelLink();
        }
    }

    @Inject(method = "tickingRequest", at = @At("HEAD"))
    private void extendedae_plus$beforeTick(appeng.api.networking.IGridNode node, int ticksSinceLastCall, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        // 在第一次tick时初始化频道链接（此时网格节点已经在线）
        if (!extendedae_plus$hasTickInitialized && !((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            extendedae_plus$hasTickInitialized = true;
            extendedae_plus$initializeChannelLink();
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void extendedae_plus$afterReadFromNBT(CompoundTag extra, CallbackInfo ci) {
        // 从NBT加载时重置频道缓存和tick初始化标志
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            extendedae_plus$lastChannel = -1;
            extendedae_plus$hasTickInitialized = false; // 重置标志，允许再次初始化
        }
    }

    @Unique
    private void extendedae_plus$initializeChannelLink() {
        // 防止重复调用
        if (((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            return;
        }
        
        try {
            IUpgradeInventory inv = this.getUpgrades();
            long channel = 0L;
            boolean found = false;
            for (var stack : inv) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                    channel = ChannelCardItem.getChannel(stack);
                    found = true;
                    break;
                }
            }
            
            // 频道没有变化则跳过
            if (extendedae_plus$lastChannel == channel) {
                return;
            }
            extendedae_plus$lastChannel = channel;
            
            ExtendedAELogger.LOGGER.debug("[服务端] IOBus 初始化频道链接: found={}, channel={}", found, channel);
            
            if (!found) {
                // 无频道卡则断开
                if (extendedae_plus$link != null) {
                    extendedae_plus$link.setFrequency(0L);
                    extendedae_plus$link.updateStatus();
                    ExtendedAELogger.LOGGER.debug("[服务端] IOBus 断开频道链接");
                    // 立即通知客户端状态变化（断开连接无需延迟）
                    ((appeng.parts.AEBasePart)(Object)this).getHost().markForUpdate();
                }
                return;
            }
            
            if (extendedae_plus$link == null) {
                var endpoint = new GenericNodeEndpointImpl(
                        () -> ((appeng.parts.AEBasePart)(Object)this).getHost().getBlockEntity(),
                        () -> ((IActionHost)(Object)this).getActionableNode()
                );
                extendedae_plus$link = new WirelessSlaveLink(endpoint);
                ExtendedAELogger.LOGGER.debug("[服务端] IOBus 创建新的无线链接");
            }
            
            extendedae_plus$link.setFrequency(channel);
            extendedae_plus$link.updateStatus();
            
            // 调试信息：检查网格节点状态
            var gridNode = ((IActionHost)(Object)this).getActionableNode();
            ExtendedAELogger.LOGGER.debug("[服务端] IOBus 设置频道: {}, 连接状态: {}, 网格节点: {}, 在线: {}", 
                channel, extendedae_plus$link.isConnected(), 
                gridNode != null ? "exists" : "null", 
                gridNode != null ? gridNode.isOnline() : "N/A");
            
            // 通知客户端状态变化
            ((appeng.parts.AEBasePart)(Object)this).getHost().markForUpdate();
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("[服务端] IOBus 初始化频道链接失败", e);
        }
    }

    @Override
    public void extendedae_plus$updateWirelessLink() {
        if (extendedae_plus$link != null) {
            extendedae_plus$link.updateStatus();
        }
    }
    
    @Override
    public boolean extendedae_plus$isWirelessConnected() {
        if (((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            return extendedae_plus$clientConnected;
        } else {
            return extendedae_plus$link != null && extendedae_plus$link.isConnected();
        }
    }
    
    @Override
    public void extendedae_plus$setClientWirelessState(boolean connected) {
        extendedae_plus$clientConnected = connected;
    }
}
