package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.networking.ticking.TickRateModulation;
import appeng.helpers.InterfaceLogic;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * 注入到 InterfaceLogic.Ticker 的每tick回调，驱动无线链接状态更新。
 */
@Mixin(targets = "appeng.helpers.InterfaceLogic$Ticker", remap = false)
public abstract class InterfaceLogicTickerMixin {

    @Unique
    private static Field extendedae_plus$outerField;

    @Unique
    private InterfaceLogic extendedae_plus$getOuterLogic() {
        try {
            if (extendedae_plus$outerField == null) {
                extendedae_plus$outerField = this.getClass().getDeclaredField("this$0");
                extendedae_plus$outerField.setAccessible(true);
            }
            return (InterfaceLogic) extendedae_plus$outerField.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access InterfaceLogic outer instance", e);
        }
    }

    @Inject(method = "tickingRequest", at = @At("HEAD"), remap = false)
    private void eap$tickHead(appeng.api.networking.IGridNode node, int ticksSinceLastCall,
                                          CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        // 仅在服务端处理延迟初始化，避免客户端干扰
        if (node != null && node.getLevel() != null && node.getLevel().isClientSide) {
            return;
        }
        
        if (this.extendedae_plus$getOuterLogic() instanceof IInterfaceWirelessLinkBridge bridge) {
            // 处理延迟初始化
            bridge.eap$handleDelayedInit();
        }
    }
    
    @Inject(method = "tickingRequest", at = @At("TAIL"), remap = false, cancellable = true)
    private void eap$tickTail(appeng.api.networking.IGridNode node, int ticksSinceLastCall,
                                          CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        if (node != null && node.getLevel() != null && node.getLevel().isClientSide) {
            return;
        }
        if (this.extendedae_plus$getOuterLogic() instanceof IInterfaceWirelessLinkBridge bridge) {
            if (bridge.eap$shouldKeepTicking() && cir.getReturnValue() == TickRateModulation.SLEEP) {
                cir.setReturnValue(TickRateModulation.SLOWER);
            }
        }
    }
}
