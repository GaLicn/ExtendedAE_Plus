package com.extendedae_plus.mixin.ae2.helpers.patternprovider;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 让 PatternProviderLogicHost 作为 IUpgradeableObject 的代理，菜单可从 host 获取升级槽。
 */
@Mixin(value = PatternProviderLogicHost.class, remap = false)
public interface PatternProviderLogicHostUpgradesMixin extends IUpgradeableObject {
    @Shadow PatternProviderLogic getLogic();

    @Override
    default IUpgradeInventory getUpgrades() {
        return ((IUpgradeableObject) this.getLogic()).getUpgrades();
    }
}
