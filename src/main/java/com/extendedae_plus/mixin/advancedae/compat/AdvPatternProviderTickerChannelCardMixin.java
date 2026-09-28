package com.extendedae_plus.mixin.advancedae.compat;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在供应器离线时仍保留低频 tick，供频道卡建立无线连接。 */
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic$Ticker", remap = false)
public abstract class AdvPatternProviderTickerChannelCardMixin {
    @Shadow(aliases = "this$0") @Final private AdvPatternProviderLogic this$0;

    @Inject(method = "getTickingRequest", at = @At("RETURN"), cancellable = true)
    private void eap$keepChannelCardTickerAwake(
            IGridNode node, CallbackInfoReturnable<TickingRequest> cir) {
        if ((Object) this.this$0 instanceof InterfaceWirelessLinkBridge bridge
                && bridge.eap$shouldKeepTicking()) {
            var original = cir.getReturnValue();
            cir.setReturnValue(new TickingRequest(
                    original.minTickRate(), original.maxTickRate(), false, original.initialTickRate()));
        }
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true)
    private void eap$maintainChannelCardLink(
            IGridNode node, int ticksSinceLastCall, CallbackInfoReturnable<TickRateModulation> cir) {
        if ((Object) this.this$0 instanceof InterfaceWirelessLinkBridge bridge) {
            bridge.eap$handleDelayedInit();
            bridge.eap$updateWirelessLink();
            if (bridge.eap$shouldKeepTicking() && cir.getReturnValue() == TickRateModulation.SLEEP) {
                cir.setReturnValue(TickRateModulation.SLOWER);
            }
        }
    }
}
