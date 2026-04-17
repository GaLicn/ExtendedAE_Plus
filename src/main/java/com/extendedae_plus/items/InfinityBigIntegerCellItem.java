package com.extendedae_plus.items;

import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory;
import com.extendedae_plus.util.storage.InfinityConstants;
import com.google.common.base.Preconditions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.math.BigInteger;
import java.util.List;

public class InfinityBigIntegerCellItem extends Item implements ICellWorkbenchItem {

    public InfinityBigIntegerCellItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("tooltip.extendedae_plus.infinity_biginteger_cell"));

        Preconditions.checkArgument(stack.getItem() == this);
        // 仅在 ItemStack 自身存在 UUID 时显示 UUID，避免触发持久化或加载逻辑
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);

        if (!customData.isEmpty()) {
            CompoundTag tag = customData.copyTag();

            if (tag.contains(InfinityConstants.INFINITY_CELL_UUID)) {
                String uuidStr = tag.getUUID(InfinityConstants.INFINITY_CELL_UUID).toString();
                tooltip.add(
                        Component.literal("UUID: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(uuidStr).withStyle(ChatFormatting.YELLOW))
                );
            }

            if (tag.contains(InfinityConstants.INFINITY_ITEM_TYPES)) {
                try {
                    int types = tag.getInt(InfinityConstants.INFINITY_ITEM_TYPES);
                    tooltip.add(
                            Component.literal("Types: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(String.valueOf(types)).withStyle(ChatFormatting.GREEN))
                    );
                } catch (Exception ignored) {}
            }

            if (tag.contains(InfinityConstants.INFINITY_ITEM_TOTAL)) {
                try {
                    byte[] bytes = tag.getByteArray(InfinityConstants.INFINITY_ITEM_TOTAL);
                    BigInteger total = new BigInteger(bytes);
                    String formatted = InfinityBigIntegerCellInventory.formatBigInteger(total);
                    tooltip.add(
                            Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(formatted).withStyle(ChatFormatting.AQUA))
                    );
                } catch (Exception ignored) {}
            } else if (tag.contains(InfinityConstants.INFINITY_CELL_ITEM_COUNT)) {
                try {
                    byte[] bytes = tag.getByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
                    BigInteger total = new BigInteger(bytes);
                    String formatted = InfinityBigIntegerCellInventory.formatBigInteger(total);
                    tooltip.add(
                            Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(formatted).withStyle(ChatFormatting.AQUA))
                    );
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack itemStack) {
        return UpgradeInventories.forItem(itemStack, 4);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack itemStack) {
        return CellConfig.create(itemStack);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) {
        return itemStack.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) {
        itemStack.set(AEComponents.STORAGE_CELL_FUZZY_MODE, fuzzyMode);
    }
}
