package com.extendedae_plus.compat.emi;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/** EMI 左下角的网络库存开关，与 JEI 共用贴图和配置。 */
public final class EmiNetworkOverlayButton {
    public static final int BUTTON_SIZE = 20;

    private static final ResourceLocation ENABLED_TEXTURE =
            ExtendedAEPlus.id("textures/gui/show_ae2_inventory_on.png");
    private static final ResourceLocation DISABLED_TEXTURE =
            ExtendedAEPlus.id("textures/gui/show_ae2_inventory_off.png");

    private EmiNetworkOverlayButton() {
    }

    public static void draw(GuiGraphics guiGraphics, int x, int y) {
        ResourceLocation texture = ModConfig.INSTANCE.jeiNetworkOverlayEnabled
                ? ENABLED_TEXTURE
                : DISABLED_TEXTURE;
        guiGraphics.blit(texture, x, y, 0, 0, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE);
    }

    public static void drawTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String key = ModConfig.INSTANCE.jeiNetworkOverlayEnabled
                ? "tooltip.extendedae_plus.jei_network_overlay.enabled"
                : "tooltip.extendedae_plus.jei_network_overlay.disabled";
        guiGraphics.renderComponentTooltip(
                Minecraft.getInstance().font,
                List.of(
                        Component.translatable(key),
                        Component.translatable("tooltip.extendedae_plus.jei_network_overlay.description")
                                .withStyle(ChatFormatting.GRAY)
                ),
                mouseX,
                mouseY
        );
    }

    /** 即时切换共享配置并播放点击音效。 */
    public static void toggle() {
        ModConfig.INSTANCE.jeiNetworkOverlayEnabled = !ModConfig.INSTANCE.jeiNetworkOverlayEnabled;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
