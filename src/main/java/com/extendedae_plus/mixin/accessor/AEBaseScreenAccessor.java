package com.extendedae_plus.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;

@Mixin(value = AEBaseScreen.class, remap = false)
public interface AEBaseScreenAccessor<T extends AEBaseMenu> {
    @Accessor(value = "style", remap = false)
    ScreenStyle extendedae_plus$getStyle();
}
