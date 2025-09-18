package com.extendedae_plus.ae.items;

import com.extendedae_plus.ae.api.storage.InfinityBigIntegerCellInventory;
import com.google.common.base.Preconditions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;

public class InfinityBigIntegerCellItem extends Item {

    public InfinityBigIntegerCellItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @NotNull TooltipContext context,
                                List<Component> tooltip,
                                @NotNull TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("tooltip.extendedae_plus.infinity_biginteger_cell.summon1"));
        tooltip.add(Component.translatable("tooltip.extendedae_plus.infinity_biginteger_cell.summon2"));

        Preconditions.checkArgument(stack.getItem() == this);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();

            if (tag != null && tag.contains("uuid")) {
                String uuidStr = tag.getUUID("uuid").toString();
                tooltip.add(
                        Component.literal("UUID: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(uuidStr).withStyle(ChatFormatting.YELLOW))
                );

                if (tag.contains("types")) {
                    try {
                        int types = tag.getInt("types");
                        tooltip.add(
                                Component.literal("Types: ")
                                        .withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(String.valueOf(types)).withStyle(ChatFormatting.GREEN))
                        );
                    } catch (Exception ignored) {
                    }
                }

                if (tag.contains("total")) {
                    BigInteger total = BigInteger.ZERO;
                    Tag t = tag.get("total");
                    try {
                        if (t instanceof LongTag) {
                            total = BigInteger.valueOf(tag.getLong("total"));
                        } else {
                            String s = tag.getString("total");
                            total = new BigInteger(s);
                        }
                    } catch (Exception ignored) {
                    }
                    String formatted = InfinityBigIntegerCellInventory.formatBigInteger(total);
                    tooltip.add(
                            Component.literal("Byte: ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(formatted).withStyle(ChatFormatting.AQUA))
                    );
                }
            }
        }
    }
}
