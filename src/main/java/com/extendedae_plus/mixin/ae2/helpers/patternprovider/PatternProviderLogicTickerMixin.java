package com.extendedae_plus.mixin.ae2.helpers.patternprovider;

import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入到 PatternProviderLogic.Ticker 的每tick回调，驱动无线链接状态更新。
 */
@Mixin(targets = "appeng.helpers.patternprovider.PatternProviderLogic$Ticker", remap = false)
public abstract class PatternProviderLogicTickerMixin {

    // Mixin 访问内部类的外部引用字段（javac 生成名 this$0）
    @Shadow(remap = false)
    @Final
    private PatternProviderLogic this$0;

    @Inject(method = "tickingRequest", at = @At("HEAD"))
    private void eap$tickHead(appeng.api.networking.IGridNode node, int ticksSinceLastCall,
                              CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        // 仅在服务端处理延迟初始化
        if (node != null && node.getLevel() != null && node.getLevel().isClientSide) {
            return;
        }
        if (this$0 instanceof InterfaceWirelessLinkBridge bridge) {
            bridge.eap$handleDelayedInit();
        }
    }

    @Inject(method = "tickingRequest", at = @At("TAIL"))
    private void eap$tickTail(appeng.api.networking.IGridNode node, int ticksSinceLastCall,
                                          CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        if (this$0 instanceof InterfaceWirelessLinkBridge bridge) {
            bridge.eap$updateWirelessLink();
        }
    }
}
