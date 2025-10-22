package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.networking.crafting.ICraftingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders$CraftingProviderList")
public interface CraftingProviderListAccessor {
    @Accessor(value = "providers",remap = false)
    List<ICraftingProvider> getProviders();
}
