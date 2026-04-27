package com.extendedae_plus.mixin.ae2.accessor;

import appeng.client.gui.WidgetContainer;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = WidgetContainer.class, remap = false)
public interface WidgetContainerAccessor {
    @Accessor("widgets")
    Map<String, AbstractWidget> eap$getWidgetsMap();
}
