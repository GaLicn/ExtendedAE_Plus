package com.extendedae_plus.items;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Optional;

public class BasicCoreItem extends Item {
    public BasicCoreItem(Properties props) {
        super(props.stacksTo(1));
    }

    // ==================== 工厂方法 ====================
    public static ItemStack of(CoreType type, int stage) {
        // 保留原工厂入口，已拆分的核心返回独立阶段物品。
        if (type == CoreType.STORAGE) {
            if (stage < 1 || stage > StorageCoreItem.STAGE_COUNT) {
                return new ItemStack(ModItems.BASIC_CORE.get());
            }
            return new ItemStack(switch (stage) {
                case 1 -> ModItems.STORAGE_CORE_1.get();
                case 2 -> ModItems.STORAGE_CORE_2.get();
                case 3 -> ModItems.STORAGE_CORE_3.get();
                default -> ModItems.STORAGE_CORE_4.get();
            });
        }
        if (type == CoreType.SPATIAL) {
            if (stage < 1 || stage > SpatialCoreItem.STAGE_COUNT) {
                return new ItemStack(ModItems.BASIC_CORE.get());
            }
            return new ItemStack(switch (stage) {
                case 1 -> ModItems.SPATIAL_CORE_1.get();
                case 2 -> ModItems.SPATIAL_CORE_2.get();
                case 3 -> ModItems.SPATIAL_CORE_3.get();
                default -> ModItems.SPATIAL_CORE_4.get();
            });
        }
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
        return new ItemStack(ModItems.BASIC_CORE.get());
    }

    public static ItemStack storageStage(int stage) { return of(CoreType.STORAGE, stage); }
    public static ItemStack spatialStage(int stage) { return of(CoreType.SPATIAL, stage); }
    public static ItemStack energyStage(int stage)  { return of(CoreType.ENERGY, stage); }
    public static ItemStack quantumStage(int stage) { return of(CoreType.QUANTUM, stage); }

    // ==================== 独立阶段查询 ====================
    public static Optional<CoreType> getType(ItemStack stack) {
        if (stack.getItem() instanceof StorageCoreItem) return Optional.of(CoreType.STORAGE);
        if (stack.getItem() instanceof SpatialCoreItem) return Optional.of(CoreType.SPATIAL);
        if (stack.getItem() instanceof EnergyStorageCoreItem) return Optional.of(CoreType.ENERGY);
        if (stack.getItem() instanceof QuantumStorageCoreItem) return Optional.of(CoreType.QUANTUM);
        // 旧组件只保留存档解码，不再启用旧阶段外观。
        return Optional.empty();
    }

    public static int getStage(ItemStack stack) {
        if (stack.getItem() instanceof StorageCoreItem core) return core.getStage();
        if (stack.getItem() instanceof SpatialCoreItem core) return core.getStage();
        if (stack.getItem() instanceof EnergyStorageCoreItem core) return core.getStage();
        if (stack.getItem() instanceof QuantumStorageCoreItem core) return core.getStage();
        return 0;
    }

    public static boolean isTyped(ItemStack stack) { return getType(stack).isPresent(); }

    public static boolean isFinalStage(ItemStack stack) {
        if (stack.getItem() instanceof StorageCoreItem core) {
            return core.getStage() >= StorageCoreItem.STAGE_COUNT;
        }
        if (stack.getItem() instanceof SpatialCoreItem core) {
            return core.getStage() >= SpatialCoreItem.STAGE_COUNT;
        }
        if (stack.getItem() instanceof EnergyStorageCoreItem core) {
            return core.getStage() >= EnergyStorageCoreItem.STAGE_COUNT;
        }
        if (stack.getItem() instanceof QuantumStorageCoreItem core) {
            return core.getStage() >= QuantumStorageCoreItem.STAGE_COUNT;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + ExtendedAEPlus.MODID + ".basic_core.untyped")
                .withStyle(ChatFormatting.GRAY));
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
