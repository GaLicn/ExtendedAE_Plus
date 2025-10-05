package com.extendedae_plus.mixin.ae2.parts.automation;

import appeng.api.networking.security.IActionHost;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.parts.automation.IOBusPart;
import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.ae.wireless.WirelessSlaveLink;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.util.Logger;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 AE2 的 I/O 总线注入频道卡联动：在升级变更时读取频道并更新无线链接。
 */
@Mixin(value = IOBusPart.class, remap = false)
public abstract class IOBusPartChannelCardMixin implements IInterfaceWirelessLinkBridge, IUpgradeableObject {

    @Unique
    private WirelessSlaveLink eap$link;
    
    @Unique
    private long eap$lastChannel = -1;
    
    @Unique
    private boolean eap$clientConnected = false;
    
    @Unique
    private boolean eap$hasTickInitialized = false;

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void eap$onUpgradesChanged(CallbackInfo ci) {
        // 只在服务端初始化频道链接
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            eap$initializeChannelLink();
        }
    }

    @Inject(method = "tickingRequest", at = @At("HEAD"))
    private void eap$beforeTick(appeng.api.networking.IGridNode node, int ticksSinceLastCall, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        // 在第一次tick时初始化频道链接（此时网格节点已经在线）
        if (!eap$hasTickInitialized && !((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            eap$hasTickInitialized = true;
            eap$initializeChannelLink();
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$afterReadFromNBT(CompoundTag extra, CallbackInfo ci) {
        // 从NBT加载时重置频道缓存和tick初始化标志
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            eap$lastChannel = -1;
            eap$hasTickInitialized = false; // 重置标志，允许再次初始化
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
            java.util.UUID ownerUUID = null;
            boolean found = false;
            for (var stack : inv) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                    channel = ChannelCardItem.getChannel(stack);
                    ownerUUID = ChannelCardItem.getOwnerUUID(stack);
                    found = true;
                    break;
                }
            }
            
            // 频道没有变化则跳过
            if (eap$lastChannel == channel) {
                return;
            }
            eap$lastChannel = channel;
            
            Logger.EAP$LOGGER.debug("[服务端] IOBus 初始化频道链接: found={}, channel={}", found, channel);
            
            if (!found) {
                // 无频道卡则断开
                if (eap$link != null) {
                    eap$link.setFrequency(0L);
                    eap$link.updateStatus();
                    Logger.EAP$LOGGER.debug("[服务端] IOBus 断开频道链接");
                    // 立即通知客户端状态变化（断开连接无需延迟）
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
                Logger.EAP$LOGGER.debug("[服务端] IOBus 创建新的无线链接");
            }
            
            // 设置频道卡的所有者UUID（如果有的话）
            eap$link.setPlacerId(ownerUUID);
            eap$link.setFrequency(channel);
            eap$link.updateStatus();
            Logger.EAP$LOGGER.debug("[服务端] IOBus 设置频道: {}, 连接状态: {}", channel, eap$link.isConnected());
            
            // 通知客户端状态变化
            ((appeng.parts.AEBasePart)(Object)this).getHost().markForUpdate();
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("[服务端] IOBus 初始化频道链接失败", e);
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
