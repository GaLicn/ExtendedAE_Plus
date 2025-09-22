package com.extendedae_plus.mixin.ae2.parts.storagebus;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.parts.storagebus.StorageBusPart;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在存储总线的 tickingRequest 尾部驱动无线链接刷新。
 */
@Mixin(value = StorageBusPart.class, remap = false)
public abstract class StorageBusPartTickerChannelCardMixin {

    @Inject(method = "tickingRequest", at = @At("TAIL"))
    private void extendedae_plus$tickTail(IGridNode node, int ticksSinceLastCall, CallbackInfoReturnable<TickRateModulation> cir) {
        if (this instanceof InterfaceWirelessLinkBridge bridge) {
            bridge.extendedae_plus$updateWirelessLink();
        }
    }
}
