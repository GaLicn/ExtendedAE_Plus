package com.extendedae_plus.item;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class BasicCoreItem extends Item {
    private static final String NBT_TYPE = "core_type";   // 0=存储, 1=空间, 2=能源, 3=量子
    private static final String NBT_STAGE = "core_stage"; // 0=未定型, 1~4=四个阶段
    private static final int MAX_STAGE = 4;

    public BasicCoreItem(Properties props) {
        super(props.stacksTo(1).setNoRepair());
    }

    /**
     * 创建指定类型和阶段的核心（用于配方输出）
     *
     * @param type  核心类型
     * @param stage 阶段（1-4），0=未定型
     */
    public static ItemStack of(CoreType type, int stage) {
        ItemStack stack = new ItemStack(ModItems.BASIC_CORE.get());
        if (type != null && stage >= 0 && stage <= MAX_STAGE) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt(NBT_TYPE, type.id);
            tag.putInt(NBT_STAGE, stage);
        }
        return stack;
    }

    // ==================== 工厂方法：支持 4 条线路 + 4 个阶段 ====================
    public static ItemStack storageStage(int stage) {return of(CoreType.STORAGE, stage);}
    public static ItemStack spatialStage(int stage) {return of(CoreType.SPATIAL, stage);}
    public static ItemStack energyStage(int stage) {return of(CoreType.ENERGY, stage);}
    public static ItemStack quantumStage(int stage) {return of(CoreType.QUANTUM, stage);}

    // ==================== NBT 查询 ====================
    public static Optional<CoreType> getType(ItemStack stack) {
        if (!stack.hasTag()) return Optional.empty();
        return CoreType.byId(stack.getTag().getInt(NBT_TYPE));
    }

    public static int getStage(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return Math.min(stack.getTag().getInt(NBT_STAGE), MAX_STAGE);
    }

    public static boolean isTyped(ItemStack stack) {return getStage(stack) > 0;}

    public static boolean isFinalStage(ItemStack stack) {return getStage(stack) >= MAX_STAGE;}

    // ==================== 耐久条 ====================
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getStage(stack) > 0;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        int stage = getStage(stack);
        return stage == 0 ? 0 : Math.round(13.0f * stage / MAX_STAGE);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        int stage = getStage(stack);
        return getType(stack)
                .map(type -> type.getTextColor().getColor())
                .orElse(0xFFFFFF);
    }

    // ==================== Tooltip ====================
    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int stage = getStage(stack);

        if (stage == 0) {
            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.untyped")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        getType(stack).ifPresent(type -> {
            // 显示目标终极核心
            String finalKey = "item." + ExtendedAEPlus.MODID + "." + type.key + "_core";
            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.evolving_to",
                            Component.translatable(finalKey).withStyle(type.getTextColor()))
                    .withStyle(ChatFormatting.AQUA));

            tooltip.add(Component.empty());

            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.progress")
                    .withStyle(ChatFormatting.YELLOW));

            for (int i = 1; i <= 4; i++) {
                String key = "item." + ExtendedAEPlus.MODID + ".basic_core." + type.key + "." + (i - 1);
                ChatFormatting color = i <= stage ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
                String prefix = i <= stage ? "✔ " : "✘ ";
                tooltip.add(Component.literal(prefix).withStyle(color)
                        .append(Component.translatable(key)));
            }

            if (stage >= MAX_STAGE) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.ready_to_craft")
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }

    // ==================== 显示名称 ====================
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        int stage = getStage(stack);
        if (stage == 0) {
            return Component.translatable("item." + ExtendedAEPlus.MODID + ".basic_core");
        }

        return getType(stack).<Component>map(type -> {
            String key = "item." + ExtendedAEPlus.MODID + ".basic_core." + type.key + "." + (stage - 1);
            return Component.translatable(key).withStyle(type.getTextColor());
        }).orElseGet(() -> Component.translatable("item." + ExtendedAEPlus.MODID + ".basic_core"));
    }

    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        int stage = getStage(stack);
        return getType(stack).map(t -> t.getRarity(stage)).orElse(Rarity.COMMON);
    }

    public enum CoreType {
        STORAGE(0, "storage", ChatFormatting.AQUA),     // 存储：青色
        SPATIAL(1, "spatial", ChatFormatting.YELLOW),     // 空间：金色
        ENERGY(2, "energy_storage", ChatFormatting.RED),       // 能源：红色
        QUANTUM(3, "quantum_storage", ChatFormatting.LIGHT_PURPLE); // 量子：亮紫

        public final int id;
        public final String key;
        public final ChatFormatting textColor;  // 用于 Tooltip 和名称

        CoreType(int id, String key, ChatFormatting textColor) {
            this.id = id;
            this.key = key;
            this.textColor = textColor;
        }

        public static Optional<CoreType> byId(int id) {
            return switch (id) {
                case 0 -> Optional.of(STORAGE);
                case 1 -> Optional.of(SPATIAL);
                case 2 -> Optional.of(ENERGY);
                case 3 -> Optional.of(QUANTUM);
                default -> Optional.empty();
            };
        }

        // 统一返回同一个颜色（文本）
        public ChatFormatting getTextColor() {
            return textColor;
        }

        public Rarity getRarity(int stage) {
            return stage == 0 ? Rarity.COMMON :
                    stage <= 2 ? Rarity.UNCOMMON :
                            stage == 3 ? Rarity.RARE : Rarity.EPIC;
        }
    }
}