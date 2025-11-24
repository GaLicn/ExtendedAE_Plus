package com.extendedae_plus.items.materials;

import appeng.items.materials.UpgradeCardItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 虚拟合成卡：安装在样板供应器中以触发自动完成逻辑。
 */
public class VirtualCraftingCardItem extends UpgradeCardItem {

    public VirtualCraftingCardItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                 @Nullable Level level,
                                 List<Component> lines,
                                 TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("item.extendedae_plus.virtual_crafting_card.tooltip_main"));
        lines.add(Component.translatable("item.extendedae_plus.virtual_crafting_card.tooltip_detail"));
    }
}
