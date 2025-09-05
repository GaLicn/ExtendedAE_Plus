package com.extendedae_plus.mixin.extendedae.accessor;

import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal$SlotsRow", remap = false)
public interface GuiExPatternTerminalSlotsRowAccessor {
    @Accessor("container")
    PatternContainerRecord getContainer();

    @Accessor("offset")
    int getOffset();

    @Accessor("slots")
    int getSlots();
}