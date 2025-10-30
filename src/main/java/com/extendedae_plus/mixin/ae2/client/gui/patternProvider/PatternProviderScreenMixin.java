package com.extendedae_plus.mixin.ae2.client.gui.patternProvider;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {
    public PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /**
     * 显示样板供应器的customName
     */
    @Inject(method = "updateBeforeRender", at = @At("RETURN"), remap = false)
    private void onUpdateBeforeRender(CallbackInfo ci) {
        Component t = this.getTitle();
        if (!t.getString().isEmpty()) {
            this.setTextContent(AEBaseScreen.TEXT_ID_DIALOG_TITLE, t);
        }
    }
}
