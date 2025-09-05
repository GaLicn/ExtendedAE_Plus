package com.extendedae_plus.mixin.extendedae.accessor;

import appeng.client.gui.widgets.Scrollbar;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiExPatternTerminal.class, remap = false)
public interface GuiExPatternTerminalAccessor {
    @Accessor("scrollbar")
    Scrollbar getScrollbar();

    @Accessor("visibleRows")
    int getVisibleRows();

    @Accessor("rows")
    ArrayList<?> getRows();
}