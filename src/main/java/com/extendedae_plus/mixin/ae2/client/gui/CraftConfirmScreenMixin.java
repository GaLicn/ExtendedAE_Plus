package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.core.localization.GuiText;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.mixin.ae2.accessor.WidgetContainerAccessor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = CraftConfirmScreen.class, remap = false)
public class CraftConfirmScreenMixin {

    @Unique
    private static final Component EAP_CANCEL_TEXT = GuiText.Cancel.text();
    @Unique
    private static final Component EAP_BOOKMARK_TEXT = Component.translatable("gui.extendedae_plus.add_bookmark");
    @Unique
    private static final Component EAP_BOOKMARK_TOOLTIP = Component.translatable("tooltip.extendedae_plus.add_missing_to_jei_bookmark");

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void eap$updateCancelButtonText(CallbackInfo ci) {
        if (!ModList.get().isLoaded("jei")){
            return;
        }

        CraftConfirmScreen self = (CraftConfirmScreen) (Object) this;
        try {
            WidgetContainer widgets = ((AEBaseScreenAccessor<?>) self).eap$getWidgets();
            if (widgets == null) return;

            AbstractWidget cancelWidget = ((WidgetContainerAccessor) widgets).eap$getWidgetsMap().get("cancel");
            if (!(cancelWidget instanceof Button cancelButton)) return;

            boolean shiftDown = Screen.hasShiftDown();

            if (shiftDown) {
                cancelButton.setMessage(EAP_BOOKMARK_TEXT);
                cancelButton.setTooltip(Tooltip.create(EAP_BOOKMARK_TOOLTIP));
            } else {
                cancelButton.setMessage(EAP_CANCEL_TEXT);
                cancelButton.setTooltip(null);
            }
        } catch (Throwable ignored) {
        }
    }
}
