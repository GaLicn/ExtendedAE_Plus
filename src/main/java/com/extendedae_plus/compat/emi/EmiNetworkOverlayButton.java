package com.extendedae_plus.compat.emi;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.config.ModConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * EMI 合成链（配方树）界面上的「AE2 网络库存显示」开关按钮。
 * <p>
 * 与 JEI 书签栏的 {@code JeiNetworkOverlayButton} 使用同一套贴图、同一配置项：
 * 按钮接在右侧 mode 按钮（"正在查看配方树"）之后，与左侧批量编码按钮 "A" 分列标题两侧。
 */
public final class EmiNetworkOverlayButton {
    /** 与 JEI 书签栏按钮同尺寸，贴图按 20x20 绘制。 */
    public static final int BUTTON_SIZE = 20;
    /** 与左侧 "A" 按钮对称的间距：mode 按钮右侧留 4px。 */
    public static final int BUTTON_GAP = 4;

    private static final ResourceLocation ENABLED_TEXTURE =
            ExtendedAEPlus.id("textures/gui/show_ae2_inventory_on.png");
    private static final ResourceLocation DISABLED_TEXTURE =
            ExtendedAEPlus.id("textures/gui/show_ae2_inventory_off.png");

    private EmiNetworkOverlayButton() {
    }

    public static void draw(GuiGraphics guiGraphics, int x, int y) {
        ResourceLocation texture = ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.get()
                ? ENABLED_TEXTURE
                : DISABLED_TEXTURE;
        guiGraphics.blit(texture, x, y, 0, 0, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE);
    }

    public static void drawTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String key = ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.get()
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

    /** 切换开关并播放点击音效，返回值仅表示已处理（调用方应吞掉该次点击）。 */
    public static void toggle() {
        ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.set(!ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.get());
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
