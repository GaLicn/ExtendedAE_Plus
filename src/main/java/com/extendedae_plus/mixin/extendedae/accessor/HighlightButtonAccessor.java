package com.extendedae_plus.mixin.extendedae.accessor;

import com.glodblock.github.extendedae.client.button.HighlightButton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@OnlyIn(Dist.CLIENT)
@Mixin(value = HighlightButton.class, remap = false)
public interface HighlightButtonAccessor {
    @Accessor("pos")
    BlockPos eap$getPos();

    @Accessor("face")
    Direction eap$getFace();
}
