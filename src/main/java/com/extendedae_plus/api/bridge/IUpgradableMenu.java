package com.extendedae_plus.api.bridge;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.menu.ToolboxMenu;

public interface IUpgradableMenu {
    ToolboxMenu getToolbox();
    IUpgradeInventory getUpgrades();
    default boolean hasUpgrade(net.minecraft.world.level.ItemLike upgradeCard) {
        return getUpgrades().isInstalled(upgradeCard);
    }
}
