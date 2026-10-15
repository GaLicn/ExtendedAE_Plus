package com.extendedae_plus.util.smartDoubling;

import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.util.crafting.StrictMolecularAssemblerPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * 合成任务存档时保留样板包装（缩放倍率与超级矩阵 Strict 包装）。
 *
 * <p>AE2 与 AdvancedAE 的 ExecutingCraftingJob 只按样板定义写入任务，读档再用定义解码出裸样板：
 * 缩放样板丢失倍率，任务退化为「原样板 × 剩余批次」而永远凑不够数量，同一样板的多个倍率还会
 * 因定义相同互相覆盖；Strict 包装丢失后，AE2 会按裸样板的替换候选投料。
 */
public final class CraftingTaskPatternNbt {

    private static final String NBT_TASKS = "tasks";
    private static final String NBT_CRAFTING_PROGRESS = "#craftingProgress";
    private static final String NBT_MULTIPLIER = "extendedae_plus:multiplier";
    private static final String NBT_STRICT = "extendedae_plus:strict";

    private CraftingTaskPatternNbt() {}

    /**
     * 按原格式重写任务列表并附加包装信息；没有需要保留的包装时保持原样。
     */
    public static <T> void writeTasks(CompoundTag data, Map<IPatternDetails, T> tasks, ToLongFunction<T> progress) {
        if (tasks.keySet().stream().noneMatch(pattern -> getMultiplier(pattern) > 0 || isStrict(pattern))) {
            return;
        }
        var list = new ListTag();
        for (var entry : tasks.entrySet()) {
            var pattern = entry.getKey();
            var item = pattern.getDefinition().toTag();
            item.putLong(NBT_CRAFTING_PROGRESS, progress.applyAsLong(entry.getValue()));
            long multiplier = getMultiplier(pattern);
            if (multiplier > 0) {
                item.putLong(NBT_MULTIPLIER, multiplier);
            }
            if (isStrict(pattern)) {
                item.putBoolean(NBT_STRICT, true);
            }
            list.add(item);
        }
        data.put(NBT_TASKS, list);
    }

    /**
     * 按任务条目记录的包装信息重建读档解码出的裸样板。
     */
    public static @Nullable IPatternDetails restore(@Nullable IPatternDetails decoded, @Nullable CompoundTag item) {
        if (decoded == null || item == null) {
            return decoded;
        }
        var pattern = decoded;
        if (item.getBoolean(NBT_STRICT) && pattern instanceof IMolecularAssemblerSupportedPattern molecularPattern) {
            pattern = StrictMolecularAssemblerPattern.wrap(molecularPattern);
        }
        long multiplier = item.contains(NBT_MULTIPLIER, Tag.TAG_LONG) ? item.getLong(NBT_MULTIPLIER) : 0;
        if (multiplier > 0) {
            pattern = PatternScaler.createScaled(pattern, multiplier);
        }
        return pattern;
    }

    private static long getMultiplier(IPatternDetails pattern) {
        if (pattern instanceof ScaledProcessingPattern scaled) {
            return scaled.getMultiplier();
        }
        if (pattern instanceof ScaledMolecularAssemblerPattern scaled) {
            return scaled.getMultiplier();
        }
        return 0;
    }

    private static boolean isStrict(IPatternDetails pattern) {
        if (pattern instanceof ScaledMolecularAssemblerPattern scaled) {
            return scaled.getOriginal() instanceof StrictMolecularAssemblerPattern;
        }
        return pattern instanceof StrictMolecularAssemblerPattern;
    }
}
