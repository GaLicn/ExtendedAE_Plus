package com.extendedae_plus.mixin.advancedae.compat;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import java.lang.reflect.Field;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 高级供应器离线时维持低频 tick，直到频道卡连接成功。 */
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic$Ticker", remap = false)
public abstract class AdvPatternProviderTickerChannelCardMixin {
    @Unique private static Field eap$outerField;

    @Unique
    private AdvPatternProviderLogic eap$getLogic() {
        try {
            if (eap$outerField == null) {
                eap$outerField = this.getClass().getDeclaredField("this$0");
                eap$outerField.setAccessible(true);
            }
            return (AdvPatternProviderLogic) eap$outerField.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法访问 AdvancedAE 供应器逻辑实例", e);
        }
    }

    @Inject(method = "getTickingRequest", at = @At("RETURN"), cancellable = true)
    private void eap$keepTickerAwake(IGridNode node, CallbackInfoReturnable<TickingRequest> cir) {
        if (this.eap$getLogic() instanceof IInterfaceWirelessLinkBridge bridge
                && bridge.eap$shouldKeepTicking()) {
            var original = cir.getReturnValue();
            cir.setReturnValue(new TickingRequest(
                    original.minTickRate(), original.maxTickRate(), false, original.canBeAlerted()));
        }
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true)
    private void eap$maintainLink(IGridNode node, int ticksSinceLastCall,
                                   CallbackInfoReturnable<TickRateModulation> cir) {
        if (this.eap$getLogic() instanceof IInterfaceWirelessLinkBridge bridge) {
            bridge.eap$handleDelayedInit();
            if (bridge.eap$shouldKeepTicking() && cir.getReturnValue() == TickRateModulation.SLEEP) {
                cir.setReturnValue(TickRateModulation.SLOWER);
            }
        }
    }
}
