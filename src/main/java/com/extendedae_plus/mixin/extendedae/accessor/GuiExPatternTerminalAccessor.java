package com.extendedae_plus.mixin.extendedae.accessor;

import appeng.client.gui.widgets.AETextField;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiExPatternTerminal.class, remap = false)
public interface GuiExPatternTerminalAccessor {
    @Accessor("searchOutField")
    AETextField getSearchOutField();
} 