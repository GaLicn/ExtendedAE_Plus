package com.extendedae_plus.mixin.ae2.accessor;

import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternAccessTermScreen$SlotsRow", remap = false)
public interface PatternAccessTermScreenSlotsRowAccessor {
    @Accessor("container")
    PatternContainerRecord eap$getContainer();

    @Accessor("offset")
    int eap$getOffset();

    @Accessor("slots")
    int eap$getSlots();
}