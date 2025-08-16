package com.extendedae_plus.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor<T extends AbstractContainerMenu> {
    @Accessor("leftPos") int extendedae_plus$getLeftPos();
    @Accessor("topPos") int extendedae_plus$getTopPos();
    @Accessor("imageWidth") int extendedae_plus$getImageWidth();
    @Accessor("imageHeight") int extendedae_plus$getImageHeight();
}
