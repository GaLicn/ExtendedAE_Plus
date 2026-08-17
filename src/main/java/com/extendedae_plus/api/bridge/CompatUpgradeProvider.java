package com.extendedae_plus.api.bridge;

import appeng.api.upgrades.IUpgradeInventory;

/** 为可选模组的机器提供本模组自带升级槽。 */
public interface CompatUpgradeProvider {
    IUpgradeInventory eap$getCompatUpgrades();
}
