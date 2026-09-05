package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.client.gui.implementations.PatternProviderScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GTOCore 已为扩展供应器提供分页，禁用其重复的滚动条。 */
@Mixin(value = PatternProviderScreen.class, priority = 900)
public abstract class GtoCorePatternProviderScrollMixin {

    @Inject(method = "gto$ae$shouldAddScrollBar", at = @At("HEAD"),
            remap = false, require = 0, cancellable = true)
    private void eap$disableDuplicateScrollBar(CallbackInfoReturnable<Boolean> cir) {
        // GTOCore 仅会对扩展样板供应器返回 true，统一返回 false 可保留其它界面的原状。
        cir.setReturnValue(false);
    }
}
