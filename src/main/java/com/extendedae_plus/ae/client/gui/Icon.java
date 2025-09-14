package com.extendedae_plus.ae.client.gui;

import appeng.client.gui.style.Blitter;
import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.resources.ResourceLocation;

public enum Icon {
    REDSTONE_LOW(0, 0),
    REDSTONE_HIGH(16, 0);

    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public static final ResourceLocation TEXTURE = new ResourceLocation(ExtendedAEPlus.MODID, "textures/guis/states.png");
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 256;

    private Icon(int x, int y) {
        this(x, y, 16, 16);
    }

    private Icon(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Blitter getBlitter() {
        return Blitter.texture(TEXTURE, TEXTURE_WIDTH, TEXTURE_HEIGHT).src(this.x, this.y, this.width, this.height);
    }
}
