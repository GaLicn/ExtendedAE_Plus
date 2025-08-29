package com.extendedae_plus.mixin.autopattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders.CraftingProviderList")
public interface CraftingProviderListAccessor {
    @Accessor("providers")
    List<?> eap$getProviders();
}
