package com.extendedae_plus.bridge;

import appeng.api.upgrades.IUpgradeInventory;

/**
 * 仅用于我方在未安装 appflux 时向逻辑类暴露自带升级槽，避免与 appflux 的 IUpgradeableObject 冲突。
 */
public interface CompatUpgradeProvider {
    IUpgradeInventory eap$getCompatUpgrades();
}
