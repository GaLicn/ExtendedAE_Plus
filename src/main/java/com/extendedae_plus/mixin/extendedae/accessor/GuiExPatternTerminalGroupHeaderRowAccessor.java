package com.extendedae_plus.mixin.extendedae.accessor;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal$GroupHeaderRow", remap = false)
public interface GuiExPatternTerminalGroupHeaderRowAccessor {
    @Invoker("group")
    PatternContainerGroup eap$getGroup();
}
