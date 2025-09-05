package com.extendedae_plus;

import appeng.client.gui.style.Blitter;
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

    static {
        MULTIPLY2 = Blitter.texture(TEXTURE, 64, 64).src(32, 0, 16, 16);
        DIVIDE2 = Blitter.texture(TEXTURE, 64, 64).src(48, 0, 16, 16);
        MULTIPLY5 = Blitter.texture(TEXTURE, 64, 64).src(0, 0, 16, 16);
        DIVIDE5 = Blitter.texture(TEXTURE, 64, 64).src(16, 0, 16, 16);
        MULTIPLY10 = Blitter.texture(TEXTURE, 64, 64).src(0, 16, 16, 16);
        DIVIDE10 = Blitter.texture(TEXTURE, 64, 64).src(16, 16, 16, 16);

    }
}
