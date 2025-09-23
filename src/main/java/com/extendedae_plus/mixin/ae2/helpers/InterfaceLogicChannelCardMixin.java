package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.init.ModItems;
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

    @Unique
    private WirelessSlaveLink eap$link;

    @Inject(method = "onUpgradesChanged", at = @At("TAIL"), remap = false)
    private void eap$onUpgradesChangedTail(CallbackInfo ci) {
        handleChannelCardChange();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void eap$afterReadNBT(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
        // 重载后根据卡状态恢复连接
        handleChannelCardChange();
    }

    @Inject(method = "clearContent", at = @At("HEAD"), remap = false)
    private void eap$onClearContent(CallbackInfo ci) {
        if (eap$link != null) {
            eap$link.onUnloadOrRemove();
        }
    }

    @Unique
    private void handleChannelCardChange() {
        var inv = getUpgrades();
        long channel = 0L;
        for (ItemStack stack : inv) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                channel = ChannelCardItem.getChannel(stack);
                break;
            }
        }
        if (eap$link == null) {
            IWirelessEndpoint endpoint = new InterfaceNodeEndpointImpl(host, this::getActionableNode);
            eap$link = new WirelessSlaveLink(endpoint);
        }
        eap$link.setFrequency(channel);
    }

    @Override
    public void eap$updateWirelessLink() {
        if (eap$link != null) {
            eap$link.updateStatus();
        }
    }
}
