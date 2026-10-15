package com.extendedae_plus.mixin.ae2.accessor;

import appeng.helpers.patternprovider.PatternContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.menu.implementations.PatternAccessTermMenu$ContainerTracker", remap = false)
public interface PatternAccessTermContainerTrackerAccessor {
    @Accessor("container")
    PatternContainer eap$getContainer();
}