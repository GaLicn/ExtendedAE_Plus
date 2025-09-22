package com.extendedae_plus.mixin.ae2.parts.storagebus;

import appeng.api.networking.security.IActionHost;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.parts.storagebus.StorageBusPart;
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

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void extendedae_plus$onUpgradesChanged(CallbackInfo ci) {
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
        if (!found) {
            if (extendedae_plus$link != null) {
                extendedae_plus$link.setFrequency(0L);
                extendedae_plus$link.updateStatus();
            }
            return;
        }
        if (extendedae_plus$link == null) {
            var endpoint = new GenericNodeEndpointImpl(
                    () -> ((appeng.parts.AEBasePart)(Object)this).getHost().getBlockEntity(),
                    () -> ((IActionHost)(Object)this).getActionableNode()
            );
            extendedae_plus$link = new WirelessSlaveLink(endpoint);
        }
        extendedae_plus$link.setFrequency(channel);
        extendedae_plus$link.updateStatus();
    }

    @Override
    public void extendedae_plus$updateWirelessLink() {
        if (extendedae_plus$link != null) {
            extendedae_plus$link.updateStatus();
        }
    }
}
