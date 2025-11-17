package com.extendedae_plus.items.materials;

import appeng.items.materials.UpgradeCardItem;
import com.extendedae_plus.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 单一的实体加速卡 Item，通过 ItemStack 的 NBT 存储 exponent（0/1/2/3）来区分等级
 */
public class EntitySpeedCardItem extends UpgradeCardItem {
    public static final String NBT_MULTIPLIER = "EAS:mult";

    public EntitySpeedCardItem(Properties props) {
        super(props);
    }

    public static ItemStack withMultiplier(int multiplier) {
        ItemStack s = new ItemStack(ModItems.ENTITY_SPEED_CARD.get());
        CompoundTag t = s.getOrCreateTag();
        t.putInt(NBT_MULTIPLIER, multiplier);
        s.setTag(t);
        return s;
    }

    public static int readMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 2;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_MULTIPLIER)) {
            // 关键：检测到没有 NBT，直接强制写一个默认合法值
            CompoundTag newTag = stack.getOrCreateTag();
            newTag.putInt(NBT_MULTIPLIER, 2);
            return 2;
        }
        int v = tag.getInt(NBT_MULTIPLIER);
        // 合法性检查
        return switch (v) {
            case 2, 4, 8, 16 -> v;
            default -> 2; // 非法值一律纠正为 x2
        };
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        int mult = readMultiplier(stack);
        String key;
        switch (mult) {
            case 2 -> key = "item." + com.extendedae_plus.ExtendedAEPlus.MODID + ".entity_speed_card.x2";
            case 4 -> key = "item." + com.extendedae_plus.ExtendedAEPlus.MODID + ".entity_speed_card.x4";
            case 8 -> key = "item." + com.extendedae_plus.ExtendedAEPlus.MODID + ".entity_speed_card.x8";
            case 16 -> key = "item." + com.extendedae_plus.ExtendedAEPlus.MODID + ".entity_speed_card.x16";
            default -> key = "item." + com.extendedae_plus.ExtendedAEPlus.MODID + ".entity_speed_card.x1";
        }
        return Component.translatable(key);
    }

    private List<Component> getTooltipLines(ItemStack stack) {
        int mult = readMultiplier(stack);
        long cap = 1L;
        switch (mult) {
            case 16 -> cap = 1024L;
            case 8 -> cap = 256L;
            case 4 -> cap = 64L;
            case 2 -> cap = 8L;
        }
        MutableComponent line1 = Component.translatable("tooltip." + com.extendedae_plus.ExtendedAEPlus.MODID + ".entity_speed_card.multiplier", "x" + mult);
        MutableComponent line2 = Component.translatable("tooltip." + com.extendedae_plus.ExtendedAEPlus.MODID + ".entity_speed_card.max", cap);
        return List.of(line1, line2);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        // add our custom tooltip lines (multiplier and max)
        lines.addAll(this.getTooltipLines(stack));
    }
}