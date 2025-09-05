package com.extendedae_plus.mixin.ae2.accessor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AEBaseScreen.class, remap = false)
public interface AEBaseScreenAccessor<T extends AEBaseMenu> {
    @Accessor(value = "style", remap = false)
    ScreenStyle eap$getStyle();
}
