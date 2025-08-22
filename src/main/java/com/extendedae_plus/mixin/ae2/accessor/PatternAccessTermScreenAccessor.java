package com.extendedae_plus.mixin.ae2.accessor;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.widgets.Scrollbar;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;

@OnlyIn(Dist.CLIENT)
@Mixin(value = PatternAccessTermScreen.class, remap = false)
public interface PatternAccessTermScreenAccessor {
	@Accessor("scrollbar")
	Scrollbar getScrollbar();

	@Accessor("visibleRows")
	int getVisibleRows();

	@Accessor("rows")
	ArrayList<?> getRows();
} 