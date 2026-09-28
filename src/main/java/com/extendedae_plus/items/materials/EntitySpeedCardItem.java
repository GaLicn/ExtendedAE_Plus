package com.extendedae_plus.items.materials;

import appeng.items.materials.UpgradeCardItem;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * 单一的实体加速卡 Item，通过 ItemStack 的 NBT 存储 exponent（0/1/2/3）来区分等级
 */
public class EntitySpeedCardItem extends UpgradeCardItem {
    private static final String NBT_MULTIPLIER = "EAS:mult";

    public EntitySpeedCardItem(Properties props) {
        super(props);
    }

    public static ItemStack withMultiplier(byte multiplier) {
        ItemStack stack = new ItemStack(ModItems.ENTITY_SPEED_CARD.get());
        CompoundTag tag = new CompoundTag();
        tag.putByte(NBT_MULTIPLIER, multiplier);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static byte readMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return (byte) 1;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return (byte) 1;
        return customData.copyTag().getByteOr(NBT_MULTIPLIER, (byte) 1);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        byte mult = readMultiplier(stack);
        String key;
        switch (mult) {
            case (byte)2 -> key = "item." + ExtendedAEPlus.MODID + ".entity_speed_card.x2";
            case (byte)4 -> key = "item." + ExtendedAEPlus.MODID + ".entity_speed_card.x4";
            case (byte)8 -> key = "item." + ExtendedAEPlus.MODID + ".entity_speed_card.x8";
            case (byte)16 -> key = "item." + ExtendedAEPlus.MODID + ".entity_speed_card.x16";
            default -> key = "item." + ExtendedAEPlus.MODID + ".entity_speed_card.x1";
        }
        return Component.translatable(key);
    }


    private List<Component> getTooltipLines(ItemStack stack) {
        byte mult = readMultiplier(stack);
        int cap = 1;
        switch (mult) {
            case (byte)16 -> cap = 1024;
            case (byte)8 -> cap = 256;
            case (byte)4 -> cap = 64;
            case (byte)2 -> cap = 8;
        }
        MutableComponent line1 = Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".entity_speed_card.multiplier", "x" + mult);
        MutableComponent line2 = Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".entity_speed_card.max", cap);
        return List.of(line1, line2);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag advancedTooltips) {
        super.appendHoverText(stack, context, display, lines, advancedTooltips);
        this.getTooltipLines(stack).forEach(lines);
    }
}


