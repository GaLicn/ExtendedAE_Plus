package com.extendedae_plus.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor<T extends AbstractContainerMenu> {
    @Accessor("leftPos") int eap$getLeftPos();
    @Accessor("topPos") int eap$getTopPos();
    @Accessor("imageWidth") int eap$getImageWidth();
    @Accessor("imageHeight") int eap$getImageHeight();
    @Accessor("hoveredSlot") Slot eap$getHoveredSlot();
}
