package com.extendedae_plus.mixin.ae2.parts;

import appeng.parts.AEBasePart;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 部件加入/移出世界时统一驱动频道卡控制器。 */
@Mixin(value = AEBasePart.class, remap = false)
public abstract class ChannelCardPartLifecycleMixin {
    @Inject(method = "addToWorld", at = @At("TAIL"))
    private void eap$loadChannelController(CallbackInfo ci) {
        if ((Object) this instanceof IInterfaceWirelessLinkBridge bridge
                && bridge.eap$getChannelCardController() != null) {
            bridge.eap$getChannelCardController().onLoaded();
        }
    }

    @Inject(method = "removeFromWorld", at = @At("HEAD"))
    private void eap$unloadChannelController(CallbackInfo ci) {
        if ((Object) this instanceof IInterfaceWirelessLinkBridge bridge
                && bridge.eap$getChannelCardController() != null) {
            bridge.eap$getChannelCardController().onUnloaded();
        }
    }
}
