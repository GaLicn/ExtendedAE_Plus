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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class BasicCoreItem extends Item {

    private static final String NBT_TYPE = "core_type";     // 0-3: 四大方向
    private static final String NBT_STAGE = "core_stage";   // 0-4: 当前阶段

    private static final int MAX_STAGE = 4;

    public enum CoreType {
        STORAGE(0, "storage_core", Rarity.UNCOMMON, ChatFormatting.AQUA),
        INFINITY(1, "infinity_core", Rarity.RARE, ChatFormatting.LIGHT_PURPLE),
        OBLIVION(2, "oblivion_singularity", Rarity.EPIC, ChatFormatting.DARK_PURPLE),
        SPATIAL(3, "spatial_core", Rarity.EPIC, ChatFormatting.YELLOW);

        public final int id;
        public final String resultItem;
        public final Rarity rarity;
        public final ChatFormatting color;

        CoreType(int id, String resultItem, Rarity rarity, ChatFormatting color) {
            this.id = id;
            this.resultItem = resultItem;
            this.rarity = rarity;
            this.color = color;
        }

        public static Optional<CoreType> byId(int id) {
            return switch (id) {
                case 0 -> Optional.of(STORAGE);
                case 1 -> Optional.of(INFINITY);
                case 2 -> Optional.of(OBLIVION);
                case 3 -> Optional.of(SPATIAL);
                default -> Optional.empty();
            };
        }
    }

    public BasicCoreItem(Properties props) {
        super(props.stacksTo(1).setNoRepair());
    }

    // ==================== 工厂方法 ====================
    public static ItemStack storage()  { return of(CoreType.STORAGE, 1); }
    public static ItemStack infinity() { return of(CoreType.INFINITY, 1); }
    public static ItemStack oblivion() { return of(CoreType.OBLIVION, 1); }
    public static ItemStack spatial()  { return of(CoreType.SPATIAL, 1); }

    /** 创建指定类型 + 阶段的核心 */
    public static ItemStack of(CoreType type, int stage) {
        ItemStack stack = new ItemStack(ModItems.BASIC_CORE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(NBT_TYPE, type.id);
        tag.putInt(NBT_STAGE, stage);
        return stack;
    }

    // ==================== NBT 读取 ====================
    public static Optional<CoreType> getType(ItemStack stack) {
        if (!stack.hasTag()) return Optional.empty();
        int id = stack.getTag().getInt(NBT_TYPE);
        return CoreType.byId(id);
    }

    public static int getStage(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return Math.min(stack.getTag().getInt(NBT_STAGE), MAX_STAGE);
    }

    public static boolean isFinalStage(ItemStack stack) {
        return getStage(stack) >= MAX_STAGE;
    }

    // ==================== 显示 ====================
    @Override
    public Component getName(ItemStack stack) {
        return getType(stack).<Component>map(type ->
                Component.translatable("item." + ExtendedAEPlus.MODID + ".basic_core." + type.name().toLowerCase())
                        .withStyle(type.color)
        ).orElseGet(() ->
                Component.translatable("item." + ExtendedAEPlus.MODID + ".basic_core")
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return getType(stack)
                .map(t -> t.rarity)
                .orElse(Rarity.COMMON);
    }

    // ==================== 耐久条 ====================
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * getStage(stack) / MAX_STAGE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int stage = getStage(stack);
        if (stage == 0) return 0xFF4444;        // 红色 - 未定型
        if (stage == 1) return 0x4488FF;        // 蓝色 - 已定型
        if (stage <= 3) return 0xFFFF44;        // 黄色 - 强化中
        return 0xFFAA00;                        // 金色 - 可合成最终核心
    }

    // ==================== Tooltip ====================
    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int stage = getStage(stack);

        if (stage == 0) {
            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.stage_0")
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.stage_0_hint")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        getType(stack).ifPresent(type -> {
            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.type",
                    Component.translatable("item." + ExtendedAEPlus.MODID + "." + type.resultItem)
                            .withStyle(type.color))
                    .withStyle(ChatFormatting.AQUA));

            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.progress")
                    .withStyle(ChatFormatting.YELLOW));

            String[] stages = {
                    "typed", "reinforced_1", "reinforced_2", "final_ready"
            };
            for (int i = 1; i <= stage; i++) {
                tooltip.add(Component.literal("  ✔ ").withStyle(ChatFormatting.GREEN)
                        .append(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.stage_" + i)));
            }
            for (int i = stage + 1; i <= MAX_STAGE; i++) {
                tooltip.add(Component.literal("  ✘ ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.stage_" + i)));
            }

            if (stage >= MAX_STAGE) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.can_craft")
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }
}