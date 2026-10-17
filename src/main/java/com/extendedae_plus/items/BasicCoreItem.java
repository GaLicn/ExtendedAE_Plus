package com.extendedae_plus.items;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.ids.EAPComponents;
import com.extendedae_plus.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class BasicCoreItem extends Item {
    private static final int MAX_STAGE = 3;

    public BasicCoreItem(Properties props) {
        super(props.stacksTo(1));
    }

    // ==================== 工厂方法 ====================
    public static ItemStack of(CoreType type, int stage) {
        // 保留原工厂入口，能源和量子分支返回独立阶段物品。
        if (type == CoreType.ENERGY) {
            if (stage < 1 || stage > EnergyStorageCoreItem.STAGE_COUNT) {
                return new ItemStack(ModItems.BASIC_CORE.get());
            }
            if (ModItems.ENERGY_STORAGE_CORE == null) return ItemStack.EMPTY;
            return new ItemStack(switch (stage) {
                case 1 -> ModItems.ENERGY_STORAGE_CORE_1.get();
                case 2 -> ModItems.ENERGY_STORAGE_CORE_2.get();
                case 3 -> ModItems.ENERGY_STORAGE_CORE_3.get();
                default -> ModItems.ENERGY_STORAGE_CORE_4.get();
            });
        }
        if (type == CoreType.QUANTUM) {
            if (stage < 1 || stage > QuantumStorageCoreItem.STAGE_COUNT) {
                return new ItemStack(ModItems.BASIC_CORE.get());
            }
            if (ModItems.QUANTUM_STORAGE_CORE == null) return ItemStack.EMPTY;
            return new ItemStack(switch (stage) {
                case 1 -> ModItems.QUANTUM_STORAGE_CORE_1.get();
                case 2 -> ModItems.QUANTUM_STORAGE_CORE_2.get();
                case 3 -> ModItems.QUANTUM_STORAGE_CORE_3.get();
                default -> ModItems.QUANTUM_STORAGE_CORE_4.get();
            });
        }
        ItemStack stack = new ItemStack(ModItems.BASIC_CORE.get());
        if (type != null && stage >= 0 && stage <= MAX_STAGE) {
            stack.set(EAPComponents.CORE_TYPE.get(), type);
            stack.set(EAPComponents.CORE_STAGE.get(), stage);
            stack.set(DataComponents.RARITY, type.getRarity(stage));
        }
        return stack;
    }

    public static ItemStack storageStage(int stage) { return of(CoreType.STORAGE, stage); }
    public static ItemStack spatialStage(int stage) { return of(CoreType.SPATIAL, stage); }
    public static ItemStack energyStage(int stage)  { return of(CoreType.ENERGY, stage); }
    public static ItemStack quantumStage(int stage) { return of(CoreType.QUANTUM, stage); }

    // ==================== DataComponents 查询 ====================
    public static Optional<CoreType> getType(ItemStack stack) {
        if (stack.getItem() instanceof EnergyStorageCoreItem) return Optional.of(CoreType.ENERGY);
        if (stack.getItem() instanceof QuantumStorageCoreItem) return Optional.of(CoreType.QUANTUM);
        // 旧能源、量子组件只保留存档解码，不再启用旧阶段外观和进度条。
        return Optional.ofNullable(stack.get(EAPComponents.CORE_TYPE.get()))
                .filter(type -> type != CoreType.ENERGY && type != CoreType.QUANTUM);
    }

    public static int getStage(ItemStack stack) {
        if (stack.getItem() instanceof EnergyStorageCoreItem core) return core.getStage();
        if (stack.getItem() instanceof QuantumStorageCoreItem core) return core.getStage();
        Integer stage = stack.get(EAPComponents.CORE_STAGE.get());
        return stage != null ? Math.min(stage, MAX_STAGE) : 0;
    }

    public static boolean isTyped(ItemStack stack) { return getType(stack).isPresent(); }

    public static boolean isFinalStage(ItemStack stack) {
        if (stack.getItem() instanceof QuantumStorageCoreItem core) {
            return core.getStage() >= QuantumStorageCoreItem.STAGE_COUNT;
        }
        int maxStage = stack.getItem() instanceof EnergyStorageCoreItem ? EnergyStorageCoreItem.STAGE_COUNT : MAX_STAGE;
        return getStage(stack) >= maxStage;
    }

    // ==================== 耐久条 ====================
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return isTyped(stack);
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        if (!isTyped(stack)) return 0;
        return Math.round(13.0f * getStage(stack) / MAX_STAGE);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return getType(stack)
                .map(type -> type.getTextColor().getColor())
                .orElse(0xFFFFFF);
    }

    // ==================== Tooltip ====================
    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!isTyped(stack)) {
            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.untyped")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        getType(stack).ifPresent(type -> {
            String finalKey = "item." + ExtendedAEPlus.MODID + "." + type.key + "_core";
            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.evolving_to",
                            Component.translatable(finalKey).withStyle(type.getTextColor()))
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.empty());

            tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.progress")
                    .withStyle(ChatFormatting.YELLOW));

            int stage = getStage(stack);
            for (int i = 1; i <= 3; i++) {
                String key = "item." + ExtendedAEPlus.MODID + ".basic_core." + type.key + "." + i;
                ChatFormatting color = i <= stage ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
                String prefix = i <= stage ? "✔ " : "✘ ";
                tooltip.add(Component.literal(prefix).withStyle(color).append(Component.translatable(key)));
            }

            if (stage >= MAX_STAGE) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.ready_to_craft")
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }

    // ==================== 核心类型枚举 ====================
    public enum CoreType {
        STORAGE (1, "storage",        ChatFormatting.AQUA),
        SPATIAL (2, "spatial",        ChatFormatting.YELLOW),
        ENERGY  (3, "energy_storage", ChatFormatting.RED),
        QUANTUM (4, "quantum_storage",ChatFormatting.LIGHT_PURPLE);

        public final int id;
        public final String key;
        public final ChatFormatting textColor;

        CoreType(int id, String key, ChatFormatting textColor) {
            this.id = id;
            this.key = key;
            this.textColor = textColor;
        }

        public ChatFormatting getTextColor() { return textColor; }

        public Rarity getRarity(int stage) {
            return switch (stage) {
                case 1, 2 -> Rarity.UNCOMMON;
                case 3  -> Rarity.EPIC;
                default -> Rarity.COMMON;
            };
        }
    }
}
