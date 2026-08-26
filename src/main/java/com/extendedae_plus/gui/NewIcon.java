package com.extendedae_plus.gui;

import appeng.client.gui.style.Blitter;
import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.resources.ResourceLocation;

public class NewIcon {
    @SuppressWarnings("all")
    private static final ResourceLocation TEXTURE = new ResourceLocation(ExtendedAEPlus.MODID,"textures/gui/nicons.png");



    public static final Blitter MULTIPLY2;
    public static final Blitter DIVIDE2;
    public static final Blitter MULTIPLY5;
    public static final Blitter DIVIDE5;
    public static final Blitter MULTIPLY10;
    public static final Blitter DIVIDE10;
    public static final Blitter SHOW_PATTERN_SCALING_CONTROLS;
    public static final Blitter HIDE_PATTERN_SCALING_CONTROLS;
    private static final ResourceLocation SHOW_PATTERN_SCALING_CONTROLS_TEXTURE =
            new ResourceLocation(ExtendedAEPlus.MODID, "textures/gui/show_pattern_scaling_controls.png");
    private static final ResourceLocation HIDE_PATTERN_SCALING_CONTROLS_TEXTURE =
            new ResourceLocation(ExtendedAEPlus.MODID, "textures/gui/hide_pattern_scaling_controls.png");

    static {
        MULTIPLY2 = Blitter.texture(TEXTURE, 64, 64).src(32, 0, 16, 16);
        DIVIDE2 = Blitter.texture(TEXTURE, 64, 64).src(48, 0, 16, 16);
        MULTIPLY5 = Blitter.texture(TEXTURE, 64, 64).src(0, 0, 16, 16);
        DIVIDE5 = Blitter.texture(TEXTURE, 64, 64).src(16, 0, 16, 16);
        MULTIPLY10 = Blitter.texture(TEXTURE, 64, 64).src(0, 16, 16, 16);
        DIVIDE10 = Blitter.texture(TEXTURE, 64, 64).src(16, 16, 16, 16);
        SHOW_PATTERN_SCALING_CONTROLS = Blitter.texture(SHOW_PATTERN_SCALING_CONTROLS_TEXTURE, 16, 16).src(0, 0, 16, 16);
        HIDE_PATTERN_SCALING_CONTROLS = Blitter.texture(HIDE_PATTERN_SCALING_CONTROLS_TEXTURE, 16, 16).src(0, 0, 16, 16);

    }
}
