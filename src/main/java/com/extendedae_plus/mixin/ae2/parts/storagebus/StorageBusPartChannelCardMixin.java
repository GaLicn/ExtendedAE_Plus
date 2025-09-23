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
    private WirelessSlaveLink eap$link;
    
    @Unique
    private long eap$lastChannel = -1;
    
    @Unique
    private boolean eap$clientConnected = false;

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void eap$onUpgradesChanged(CallbackInfo ci) {
        // 只在服务端初始化频道链接
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            eap$initializeChannelLink();
        }
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void eap$onMainNodeStateChanged(IGridNodeListener.State reason, CallbackInfo ci) {
        // 在节点状态变化时（包括加载后的GRID_BOOT）重新初始化频道链接
        if (reason == IGridNodeListener.State.GRID_BOOT && !((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            eap$initializeChannelLink();
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$afterReadFromNBT(CompoundTag extra, CallbackInfo ci) {
        // 从NBT加载后也重新初始化频道链接（只在服务端）
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            // 从NBT加载时重置频道缓存，强制重新初始化
            eap$lastChannel = -1;
            eap$initializeChannelLink();
        }
    }

    @Unique
    public void eap$initializeChannelLink() {
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
            if (eap$lastChannel == channel) {
                return;
            }
            eap$lastChannel = channel;
            
            ExtendedAELogger.LOGGER.debug("[服务端] StorageBus 初始化频道链接: found={}, channel={}", found, channel);
            
            if (!found) {
                if (eap$link != null) {
                    eap$link.setFrequency(0L);
                    eap$link.updateStatus();
                    ExtendedAELogger.LOGGER.debug("[服务端] StorageBus 断开频道链接");
                    // 通知客户端状态变化
                    ((appeng.parts.AEBasePart)(Object)this).getHost().markForUpdate();
                }
                return;
            }
            
            if (eap$link == null) {
                var endpoint = new GenericNodeEndpointImpl(
                        () -> ((appeng.parts.AEBasePart)(Object)this).getHost().getBlockEntity(),
                        () -> ((IActionHost)(Object)this).getActionableNode()
                );
                eap$link = new WirelessSlaveLink(endpoint);
                ExtendedAELogger.LOGGER.debug("[服务端] StorageBus 创建新的无线链接");
            }
            
            eap$link.setFrequency(channel);
            eap$link.updateStatus();
            ExtendedAELogger.LOGGER.debug("[服务端] StorageBus 设置频道: {}, 连接状态: {}", channel, eap$link.isConnected());
            
            // 通知客户端状态变化
            ((appeng.parts.AEBasePart)(Object)this).getHost().markForUpdate();
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("[服务端] StorageBus 初始化频道链接失败", e);
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
        if (((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            return eap$clientConnected;
        } else {
            return eap$link != null && eap$link.isConnected();
        }
    }
    
    @Override
    public void eap$setClientWirelessState(boolean connected) {
        eap$clientConnected = connected;
    }
}
