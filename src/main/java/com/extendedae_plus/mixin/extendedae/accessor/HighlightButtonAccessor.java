package com.extendedae_plus.mixin.extendedae.accessor;

import com.glodblock.github.extendedae.client.button.HighlightButton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HighlightButton.class)
public interface HighlightButtonAccessor {
    @Accessor("pos")
    BlockPos getPos();

    @Accessor("face")
    Direction getFace();
}