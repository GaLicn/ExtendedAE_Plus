package com.extendedae_plus.api.bridge;

import appeng.api.upgrades.IUpgradeInventory;

/** 为可选模组的机器提供本模组自带升级槽。 */
public interface CompatUpgradeProvider {
    IUpgradeInventory eap$getCompatUpgrades();

    /**
     * 实际生效的升级槽。
     *
     * <p>未安装 appflux 时与 {@link #eap$getCompatUpgrades()} 相同；安装 appflux 时返回其注入的槽位，
     * 否则往自建槽里装卡对机器不生效。</p>
     */
    default IUpgradeInventory eap$getEffectiveUpgrades() {
        return this.eap$getCompatUpgrades();
    }
}
