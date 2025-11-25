package com.extendedae_plus.mixin.ae2.parts.automation;

import appeng.api.networking.security.IActionHost;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.parts.automation.IOBusPart;
import com.extendedae_plus.ae.wireless.WirelessSlaveLink;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.materials.ChannelCardItem;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
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
    private WirelessSlaveLink eap$link;
    
    @Unique
    private long eap$lastChannel = -1;
    
    @Unique
    private UUID eap$lastOwner;
    
    @Unique
    private boolean eap$clientConnected = false;
    
    @Unique
    private boolean eap$hasTickInitialized = false;

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void eap$onUpgradesChanged(CallbackInfo ci) {
        // 只在服务端初始化频道链接
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            this.eap$initializeChannelLink();
        }
    }

    @Inject(method = "tickingRequest", at = @At("HEAD"))
    private void eap$beforeTick(appeng.api.networking.IGridNode node, int ticksSinceLastCall, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        // 在第一次tick时初始化频道链接（此时网格节点已经在线）
        if (!this.eap$hasTickInitialized && !((appeng.parts.AEBasePart) (Object) this).isClientSide()) {
            this.eap$hasTickInitialized = true;
            this.eap$initializeChannelLink();
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$afterReadFromNBT(CompoundTag extra, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        // 从NBT加载时重置频道缓存和tick初始化标志
        if (!((appeng.parts.AEBasePart)(Object)this).isClientSide()) {
            this.eap$lastChannel = -1;
            this.eap$hasTickInitialized = false; // 重置标志，允许再次初始化
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
        if (((appeng.parts.AEBasePart) (Object) this).isClientSide()) {
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
            UUID owner = null;
            for (var stack : inv) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                    channel = ChannelCardItem.getChannel(stack);
                    owner = ChannelCardItem.getOwnerUUID(stack);
                    if (owner == null) {
                        owner = this.eap$getFallbackOwner();
                    }
                    found = true;
                    break;
                }
            }

            // 频道没有变化则跳过
            boolean sameOwner = (this.eap$lastOwner == null && owner == null)
                    || (this.eap$lastOwner != null && this.eap$lastOwner.equals(owner));
            if (this.eap$link != null && this.eap$lastChannel == channel && sameOwner) {
                return;
            }
            this.eap$lastChannel = channel;


            if (!found) {
                // 无频道卡则断开
                if (this.eap$link != null) {
                    this.eap$link.setPlacerId(null);
                    this.eap$link.setFrequency(0L);
                    this.eap$link.updateStatus();
                    // 立即通知客户端状态变化（断开连接无需延迟）
                    ((appeng.parts.AEBasePart)(Object)this).getHost().markForUpdate();
                }
                this.eap$lastChannel = 0L;
                this.eap$lastOwner = null;
                return;
            }

            if (this.eap$link == null) {
                var endpoint = new GenericNodeEndpointImpl(
                        () -> ((appeng.parts.AEBasePart)(Object)this).getHost().getBlockEntity(),
                        () -> ((IActionHost)(Object)this).getActionableNode()
                );
                this.eap$link = new WirelessSlaveLink(endpoint);
            }

            this.eap$link.setPlacerId(owner);
            this.eap$link.setFrequency(channel);
            this.eap$link.updateStatus();
            this.eap$lastOwner = owner;

            // 通知客户端状态变化
            ((appeng.parts.AEBasePart)(Object)this).getHost().markForUpdate();
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("[服务端] IOBus 初始化频道链接失败", e);
        }
    }

    @Unique
    private UUID eap$getFallbackOwner() {
        try {
            var node = ((IActionHost)(Object)this).getActionableNode();
            if (node != null) {
                return node.getOwningPlayerProfileId();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
