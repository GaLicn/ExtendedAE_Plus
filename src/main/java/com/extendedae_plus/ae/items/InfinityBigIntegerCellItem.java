package com.extendedae_plus.ae.items;

import com.extendedae_plus.ae.api.storage.InfinityBigIntegerCellInventory;
import com.google.common.base.Preconditions;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;

public class InfinityBigIntegerCellItem extends Item {

    public InfinityBigIntegerCellItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    /**
     * 在物品悬停提示中展示额外信息。
     * 功能：
     * - 若 ItemStack 的 NBT 含有 UUID，则显示该 UUID（不会触发服务器加载或持久化行为）
     * - 若 NBT 同步了 total 字段，则读取并格式化显示总存储量（使用 Inventory 的 formatBigInteger）
     *
     * 设计说明：客户端 tooltip 不主动访问服务端 SavedData，以避免不必要的 I/O 与状态变更。
     */
    @Override
    public void appendHoverText(ItemStack stack,
                                @Nullable Level world,
                                @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag context) {
        tooltip.add(Component.translatable("tooltip.extendedae_plus.infinity_biginteger_cell.summon1"));
        tooltip.add(Component.translatable("tooltip.extendedae_plus.infinity_biginteger_cell.summon2"));

        Preconditions.checkArgument(stack.getItem() == this);
        // 仅在 ItemStack 自身存在 UUID 时显示 UUID，避免触发持久化或加载逻辑
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("uuid")) {
            String uuidStr = tag.getUUID("uuid").toString();
            tooltip.add(
                    Component.literal("UUID: ").withStyle(ChatFormatting.GRAY).append(Component.literal(uuidStr).withStyle(ChatFormatting.YELLOW))
            );
            // 读取并显示已缓存的种类数量（types），表示当前存储了多少种不同的 AEKey
            if (tag.contains("types")) {
                try {
                    int types = tag.getInt("types");
                    tooltip.add(
                            Component.literal("Types: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(types)).withStyle(ChatFormatting.GREEN))
                    );
                } catch (Exception ignored) {
                    // ignore malformed value
                }
            }
            // 读取并显示已缓存的 total（支持 long 或 string），使用格式化函数展示友好单位
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
                    // 解析失败保持为 0
                }
                String formatted = InfinityBigIntegerCellInventory.formatBigInteger(total);
                tooltip.add(
                        Component.literal("Byte: ").withStyle(ChatFormatting.GRAY).append(Component.literal(formatted).withStyle(ChatFormatting.AQUA))
                );
            }
        }
    }
}