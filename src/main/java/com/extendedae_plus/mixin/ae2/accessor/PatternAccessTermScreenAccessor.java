package com.extendedae_plus.mixin.ae2.accessor;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.widgets.Scrollbar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;

@OnlyIn(Dist.CLIENT)
@Mixin(value = PatternAccessTermScreen.class, remap = false)
public interface PatternAccessTermScreenAccessor {
	@Accessor("scrollbar")
	Scrollbar eap$getScrollbar();

	@Accessor("visibleRows")
	int eap$getVisibleRows();

	@Accessor("rows")
	ArrayList<?> eap$getRows();
} 