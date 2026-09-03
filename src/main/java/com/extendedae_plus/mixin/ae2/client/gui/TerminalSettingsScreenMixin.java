package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.widgets.AECheckbox;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the JEI network inventory overlay preference to AE2's terminal settings screen. */
@Mixin(value = TerminalSettingsScreen.class, remap = false)
public abstract class TerminalSettingsScreenMixin {

    @Unique
    private AECheckbox eap$jeiNetworkOverlayCheckbox;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$addJeiNetworkOverlaySetting(CallbackInfo ci) {
        var widgets = ((AEBaseScreenAccessor<?>) this).eap$getWidgets();
        this.eap$jeiNetworkOverlayCheckbox = widgets.addCheckbox(
                "searchTooltipsCheckbox",
                Component.translatable("gui.extendedae_plus.terminal_settings.jei_network_overlay"),
                this::eap$saveJeiNetworkOverlaySetting
        );
        this.eap$jeiNetworkOverlayCheckbox.setSelected(ModConfig.INSTANCE.jeiNetworkOverlayEnabled);
        this.eap$jeiNetworkOverlayCheckbox.active = ModList.get().isLoaded("jei");
    }

    @Unique
    private void eap$saveJeiNetworkOverlaySetting() {
        ModConfig.setJeiNetworkOverlayEnabled(this.eap$jeiNetworkOverlayCheckbox.isSelected());
    }
}
