package com.extendedae_plus.client.gui;

import appeng.client.gui.style.Blitter;
import net.minecraft.resources.ResourceLocation;

/**
 * PatternBetter功能的图标定义
 * 复刻自PatternBetter模组的NewIcon类
 */
public class PatternBetterIcons {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("extendedae_plus", "textures/gui/pattern_better_icons.png");

    public static final Blitter MULTIPLY2;
    public static final Blitter DIVIDE2;
    public static final Blitter MULTIPLY5;
    public static final Blitter DIVIDE5;
    public static final Blitter MULTIPLY10;
    public static final Blitter DIVIDE10;

    static {
        MULTIPLY2 = Blitter.texture(TEXTURE, 64, 64).src(32, 0, 16, 16);
        DIVIDE2 = Blitter.texture(TEXTURE, 64, 64).src(48, 0, 16, 16);
        MULTIPLY5 = Blitter.texture(TEXTURE, 64, 64).src(0, 0, 16, 16);
        DIVIDE5 = Blitter.texture(TEXTURE, 64, 64).src(16, 0, 16, 16);
        MULTIPLY10 = Blitter.texture(TEXTURE, 64, 64).src(0, 16, 16, 16);
        DIVIDE10 = Blitter.texture(TEXTURE, 64, 64).src(16, 16, 16, 16);
    }
}
