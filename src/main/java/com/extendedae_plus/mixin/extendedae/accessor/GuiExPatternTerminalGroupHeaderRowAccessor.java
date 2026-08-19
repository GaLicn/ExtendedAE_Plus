package com.extendedae_plus.mixin.extendedae.accessor;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal$GroupHeaderRow", remap = false)
public interface GuiExPatternTerminalGroupHeaderRowAccessor {
    @Invoker("group")
    PatternContainerGroup eap$getGroup();
}
