package com.extendedae_plus.client.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.config.ModConfigs;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class JeiNetworkOverlayButton {
    private static final int BUTTON_SIZE = 20;
    private static final ResourceLocation ENABLED_TEXTURE =
            ExtendedAEPlus.id("textures/gui/show_ae2_inventory_on.png");
    private static final ResourceLocation DISABLED_TEXTURE =
            ExtendedAEPlus.id("textures/gui/show_ae2_inventory_off.png");

    /**
     * 当前生效的按钮实例。按钮由 {@code BookmarkOverlay} 构造时创建，
     * 输入监听器通过该静态引用找到它，避免依赖 JEI 的内部输入接口。
     */
    private static JeiNetworkOverlayButton active;

    private ImmutableRect2i area = ImmutableRect2i.EMPTY;
    private boolean visible;

    public JeiNetworkOverlayButton() {
        active = this;
    }

    /**
     * 由 {@code ScreenEvent.MouseButtonPressed.Pre} 调用。
     *
     * @return true 表示点击落在按钮上并已被消费，调用方应取消事件
     */
    public static boolean handleClick(double mouseX, double mouseY, int button) {
        JeiNetworkOverlayButton target = active;
        return target != null && target.tryClick(mouseX, mouseY, button);
    }

    private boolean tryClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !this.visible || !this.area.contains(mouseX, mouseY)) {
            return false;
        }
        ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.set(!ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.get());
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    public void updateBounds(ImmutableRect2i area) {
        this.area = area;
    }

    public void draw(GuiGraphics guiGraphics) {
        if (this.area.isEmpty()) {
            return;
        }
        this.visible = true;
        ResourceLocation texture = ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.get()
                ? ENABLED_TEXTURE
                : DISABLED_TEXTURE;
        guiGraphics.blit(texture, this.area.x(), this.area.y(), 0, 0,
                BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE);
    }

    public void drawTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.visible || !this.area.contains(mouseX, mouseY)) {
            return;
        }
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

    /**
     * 由 JEI 每帧绘制的入口调用，清除上一帧的可见状态。
     * 若不清除，JEI 隐藏书签栏后按钮不再绘制，旧坐标仍会吃掉鼠标点击。
     */
    public void beginFrame() {
        this.visible = false;
    }
}
