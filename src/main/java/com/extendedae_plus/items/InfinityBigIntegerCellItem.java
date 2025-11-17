package com.extendedae_plus.items;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.cells.ICellWorkbenchItem;
import com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory;
import com.extendedae_plus.util.storage.InfinityConstants;
import com.google.common.base.Preconditions;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class InfinityBigIntegerCellItem extends Item implements ICellWorkbenchItem {

    public InfinityBigIntegerCellItem(Properties props) {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @Nullable Level world,
                                @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag context) {
        tooltip.add(Component.translatable("tooltip.extendedae_plus.infinity_biginteger_cell"));

        Preconditions.checkArgument(stack.getItem() == this);
        // 仅在 ItemStack 自身存在 UUID 时显示 UUID，避免触发持久化或加载逻辑
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(InfinityConstants.INFINITY_CELL_UUID)) {
            String uuidStr = tag.getUUID(InfinityConstants.INFINITY_CELL_UUID).toString();
            tooltip.add(
                    Component.literal("UUID: ").withStyle(ChatFormatting.GRAY).append(Component.literal(uuidStr).withStyle(ChatFormatting.YELLOW))
            );

            // 显示已缓存的种类数量（types）——优先使用 ItemStack 缓存字段
            if (tag.contains(InfinityConstants.INFINITY_ITEM_TYPES)) {
                try {
                    int types = tag.getInt(InfinityConstants.INFINITY_ITEM_TYPES);
                    tooltip.add(
                            Component.literal("Types: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(types)).withStyle(ChatFormatting.GREEN))
                    );
                } catch (Exception ignored) {
                    // ignore malformed value
                }
            }

            // 显示物品总数（formatted）。优先使用缓存的 INFINITY_ITEM_TOTAL 字段（byte[]），否则回退为 legacy 字段或不显示
            if (tag.contains(InfinityConstants.INFINITY_ITEM_TOTAL)) {
                try {
                    byte[] bytes = tag.getByteArray(InfinityConstants.INFINITY_ITEM_TOTAL);
                    BigInteger total = new BigInteger(bytes);
                    String formatted = InfinityBigIntegerCellInventory.formatBigInteger(total);
                    tooltip.add(
                            Component.literal("Total: ").withStyle(ChatFormatting.GRAY).append(Component.literal(formatted).withStyle(ChatFormatting.AQUA))
                    );
                } catch (Exception ignored) {
                    // ignore malformed value
                }
            } else if (tag.contains(InfinityConstants.INFINITY_CELL_ITEM_COUNT)) {
                try {
                    byte[] bytes = tag.getByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
                    BigInteger total = new BigInteger(bytes);
                    String formatted = InfinityBigIntegerCellInventory.formatBigInteger(total);
                    tooltip.add(
                            Component.literal("Total: ").withStyle(ChatFormatting.GRAY).append(Component.literal(formatted).withStyle(ChatFormatting.AQUA))
                    );
                } catch (Exception ignored) {
                    // ignore malformed value
                }
            }
        }
    }

    /**
     * 创建一个带有指定 UUID 的 Infinity 磁盘 ItemStack
     */
    public static ItemStack withUUID(UUID uuid) {
        ItemStack stack = new ItemStack(Objects.requireNonNull(
                ForgeRegistries.ITEMS.getValue(new ResourceLocation("extendedae_plus", "infinity_biginteger_cell")
        )));
        stack.getOrCreateTag().putUUID(InfinityConstants.INFINITY_CELL_UUID, uuid);
        return stack;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) {
        return null;
    }

    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) {
    }
}