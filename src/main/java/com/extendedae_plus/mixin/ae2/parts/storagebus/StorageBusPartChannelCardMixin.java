package com.extendedae_plus.mixin.ae2.parts.storagebus;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.parts.storagebus.StorageBusPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.extendedae_plus.util.ExtendedAELogger;
import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.wireless.WirelessSlaveLink;
import com.extendedae_plus.wireless.endpoint.GenericNodeEndpointImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 AE2 的存储总线注入频道卡联动：在升级变更时读取频道并更新无线链接。
 */
@Mixin(value = StorageBusPart.class, remap = false)
public abstract class StorageBusPartChannelCardMixin implements InterfaceWirelessLinkBridge, IUpgradeableObject {

    @Unique
    private WirelessSlaveLink extendedae_plus$link;
    
    @Unique
    private long extendedae_plus$lastChannel = -1;
    
    @Unique
    private boolean extendedae_plus$clientConnected = false;

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void extendedae_plus$onUpgradesChanged(CallbackInfo ci) {
        // 只在服务端初始化频道链接
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            extendedae_plus$initializeChannelLink();
        }
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void extendedae_plus$onMainNodeStateChanged(IGridNodeListener.State reason, CallbackInfo ci) {
        // 在节点状态变化时（包括加载后的GRID_BOOT）重新初始化频道链接
        if (reason == IGridNodeListener.State.GRID_BOOT && !((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            extendedae_plus$initializeChannelLink();
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void extendedae_plus$afterReadFromNBT(CompoundTag extra, CallbackInfo ci) {
        // 从NBT加载后也重新初始化频道链接（只在服务端）
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            // 从NBT加载时重置频道缓存，强制重新初始化
            extendedae_plus$lastChannel = -1;
            extendedae_plus$initializeChannelLink();
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
            
            ExtendedAELogger.LOGGER.debug("[服务端] StorageBus 初始化频道链接: found={}, channel={}", found, channel);
            
            if (!found) {
                if (extendedae_plus$link != null) {
                    extendedae_plus$link.setFrequency(0L);
                    extendedae_plus$link.updateStatus();
                    ExtendedAELogger.LOGGER.debug("[服务端] StorageBus 断开频道链接");
                    // 通知客户端状态变化
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
                ExtendedAELogger.LOGGER.debug("[服务端] StorageBus 创建新的无线链接");
            }
            
            extendedae_plus$link.setFrequency(channel);
            extendedae_plus$link.updateStatus();
            
            // 调试信息：检查网格节点状态
            var gridNode = ((IActionHost)(Object)this).getActionableNode();
            ExtendedAELogger.LOGGER.debug("[服务端] StorageBus 设置频道: {}, 连接状态: {}, 网格节点: {}, 在线: {}", 
                channel, extendedae_plus$link.isConnected(), 
                gridNode != null ? "exists" : "null", 
                gridNode != null ? gridNode.isOnline() : "N/A");
            
            // 通知客户端状态变化
            ((appeng.parts.AEBasePart)(Object)this).getHost().markForUpdate();
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("[服务端] StorageBus 初始化频道链接失败", e);
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
