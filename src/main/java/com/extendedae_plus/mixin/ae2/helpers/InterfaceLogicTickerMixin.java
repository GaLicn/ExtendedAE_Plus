package com.extendedae_plus.mixin.ae2.helpers;

import appeng.helpers.InterfaceLogic;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入到 InterfaceLogic.Ticker 的每tick回调，驱动无线链接状态更新。
 */
@Mixin(targets = "appeng.helpers.InterfaceLogic$Ticker")
public abstract class InterfaceLogicTickerMixin {

    // Mixin 访问内部类的外部引用字段（javac 生成名 this$0）
    @Shadow(remap = false)
    @Final
    private InterfaceLogic this$0;

    @Inject(method = "tickingRequest", at = @At("TAIL"), remap = false)
    private void extendedae_plus$tickTail(appeng.api.networking.IGridNode node, int ticksSinceLastCall,
                                          CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        if (this$0 instanceof InterfaceWirelessLinkBridge bridge) {
            bridge.extendedae_plus$updateWirelessLink();
        }
    }
}
