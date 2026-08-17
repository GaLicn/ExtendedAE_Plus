package com.extendedae_plus.mixin.ae2.parts;

import appeng.parts.AEBasePart;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 为所有已接入频道卡桥的 AE 部件统一转发进出世界生命周期。 */
@Mixin(value = AEBasePart.class, remap = false)
public abstract class ChannelCardPartLifecycleMixin {
    @Inject(method = "addToWorld", at = @At("TAIL"))
    private void eap$loadChannelCardController(CallbackInfo ci) {
        if ((Object) this instanceof InterfaceWirelessLinkBridge bridge) {
            var controller = bridge.eap$getChannelCardController();
            if (controller != null) controller.onLoaded();
        }
    }

    @Inject(method = "removeFromWorld", at = @At("HEAD"))
    private void eap$unloadChannelCardController(CallbackInfo ci) {
        if ((Object) this instanceof InterfaceWirelessLinkBridge bridge) {
            var controller = bridge.eap$getChannelCardController();
            if (controller != null) controller.onUnloaded();
        }
    }
}
