package com.extendedae_plus.items.materials;

import appeng.items.materials.UpgradeCardItem;

/**
 * 虚拟合成卡：用于自动完成样板供应器的合成任务。
 * 逻辑由 PatternProviderLogic 的 mixin 处理，此处仅作为升级卡物品本体。
 */
public class VirtualCraftingCardItem extends UpgradeCardItem {
    public VirtualCraftingCardItem(Properties properties) {
        super(properties);
    }
}
