package com.extendedae_plus.mixin.ae2.parts;

import appeng.parts.AEBasePart;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
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
            if (controller == null) {
                return;
            }
            controller.onLoaded();
            // 卸载时摘过条目，重新进世界要重新挂回宿主，否则之后方块实体被移除就漏掉了这个控制器。
            eap$reRegisterController(controller);
        }
    }

    @Inject(method = "removeFromWorld", at = @At("HEAD"))
    private void eap$unloadChannelCardController(CallbackInfo ci) {
        if ((Object) this instanceof InterfaceWirelessLinkBridge bridge) {
            var controller = bridge.eap$getChannelCardController();
            if (controller == null) {
                return;
            }
            controller.onUnloaded();
            // 部件被单独拆掉时宿主方块实体仍然存活，必须摘掉自己的条目，
            // 否则长期存活的缆线总线会一直累积已失效的控制器。
            var host = ((AEBasePart) (Object) this).getHost();
            if (host != null) {
                ChannelCardConnectionController.unregister(host.getBlockEntity(), controller);
            }
        }
    }

    private void eap$reRegisterController(ChannelCardConnectionController controller) {
        var host = ((AEBasePart) (Object) this).getHost();
        if (host != null) {
            ChannelCardConnectionController.register(host.getBlockEntity(), controller);
        }
    }
}
