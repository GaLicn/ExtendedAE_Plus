package com.extendedae_plus.mixin.ae2.accessor;

import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternAccessTermScreen$SlotsRow", remap = false)
public interface PatternAccessTermScreenSlotsRowAccessor {
    @Accessor("container")
    PatternContainerRecord getContainer();

    @Accessor("offset")
    int getOffset();

    @Accessor("slots")
    int getSlots();
} 