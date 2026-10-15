package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 样板供应器数据工具类
 * 用于获取样板供应器中的所有样板数据，包括输入输出物品的数量信息
 */
public class PatternProviderDataUtil {

    /**
     * 判断 provider 是否可用并属于指定网格（在线且处于活跃状态）。
     */
    public static boolean isProviderAvailable(PatternProviderLogic provider, IGrid expectedGrid) {
        if (provider == null || expectedGrid == null) return false;
        var grid = provider.getGrid();
        if (grid == null || !grid.equals(expectedGrid)) return false;

        // mainNode 为 AE2 公开 API（IManagedGridNode），其 isActive() 可直接调用，无需反射。
        if (!(provider instanceof PatternProviderLogicAccessor accessor)) return false;
        var mainNode = accessor.eap$mainNode();
        return mainNode != null && mainNode.isActive();
    }

    /**
     * 样板缩放操作结果
     */
    public static class PatternScalingResult {
        private final int totalPatterns;
        private final int scaledPatterns;
        private final int failedPatterns;

        public PatternScalingResult(int totalPatterns, int scaledPatterns, int failedPatterns) {
            this.totalPatterns = totalPatterns;
            this.scaledPatterns = scaledPatterns;
            this.failedPatterns = failedPatterns;
        }

        public int getTotalPatterns() { return totalPatterns; }
        public int getScaledPatterns() { return scaledPatterns; }
        public int getFailedPatterns() { return failedPatterns; }
    }

    /**
     * 对样板供应器中的所有样板进行输入输出数量倍增
     * ExtendedAE风格的样板倍增实现
     *
     * @param patternProvider 样板供应器逻辑
     * @param multiplier 倍数（必须大于0）
     * @return 缩放操作结果
     */
    public static PatternScalingResult multiplyPatternAmounts(PatternProviderLogic patternProvider, double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("倍数必须大于0");
        }
        return scalePatternAmountsExtendedAEStyle(patternProvider, multiplier);
    }

    /**
     * 查找 provider 中匹配给定定义的样板槽位（轻量、按需解码并早退出）
     * @param patternProvider 要搜索的 provider
     * @param targetDefinition pattern.getDefinition() 返回的对象（用于 equals 比较）
     * @return 找到的槽位索引，未找到返回 -1
     */
    public static int findSlotForPattern(PatternProviderLogic patternProvider, Object targetDefinition) {
        if (patternProvider == null || targetDefinition == null) return -1;
        InternalInventory inv = patternProvider.getPatternInv();
        if (inv == null) return -1;
        Level level = getPatternProviderLevel(patternProvider);
        if (level == null) return -1;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            try {
                IPatternDetails d = PatternDetailsHelper.decodePattern(s, level);
                if (d != null && d.getDefinition().equals(targetDefinition)) {
                    return i;
                }
            } catch (Exception e) {
            }
        }
        return -1;
    }

    /**
     * 对样板供应器中的所有样板进行输入输出数量倍除
     * ExtendedAE风格的样板除法实现
     *
     * @param patternProvider 样板供应器逻辑
     * @param divisor 除数（必须大于0）
     * @return 缩放操作结果
     */
    public static PatternScalingResult dividePatternAmounts(PatternProviderLogic patternProvider, double divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("除数必须大于0");
        }
        return scalePatternAmountsExtendedAEStyle(patternProvider, 1.0 / divisor);
    }

    /**
     * ExtendedAE风格的样板数量缩放实现
     * 提供更好的错误处理和样板兼容性
     */
    private static PatternScalingResult scalePatternAmountsExtendedAEStyle(PatternProviderLogic patternProvider, double scaleFactor) {
        int totalPatterns = 0;
        int scaledPatterns = 0;
        int failedPatterns = 0;

        if (patternProvider == null) {
            return new PatternScalingResult(0, 0, 0);
        }

        InternalInventory patternInventory = patternProvider.getPatternInv();
        if (patternInventory == null) {
            return new PatternScalingResult(0, 0, 0);
        }

        // 获取Level对象 - ExtendedAE风格的安全获取
        Level level = getPatternProviderLevel(patternProvider);
        if (level == null) {
            return new PatternScalingResult(0, 0, 0);
        }

        // 备份原始样板，以便在出错时恢复
        Map<Integer, ItemStack> originalPatterns = new HashMap<>();

        // 第一阶段：验证所有样板并创建备份
        for (int i = 0; i < patternInventory.size(); i++) {
            ItemStack patternStack = patternInventory.getStackInSlot(i);
            if (patternStack.isEmpty()) {
                continue;
            }

            totalPatterns++;
            originalPatterns.put(i, patternStack.copy());

            try {
                // 验证样板是否可以被解码
                IPatternDetails originalPattern = PatternDetailsHelper.decodePattern(patternStack, level);
                if (originalPattern == null) {
                    failedPatterns++;
                    continue;
                }

                // ExtendedAE风格：检查样板类型兼容性
                if (!isPatternScalable(originalPattern)) {
                    // 合成样板跳过，不计入失败数
                    continue;
                }

                // 如果是除法操作，预先验证可行性
                if (scaleFactor < 1.0) {
                    if (!canScalePatternExtendedAEStyle(originalPattern, scaleFactor)) {
                        failedPatterns++;
                        continue;
                    }
                }

            } catch (Exception ignored) {
                failedPatterns++;
            }
        }

        // 第二阶段：执行实际的样板缩放
        for (int i = 0; i < patternInventory.size(); i++) {
            ItemStack patternStack = patternInventory.getStackInSlot(i);
            if (patternStack.isEmpty() || !originalPatterns.containsKey(i)) {
                continue;
            }

            try {
                // 解码原始样板
                IPatternDetails originalPattern = PatternDetailsHelper.decodePattern(patternStack, level);
                if (originalPattern == null || !isPatternScalable(originalPattern)) {
                    continue;
                }

                // ExtendedAE风格的样板缩放
                ItemStack scaledPatternStack = scalePatternExtendedAEStyle(originalPattern, scaleFactor);
                if (scaledPatternStack == null || scaledPatternStack.isEmpty()) {
                    failedPatterns++;
                    continue;
                }

                // 应用缩放后的样板 - 使用正确的方法确保数据持久化
                setPatternWithPersistence(patternInventory, i, scaledPatternStack, patternProvider);
                scaledPatterns++;

            } catch (Exception ignored) {
                failedPatterns++;

                // ExtendedAE风格：出错时恢复原始样板
                try {
                    setPatternWithPersistence(patternInventory, i, originalPatterns.get(i), patternProvider);
                } catch (Exception ignoredRestore) {
                }
            }
        }

        // 触发样板更新
        try {
            patternProvider.updatePatterns();
        } catch (Exception ignored) {
        }

        return new PatternScalingResult(totalPatterns, scaledPatterns, failedPatterns);
    }

    /**
     * 正确设置样板并确保数据持久化
     * 解决样板修改后关闭UI就恢复的问题
     */
    private static void setPatternWithPersistence(InternalInventory patternInventory, int slot, ItemStack newPattern, PatternProviderLogic patternProvider) {
        try {
            // 1. 设置物品到库存
            patternInventory.setItemDirect(slot, newPattern);

            // 2. 标记数据为脏数据，确保保存到磁盘（尝试使用 mixin accessor 替代反射）
            try {
                if (patternProvider instanceof PatternProviderLogicAccessor accessor) {
                    var host = accessor.eap$host();
                    if (host != null) {
                        BlockEntity be = host.getBlockEntity();
                        if (be != null) {
                            try {
                                be.setChanged();
                            } catch (Exception ignored) {
                            }
                            try {
                                Level level = be.getLevel();
                                if (level != null && !level.isClientSide()) {
                                    var pos = be.getBlockPos();
                                    var state = be.getBlockState();
                                    level.sendBlockUpdated(pos, state, state, 3);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            // 3. 强制更新样板缓存
            patternProvider.updatePatterns();

        } catch (Exception e) {
            throw new RuntimeException("设置样板时发生错误: " + e.getMessage(), e);
        }
    }

    /**
     * ExtendedAE风格：安全获取样板供应器的Level对象
     */
    private static Level getPatternProviderLevel(PatternProviderLogic patternProvider) {
        if (patternProvider == null) return null;
        try {
            if (patternProvider instanceof PatternProviderLogicAccessor accessor) {
                var host = accessor.eap$host();
                if (host != null) {
                    BlockEntity be = host.getBlockEntity();
                    if (be != null) {
                        return be.getLevel();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * ExtendedAE风格：检查样板是否可以缩放
     */
    private static boolean isPatternScalable(IPatternDetails pattern) {
        if (pattern == null) return false;
        String def = String.valueOf(pattern.getDefinition()).toLowerCase();
        boolean isCrafting = def.contains("crafting");
        // 非合成且有输出，认为可缩放（包含处理/石切/锻造等）
        return !isCrafting && !pattern.getOutputs().isEmpty();
    }

    /**
     * ExtendedAE风格：检查样板是否可以按指定比例缩放
     */
    private static boolean canScalePatternExtendedAEStyle(IPatternDetails pattern, double scaleFactor) {
        if (!isPatternScalable(pattern)) return false;

        try {
            // 对于倍增操作，总是允许
            if (scaleFactor >= 1.0) return true;

            // 对于除法操作，检查所有输入输出是否可以被整除
            double divisor = 1.0 / scaleFactor;

            // 检查输入
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                GenericStack[] possibleInputs = input.getPossibleInputs();
                long multiplier = input.getMultiplier();

                if (possibleInputs.length > 0 && possibleInputs[0] != null) {
                    long currentAmount = possibleInputs[0].amount() * multiplier;
                    if (currentAmount < divisor || (currentAmount % divisor != 0)) {
                        return false;
                    }
                }
            }

            // 检查输出
            for (GenericStack output : pattern.getOutputs()) {
                if (output != null && output.what() != null) {
                    long currentAmount = output.amount();
                    if (currentAmount < divisor || (currentAmount % divisor != 0)) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ExtendedAE风格：缩放样板
     */
    private static ItemStack scalePatternExtendedAEStyle(IPatternDetails originalPattern, double scaleFactor) {
        if (!isPatternScalable(originalPattern)) {
            return null;
        }

        try {
            // 获取原始输入输出
            IPatternDetails.IInput[] originalInputs = originalPattern.getInputs();
            var originalOutputs = originalPattern.getOutputs();

            // ExtendedAE风格：更精确的数量计算
            List<GenericStack> scaledInputs = new ArrayList<>();
            for (IPatternDetails.IInput input : originalInputs) {
                GenericStack[] possibleInputs = input.getPossibleInputs();
                long multiplier = input.getMultiplier();

                if (possibleInputs.length > 0 && possibleInputs[0] != null) {
                    GenericStack primaryInput = possibleInputs[0];
                    long originalAmount = primaryInput.amount() * multiplier;

                    // ExtendedAE风格：精确计算新数量
                    long newAmount;
                    if (scaleFactor >= 1.0) {
                        // 倍增：四舍五入，但至少为1
                        newAmount = Math.max(1, Math.round(originalAmount * scaleFactor));
                    } else {
                        // 除法：必须能整除
                        double divisor = 1.0 / scaleFactor;
                        if (originalAmount % divisor == 0) {
                            newAmount = Math.max(1, (long)(originalAmount / divisor));
                        } else {
                            // 不能整除，返回null表示失败
                            return null;
                        }
                    }

                    scaledInputs.add(new GenericStack(primaryInput.what(), newAmount));
                }
            }

            // ExtendedAE风格：缩放输出
            List<GenericStack> scaledOutputs = new ArrayList<>();
            for (GenericStack output : originalOutputs) {
                if (output != null && output.what() != null) {
                    long originalAmount = output.amount();

                    // ExtendedAE风格：精确计算新数量
                    long newAmount;
                    if (scaleFactor >= 1.0) {
                        // 倍增：四舍五入，但至少为1
                        newAmount = Math.max(1, Math.round(originalAmount * scaleFactor));
                    } else {
                        // 除法：必须能整除
                        double divisor = 1.0 / scaleFactor;
                        if (originalAmount % divisor == 0) {
                            newAmount = Math.max(1, (long)(originalAmount / divisor));
                        } else {
                            // 不能整除，返回null表示失败
                            return null;
                        }
                    }

                    scaledOutputs.add(new GenericStack(output.what(), newAmount));
                }
            }

            // 创建新的处理样板（1.21.1 接口接受 List）
            return PatternDetailsHelper.encodeProcessingPattern(
                scaledInputs,
                scaledOutputs
            );

        } catch (Exception e) {
            return null;
        }
    }
}