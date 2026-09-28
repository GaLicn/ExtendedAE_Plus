package com.extendedae_plus.mixin.appflux.accessor;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = PatternProviderLogic.class, priority = 500, remap = false)
public interface PatternProviderLogicAppfluxAccessor {
    @Accessor(value = "af_upgrades", remap = false)
    IUpgradeInventory eap$getAppfluxUpgrades();

    @Accessor(value = "af_upgrades", remap = false)
    void eap$setAppfluxUpgrades(IUpgradeInventory upgrades);

    @Invoker(value = "af_onUpgradesChanged", remap = false)
    void eap$invokeAppfluxUpgradesChanged();
}
