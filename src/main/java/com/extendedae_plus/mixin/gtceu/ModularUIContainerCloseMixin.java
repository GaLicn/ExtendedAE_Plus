package com.extendedae_plus.mixin.gtceu;

import com.extendedae_plus.content.ClientPatternHighlightStore;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModularUIContainer.class, remap = false)
public class ModularUIContainerCloseMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        try {
            ClientPatternHighlightStore.clearAll();
        } catch (Throwable ignored) {}
    }
}