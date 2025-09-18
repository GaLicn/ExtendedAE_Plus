package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.implementations.PatternProviderScreen;
import com.extendedae_plus.content.ClientPatternHighlightStore;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContainerScreen.class)
public class PatternProviderCloseMixin {
	@Inject(method = "removed", at = @At("HEAD"))
	private void onRemoved(CallbackInfo ci) {
		try {
			if (((Object) this) instanceof PatternProviderScreen) {
				ClientPatternHighlightStore.clearAll();
			}
		} catch (Throwable ignored) {
		}
	}
}