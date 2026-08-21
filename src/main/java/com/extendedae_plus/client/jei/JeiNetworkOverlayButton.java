package com.extendedae_plus.client.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.config.ModConfigs;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.Optional;
import java.util.List;

public final class JeiNetworkOverlayButton implements IUserInputHandler {
    private static final int BUTTON_SIZE = 20;
    private static final ResourceLocation ENABLED_TEXTURE =
            ExtendedAEPlus.id("textures/gui/show_ae2_inventory_on.png");
    private static final ResourceLocation DISABLED_TEXTURE =
            ExtendedAEPlus.id("textures/gui/show_ae2_inventory_off.png");

    private ImmutableRect2i area = ImmutableRect2i.EMPTY;
    private boolean visible;

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

    public void beginFrame() {
        this.visible = false;
    }

    @Override
    public Optional<IUserInputHandler> handleUserInput(
            Screen screen,
            UserInput input,
            IInternalKeyMappings keyBindings
    ) {
        if (input.getKey().getType() != InputConstants.Type.MOUSE
                || input.getKey().getValue() != 0
                || !this.visible
                || !this.area.contains(input.getMouseX(), input.getMouseY())) {
            return Optional.empty();
        }
        if (!input.isSimulate()) {
            // JEI 会先模拟输入，只有实际点击阶段才切换并播放声音。
            ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.set(!ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.get());
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return Optional.of(this);
    }
}
