package com.extendedae_plus.items;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class StorageCoreItem extends Item {
    public static final int STAGE_COUNT = 4;

    // 阶段由注册的物品决定，不再写入物品堆组件。
    private final int stage;

    public StorageCoreItem(Properties properties, int stage) {
        super(properties.stacksTo(1).fireResistant());
        if (stage < 1 || stage > STAGE_COUNT) {
            throw new IllegalArgumentException("Invalid storage core stage: " + stage);
        }
        this.stage = stage;
    }

    public int getStage() {
        return stage;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + ExtendedAEPlus.MODID + ".basic_core.";
        tooltip.add(Component.translatable(prefix + "evolving_to",
                        Component.translatable("item." + ExtendedAEPlus.MODID + ".storage_core")
                                .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(prefix + "progress").withStyle(ChatFormatting.YELLOW));
        for (int i = 1; i <= STAGE_COUNT; i++) {
            boolean completed = i <= stage;
            tooltip.add(Component.literal(completed ? "✔ " : "✘ ")
                    .withStyle(completed ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("item." + ExtendedAEPlus.MODID + ".storage_core_" + i)));
        }
        if (stage == STAGE_COUNT) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(prefix + "ready_to_craft").withStyle(ChatFormatting.GOLD));
        }
    }
}
