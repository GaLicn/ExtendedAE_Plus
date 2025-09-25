package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.mojang.logging.LogUtils;
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
    private static final boolean DEBUG = true; // 临时诊断开关，发布可设为 false

    /**
     * 样板数据类，包含样板的输入输出信息
     */
    public static class PatternData {
        private final IPatternDetails patternDetails;
        private final ItemStack patternStack;
        private final int slotIndex;
        private final List<InputData> inputs;
        private final List<OutputData> outputs;

        public PatternData(IPatternDetails patternDetails, ItemStack patternStack, int slotIndex) {
            this.patternDetails = patternDetails;
            this.patternStack = patternStack;
            this.slotIndex = slotIndex;
            this.inputs = new ArrayList<>();
            this.outputs = new ArrayList<>();
            
            // 解析输入数据
            parseInputs();
            // 解析输出数据
            parseOutputs();
        }

        private void parseInputs() {
            IPatternDetails.IInput[] patternInputs = patternDetails.getInputs();
            for (int i = 0; i < patternInputs.length; i++) {
                IPatternDetails.IInput input = patternInputs[i];
                GenericStack[] possibleInputs = input.getPossibleInputs();
                long multiplier = input.getMultiplier();
                
                for (GenericStack possibleInput : possibleInputs) {
                    if (possibleInput != null && possibleInput.what() != null) {
                        inputs.add(new InputData(possibleInput.what(), possibleInput.amount() * multiplier, i));
                    }
                }
            }
        }

        private void parseOutputs() {
            var patternOutputs = patternDetails.getOutputs();
            for (int i = 0; i < patternOutputs.size(); i++) {
                GenericStack output = patternOutputs.get(i);
                if (output != null && output.what() != null) {
                    outputs.add(new OutputData(output.what(), output.amount(), i));
                }
            }
        }

        public IPatternDetails getPatternDetails() {
            return patternDetails;
        }

        public ItemStack getPatternStack() {
            return patternStack;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        public List<InputData> getInputs() {
            return inputs;
        }

        public List<OutputData> getOutputs() {
            return outputs;
        }

        /**
         * 获取样板的定义键
         */
        public String getPatternDefinition() {
            return patternDetails.getDefinition().toString();
        }

        /**
         * 检查是否为制作样板
         * 通过检查实现类型来判断
         */
        public boolean isCraftingPattern() {
            // 使用样板定义判断，避免依赖类名（发布版会被混淆）
            String def = String.valueOf(patternDetails.getDefinition()).toLowerCase();
            return def.contains("crafting");
        }

        /**
         * 检查是否为处理样板
         * 通过检查实现类型来判断
         */
        public boolean isProcessingPattern() {
            // 使用样板定义判断处理类样板（含处理/石切/锻造等非合成）
            String def = String.valueOf(patternDetails.getDefinition()).toLowerCase();
            return def.contains("processing") || def.contains("stonecutting") || def.contains("smithing");
        }

        /**
         * 检查是否为石切样板
         * 通过检查定义类型来判断
         */
        public boolean isStonecuttingPattern() {
            return patternDetails.getDefinition().toString().contains("stonecutting");
        }

        /**
         * 检查是否为锻造样板
         * 通过检查定义类型来判断
         */
        public boolean isSmithingPattern() {
            return patternDetails.getDefinition().toString().contains("smithing");
        }

        /**
         * 获取样板的输出数量（主要输出）
         */
        public long getPrimaryOutputAmount() {
            if (outputs.isEmpty()) {
                return 0;
            }
            return outputs.get(0).getAmount();
        }

        /**
         * 检查样板是否有副产品
         */
        public boolean hasSecondaryOutputs() {
            return outputs.size() > 1;
        }
    }

    /**
     * 输入数据类
     */
    public static class InputData {
        private final AEKey key;
        private final long amount;
        private final int inputIndex;

        public InputData(AEKey key, long amount, int inputIndex) {
            this.key = key;
            this.amount = amount;
            this.inputIndex = inputIndex;
        }

        public AEKey getKey() {
            return key;
        }

        public long getAmount() {
            return amount;
        }

        public int getInputIndex() {
            return inputIndex;
        }

        public String getDisplayName() {
            return key.getDisplayName().getString();
        }
    }

    /**
     * 输出数据类
     */
    public static class OutputData {
        private final AEKey key;
        private final long amount;
        private final int outputIndex;

        public OutputData(AEKey key, long amount, int outputIndex) {
            this.key = key;
            this.amount = amount;
            this.outputIndex = outputIndex;
        }

        public AEKey getKey() {
            return key;
        }

        public long getAmount() {
            return amount;
        }

        public int getOutputIndex() {
            return outputIndex;
        }

        public String getDisplayName() {
            return key.getDisplayName().getString();
        }
    }

    /**
     * 获取样板供应器中的所有样板数据
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 所有样板的详细信息列表
     */
    public static List<PatternData> getAllPatternData(PatternProviderLogic patternProvider) {
        List<PatternData> patternDataList = new ArrayList<>();
        
        if (patternProvider == null) {
            return patternDataList;
        }

        // 获取样板库存
        InternalInventory patternInventory = patternProvider.getPatternInv();
        if (patternInventory == null) {
            return patternDataList;
        }
        // 获取 Level（使用 mixin accessor 替代反射）
        Level level = getPatternProviderLevel(patternProvider);
        if (level == null) {
            return patternDataList;
        }

        // 遍历所有样板槽位
        for (int i = 0; i < patternInventory.size(); i++) {
            ItemStack patternStack = patternInventory.getStackInSlot(i);
            if (patternStack.isEmpty()) continue;
            try {
                // 解码样板
                IPatternDetails patternDetails = PatternDetailsHelper.decodePattern(patternStack, level);
                if (patternDetails != null) {
                    patternDataList.add(new PatternData(patternDetails, patternStack, i));
                }
            } catch (Exception e) {
            }
        }

        return patternDataList;
    }

    /**
     * 获取样板供应器中的样板数量
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 样板数量
     */
    public static int getPatternCount(PatternProviderLogic patternProvider) {
        if (patternProvider == null) {
            return 0;
        }

        InternalInventory patternInventory = patternProvider.getPatternInv();
        if (patternInventory == null) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < patternInventory.size(); i++) {
            if (!patternInventory.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }

        return count;
    }

    /**
     * 获取样板供应器的总槽位数
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 总槽位数
     */
    public static int getTotalSlots(PatternProviderLogic patternProvider) {
        if (patternProvider == null) {
            return 0;
        }

        InternalInventory patternInventory = patternProvider.getPatternInv();
        return patternInventory != null ? patternInventory.size() : 0;
    }

    /**
     * 获取样板供应器的可用槽位数
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 可用槽位数
     */
    public static int getAvailableSlots(PatternProviderLogic patternProvider) {
        return getTotalSlots(patternProvider) - getPatternCount(patternProvider);
    }

    /**
     * 检查样板供应器是否有足够的槽位
     * 
     * @param patternProvider 样板供应器逻辑
     * @param requiredSlots 需要的槽位数
     * @return 是否有足够的槽位
     */
    public static boolean hasEnoughSlots(PatternProviderLogic patternProvider, int requiredSlots) {
        return getAvailableSlots(patternProvider) >= requiredSlots;
    }

    /**
     * 获取指定槽位的样板数据
     * 
     * @param patternProvider 样板供应器逻辑
     * @param slotIndex 槽位索引
     * @return 样板数据，如果槽位为空或无效则返回null
     */
    public static PatternData getPatternDataAtSlot(PatternProviderLogic patternProvider, int slotIndex) {
        if (patternProvider == null) {
            return null;
        }

        InternalInventory patternInventory = patternProvider.getPatternInv();
        if (patternInventory == null || slotIndex < 0 || slotIndex >= patternInventory.size()) {
            return null;
        }

        ItemStack patternStack = patternInventory.getStackInSlot(slotIndex);
        if (patternStack.isEmpty()) {
            return null;
        }

        Level level = getPatternProviderLevel(patternProvider);
        if (level == null) {
            return null;
        }

        IPatternDetails patternDetails = PatternDetailsHelper.decodePattern(patternStack, level);
        if (patternDetails == null) {
            return null;
        }

        return new PatternData(patternDetails, patternStack, slotIndex);
    }

    /**
     * 检查样板供应器是否为空
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 是否为空
     */
    public static boolean isEmpty(PatternProviderLogic patternProvider) {
        return getPatternCount(patternProvider) == 0;
    }

    /**
     * 检查样板供应器是否已满
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 是否已满
     */
    public static boolean isFull(PatternProviderLogic patternProvider) {
        return getAvailableSlots(patternProvider) == 0;
    }

    /**
     * 获取样板供应器的优先级
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 优先级值
     */
    public static int getPatternPriority(PatternProviderLogic patternProvider) {
        if (patternProvider == null) {
            return 0;
        }
        return patternProvider.getPatternPriority();
    }

    /**
     * 检查样板供应器是否连接到ME网络
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 是否连接到网络
     */
    public static boolean isConnectedToNetwork(PatternProviderLogic patternProvider) {
        if (patternProvider == null) {
            return false;
        }
        return patternProvider.getGrid() != null;
    }

    /**
     * 判断 provider 是否可用并属于指定网格（在线且有频道/处于活跃状态）
     */
    public static boolean isProviderAvailable(PatternProviderLogic provider, IGrid expectedGrid) {
        if (provider == null || expectedGrid == null) return false;
        try {
            var grid = provider.getGrid();
            if (grid == null || !grid.equals(expectedGrid)) return false;

            // 使用 accessor 获取 mainNode，再调用 isActive
            if (provider instanceof PatternProviderLogicAccessor accessor) {
                var mainNode = accessor.eap$mainNode();
                if (mainNode == null) return false;
                try {
                    var isActiveMethod = mainNode.getClass().getMethod("isActive");
                    Object active = isActiveMethod.invoke(mainNode);
                    if (active instanceof Boolean && !((Boolean) active)) return false;
                } catch (NoSuchMethodException nsme) {
                    // 没有 isActive 方法时，退回到检查 channels
                    try {
                        var getChannels = mainNode.getClass().getMethod("getChannels");
                        Object channels = getChannels.invoke(mainNode);
                        if (channels instanceof java.util.Collection) {
                            if (((java.util.Collection<?>) channels).isEmpty()) return false;
                        }
                    } catch (Exception ignored) {
                        // 无法判断 channels 时，认为不可用
                        return false;
                    }
                }
            } else {
                // 没有 accessor 的情况，尽量通过反射判断 mainNode.channels
                try {
                    var mainNodeField = provider.getClass().getDeclaredField("mainNode");
                    mainNodeField.setAccessible(true);
                    var mainNode = mainNodeField.get(provider);
                    if (mainNode == null) return false;
                    var getChannelsMethod = mainNode.getClass().getMethod("getChannels");
                    Object channels = getChannelsMethod.invoke(mainNode);
                    if (channels instanceof java.util.Collection) {
                        return !((java.util.Collection<?>) channels).isEmpty();
                    }
                } catch (Exception e) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查样板供应器是否处于活跃状态
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 是否活跃
     */
    public static boolean isActive(PatternProviderLogic patternProvider) {
        if (patternProvider == null) {
            return false;
        }
        var grid = patternProvider.getGrid();
        if (grid == null) {
            return false;
        }
        // 检查网格节点是否活跃（使用 accessor 代替反射）
        try {
            if (patternProvider instanceof PatternProviderLogicAccessor accessor) {
                var mainNode = accessor.eap$mainNode();
                if (mainNode != null) {
                    try {
                        var isActiveMethod = mainNode.getClass().getMethod("isActive");
                        return (Boolean) isActiveMethod.invoke(mainNode);
                    } catch (Exception e) {
                        // 无法调用 isActive 时，认为活跃
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    /**
     * 获取样板供应器的显示名称
     * 
     * @param patternProvider 样板供应器逻辑
     * @return 显示名称
     */
    public static String getDisplayName(PatternProviderLogic patternProvider) {
        if (patternProvider == null) {
            return "未知样板供应器";
        }
        
        try {
            var group = patternProvider.getTerminalGroup();
            if (group != null && group.name() != null) {
                return group.name().getString();
            }
        } catch (Exception e) {
            // 忽略异常，使用默认名称
        }
        
        return "样板供应器";
    }

    /**
     * 样板缩放操作结果
     */
    public static class PatternScalingResult {
        private final int totalPatterns;
        private final int scaledPatterns;
        private final int failedPatterns;
        private final List<String> errors;

        public PatternScalingResult(int totalPatterns, int scaledPatterns, int failedPatterns, List<String> errors) {
            this.totalPatterns = totalPatterns;
            this.scaledPatterns = scaledPatterns;
            this.failedPatterns = failedPatterns;
            this.errors = new ArrayList<>(errors);
        }

        public int getTotalPatterns() { return totalPatterns; }
        public int getScaledPatterns() { return scaledPatterns; }
        public int getFailedPatterns() { return failedPatterns; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        
        public boolean isSuccessful() { return failedPatterns == 0; }
        public boolean hasPartialSuccess() { return scaledPatterns > 0 && failedPatterns > 0; }
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
        return scalePatternAmountsExtendedAEStyle(patternProvider, multiplier, true);
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
     * ExtendedAE风格的样板复制倍增
     * 支持更精确的样板处理和错误恢复
     * 
     * @param patternProvider 样板供应器逻辑
     * @param multiplier 倍数
     * @return 缩放操作结果
     */
    public static PatternScalingResult duplicatePatternAmountsExtendedAEStyle(PatternProviderLogic patternProvider, double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("倍数必须大于0");
        }
        return scalePatternAmountsExtendedAEStyle(patternProvider, multiplier, true);
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
        return scalePatternAmountsExtendedAEStyle(patternProvider, 1.0 / divisor, false);
    }

    /**
     * ExtendedAE风格的样板数量缩放实现
     * 提供更好的错误处理和样板兼容性
     */
    private static PatternScalingResult scalePatternAmountsExtendedAEStyle(PatternProviderLogic patternProvider, double scaleFactor, boolean isMultiply) {
        List<String> errors = new ArrayList<>();
        int totalPatterns = 0;
        int scaledPatterns = 0;
        int failedPatterns = 0;

        if (patternProvider == null) {
            errors.add("样板供应器为null");
            return new PatternScalingResult(0, 0, 0, errors);
        }

        InternalInventory patternInventory = patternProvider.getPatternInv();
        if (patternInventory == null) {
            errors.add("无法访问样板库存");
            return new PatternScalingResult(0, 0, 0, errors);
        }

        // 获取Level对象 - ExtendedAE风格的安全获取
        Level level = getPatternProviderLevel(patternProvider);
        if (level == null) {
            errors.add("无法获取Level对象，请确保样板供应器在有效的世界中");
            return new PatternScalingResult(0, 0, 0, errors);
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
                    errors.add("槽位 " + i + ": 无法解码样板");
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
                        errors.add("槽位 " + i + ": 样板数量无法按指定比例缩放");
                        failedPatterns++;
                        continue;
                    }
                }

            } catch (Exception e) {
                errors.add("槽位 " + i + ": 样板验证失败 - " + e.getMessage());
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
                ItemStack scaledPatternStack = scalePatternExtendedAEStyle(originalPattern, scaleFactor, level);
                if (scaledPatternStack == null || scaledPatternStack.isEmpty()) {
                    errors.add("槽位 " + i + ": 样板缩放失败");
                    failedPatterns++;
                    continue;
                }

                // 应用缩放后的样板 - 使用正确的方法确保数据持久化
                setPatternWithPersistence(patternInventory, i, scaledPatternStack, patternProvider);
                scaledPatterns++;

            } catch (Exception e) {
                errors.add("槽位 " + i + ": 处理样板时发生异常 - " + e.getMessage());
                failedPatterns++;
                
                // ExtendedAE风格：出错时恢复原始样板
                try {
                    setPatternWithPersistence(patternInventory, i, originalPatterns.get(i), patternProvider);
                } catch (Exception restoreException) {
                    errors.add("槽位 " + i + ": 恢复原始样板失败 - " + restoreException.getMessage());
                }
            }
        }

        // 触发样板更新
        try {
            patternProvider.updatePatterns();
        } catch (Exception e) {
            errors.add("更新样板列表时发生异常: " + e.getMessage());
        }

        return new PatternScalingResult(totalPatterns, scaledPatterns, failedPatterns, errors);
    }

    /**
     * 内部方法：执行样板数量缩放（保留原有实现以兼容性）
     */
    private static PatternScalingResult scalePatternAmounts(PatternProviderLogic patternProvider, double scaleFactor, boolean isMultiply) {
        List<String> errors = new ArrayList<>();
        int totalPatterns = 0;
        int scaledPatterns = 0;
        int failedPatterns = 0;

        if (patternProvider == null) {
            errors.add("样板供应器为null");
            return new PatternScalingResult(0, 0, 0, errors);
        }

        InternalInventory patternInventory = patternProvider.getPatternInv();
        if (patternInventory == null) {
            errors.add("无法访问样板库存");
            return new PatternScalingResult(0, 0, 0, errors);
        }

        // 获取Level对象
        Level level = null;
        try {
            var hostField = patternProvider.getClass().getDeclaredField("host");
            hostField.setAccessible(true);
            var host = hostField.get(patternProvider);
            if (host != null) {
                var getBlockEntityMethod = host.getClass().getMethod("getBlockEntity");
                var blockEntity = getBlockEntityMethod.invoke(host);
                if (blockEntity != null) {
                    var getLevelMethod = blockEntity.getClass().getMethod("getLevel");
                    level = (Level) getLevelMethod.invoke(blockEntity);
                }
            }
        } catch (Exception e) {
            errors.add("无法获取Level对象: " + e.getMessage());
            return new PatternScalingResult(0, 0, 0, errors);
        }

        if (level == null) {
            errors.add("Level对象为null");
            return new PatternScalingResult(0, 0, 0, errors);
        }

        // 遍历所有样板槽位
        for (int i = 0; i < patternInventory.size(); i++) {
            ItemStack patternStack = patternInventory.getStackInSlot(i);
            if (patternStack.isEmpty()) {
                continue;
            }

            totalPatterns++;

            try {
                // 解码原始样板
                IPatternDetails originalPattern = PatternDetailsHelper.decodePattern(patternStack, level);
                if (originalPattern == null) {
                    errors.add("槽位 " + i + ": 无法解码样板");
                    failedPatterns++;
                    continue;
                }

                // 检查是否为合成样板
                boolean isCraftingPattern = originalPattern.getClass().getSimpleName().equals("AECraftingPattern");
                
                if (isCraftingPattern) {
                    // 合成样板跳过缩放，不计入失败数
                    continue;
                }

                // 如果是除法操作，检查是否可以被除
                if (scaleFactor < 1.0) {
                    double divisor = 1.0 / scaleFactor;
                    if (!canDivideProcessingPattern(originalPattern, divisor)) {
                        // 不能被除的样板跳过，不计入失败数，但记录信息
                        errors.add("槽位 " + i + ": 样板数量为1或不能被整除，跳过除法操作");
                        failedPatterns++;
                        continue;
                    }
                }

                // 缩放样板（只处理处理样板）
                ItemStack scaledPatternStack = scalePattern(originalPattern, scaleFactor, level);
                if (scaledPatternStack == null || scaledPatternStack.isEmpty()) {
                    errors.add("槽位 " + i + ": 样板缩放失败");
                    failedPatterns++;
                    continue;
                }

                // 替换原样板 - 使用正确的方法确保数据持久化
                setPatternWithPersistence(patternInventory, i, scaledPatternStack, patternProvider);
                scaledPatterns++;

            } catch (Exception e) {
                errors.add("槽位 " + i + ": 处理样板时发生异常 - " + e.getMessage());
                failedPatterns++;
            }
        }

        // 触发样板更新
        try {
            patternProvider.updatePatterns();
        } catch (Exception e) {
            errors.add("更新样板列表时发生异常: " + e.getMessage());
        }

        return new PatternScalingResult(totalPatterns, scaledPatterns, failedPatterns, errors);
    }

    /**
     * 缩放单个样板的输入输出数量
     * 注意：只有处理样板会被缩放，合成样板会被跳过
     */
    private static ItemStack scalePattern(IPatternDetails originalPattern, double scaleFactor, Level level) {
        try {
            // 基于定义字符串进行类型判断，避免类名混淆问题
            String def = String.valueOf(originalPattern.getDefinition()).toLowerCase();
            boolean isCrafting = def.contains("crafting");

            if (isCrafting) {
                // 合成样板不参与缩放
                return null;
            }

            // 非合成（处理/石切/锻造等）按处理样板流程处理
            if (scaleFactor < 1.0) {
                double divisor = 1.0 / scaleFactor;
                if (!canDivideProcessingPattern(originalPattern, divisor)) {
                    return null;
                }
            }
            return scaleProcessingPattern(originalPattern, scaleFactor);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查处理样板是否可以被除法操作
     * 如果输出为1且不能被整除，则不能再除
     */
    private static boolean canDivideProcessingPattern(IPatternDetails originalPattern, double divisor) {
        try {
            // 获取原始输入输出
            IPatternDetails.IInput[] originalInputs = originalPattern.getInputs();
            var originalOutputs = originalPattern.getOutputs();

            // 检查输入是否可以被除
            for (IPatternDetails.IInput input : originalInputs) {
                GenericStack[] possibleInputs = input.getPossibleInputs();
                long multiplier = input.getMultiplier();
                
                if (possibleInputs.length > 0 && possibleInputs[0] != null) {
                    GenericStack primaryInput = possibleInputs[0];
                    long currentAmount = primaryInput.amount() * multiplier;
                    
                    // 如果当前数量为1且不能被整除，则不能除
                    if (currentAmount == 1 || (currentAmount % divisor != 0 && currentAmount < divisor)) {
                        return false;
                    }
                }
            }

            // 检查输出是否可以被除
            for (GenericStack output : originalOutputs) {
                if (output != null && output.what() != null) {
                    long currentAmount = output.amount();
                    
                    // 如果当前数量为1且不能被整除，则不能除
                    if (currentAmount == 1 || (currentAmount % divisor != 0 && currentAmount < divisor)) {
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
    private static ItemStack scalePatternExtendedAEStyle(IPatternDetails originalPattern, double scaleFactor, Level level) {
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

    /**
     * 缩放处理样板（保留原有实现以兼容性）
     */
    private static ItemStack scaleProcessingPattern(IPatternDetails originalPattern, double scaleFactor) {
        try {
            // 获取原始输入输出
            IPatternDetails.IInput[] originalInputs = originalPattern.getInputs();
            var originalOutputs = originalPattern.getOutputs();

            // 缩放输入
            List<GenericStack> scaledInputs = new ArrayList<>();
            for (IPatternDetails.IInput input : originalInputs) {
                GenericStack[] possibleInputs = input.getPossibleInputs();
                long multiplier = input.getMultiplier();
                
                if (possibleInputs.length > 0 && possibleInputs[0] != null) {
                    GenericStack primaryInput = possibleInputs[0];
                    long newAmount = Math.max(1, Math.round(primaryInput.amount() * multiplier * scaleFactor));
                    scaledInputs.add(new GenericStack(primaryInput.what(), newAmount));
                }
            }

            // 缩放输出
            List<GenericStack> scaledOutputs = new ArrayList<>();
            for (GenericStack output : originalOutputs) {
                if (output != null && output.what() != null) {
                    long newAmount = Math.max(1, Math.round(output.amount() * scaleFactor));
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

    // 注意：根据用户要求，合成样板不参与任何缩放操作
    // 因此移除了 scaleCraftingPattern 方法

    /**
     * 预览样板缩放结果（不实际修改样板）
     * 
     * @param patternProvider 样板供应器逻辑
     * @param scaleFactor 缩放因子
     * @return 预览结果列表
     */
    public static List<PatternScalingPreview> previewPatternScaling(PatternProviderLogic patternProvider, double scaleFactor) {
        List<PatternScalingPreview> previews = new ArrayList<>();
        
        if (patternProvider == null || scaleFactor <= 0) {
            return previews;
        }

        List<PatternData> patterns = getAllPatternData(patternProvider);
        for (PatternData pattern : patterns) {
            // 只预览处理样板的缩放效果
            boolean isCraftingPattern = pattern.getPatternDetails().getClass().getSimpleName().equals("AECraftingPattern");
            
            if (!isCraftingPattern) {
                PatternScalingPreview preview = new PatternScalingPreview(
                    pattern.getSlotIndex(),
                    pattern.getPatternDetails(),
                    scaleFactor
                );
                previews.add(preview);
            }
        }

        return previews;
    }

    /**
     * 样板缩放预览类
     */
    public static class PatternScalingPreview {
        private final int slotIndex;
        private final IPatternDetails originalPattern;
        private final double scaleFactor;
        private final List<InputPreview> inputPreviews;
        private final List<OutputPreview> outputPreviews;

        public PatternScalingPreview(int slotIndex, IPatternDetails originalPattern, double scaleFactor) {
            this.slotIndex = slotIndex;
            this.originalPattern = originalPattern;
            this.scaleFactor = scaleFactor;
            this.inputPreviews = new ArrayList<>();
            this.outputPreviews = new ArrayList<>();
            
            calculatePreviews();
        }

        private void calculatePreviews() {
            // 预览输入变化
            for (IPatternDetails.IInput input : originalPattern.getInputs()) {
                GenericStack[] possibleInputs = input.getPossibleInputs();
                long multiplier = input.getMultiplier();
                
                if (possibleInputs.length > 0 && possibleInputs[0] != null) {
                    GenericStack primaryInput = possibleInputs[0];
                    long originalAmount = primaryInput.amount() * multiplier;
                    long newAmount = Math.max(1, Math.round(originalAmount * scaleFactor));
                    
                    inputPreviews.add(new InputPreview(
                        primaryInput.what(),
                        originalAmount,
                        newAmount
                    ));
                }
            }

            // 预览输出变化
            for (GenericStack output : originalPattern.getOutputs()) {
                if (output != null && output.what() != null) {
                    long originalAmount = output.amount();
                    long newAmount = Math.max(1, Math.round(originalAmount * scaleFactor));
                    
                    outputPreviews.add(new OutputPreview(
                        output.what(),
                        originalAmount,
                        newAmount
                    ));
                }
            }
        }

        // Getters
        public int getSlotIndex() { return slotIndex; }
        public double getScaleFactor() { return scaleFactor; }
        public List<InputPreview> getInputPreviews() { return new ArrayList<>(inputPreviews); }
        public List<OutputPreview> getOutputPreviews() { return new ArrayList<>(outputPreviews); }
    }

    /**
     * 输入预览类
     */
    public static class InputPreview {
        private final AEKey key;
        private final long originalAmount;
        private final long newAmount;

        public InputPreview(AEKey key, long originalAmount, long newAmount) {
            this.key = key;
            this.originalAmount = originalAmount;
            this.newAmount = newAmount;
        }

        public AEKey getKey() { return key; }
        public long getOriginalAmount() { return originalAmount; }
        public long getNewAmount() { return newAmount; }
        public String getDisplayName() { return key.getDisplayName().getString(); }
    }

    /**
     * 输出预览类
     */
    public static class OutputPreview {
        private final AEKey key;
        private final long originalAmount;
        private final long newAmount;

        public OutputPreview(AEKey key, long originalAmount, long newAmount) {
            this.key = key;
            this.originalAmount = originalAmount;
            this.newAmount = newAmount;
        }

        public AEKey getKey() { return key; }
        public long getOriginalAmount() { return originalAmount; }
        public long getNewAmount() { return newAmount; }
        public String getDisplayName() { return key.getDisplayName().getString(); }
    }
}