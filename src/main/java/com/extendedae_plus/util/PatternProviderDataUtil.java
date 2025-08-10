package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * 样板供应器数据工具类
 * 用于获取样板供应器中的所有样板数据，包括输入输出物品的数量信息
 */
public class PatternProviderDataUtil {

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
            GenericStack[] patternOutputs = patternDetails.getOutputs();
            for (int i = 0; i < patternOutputs.length; i++) {
                GenericStack output = patternOutputs[i];
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
            // 根据AE2源码，制作样板使用AECraftingPattern类
            return patternDetails.getClass().getSimpleName().equals("AECraftingPattern");
        }

        /**
         * 检查是否为处理样板
         * 通过检查实现类型来判断
         */
        public boolean isProcessingPattern() {
            // 处理样板使用AEProcessingPattern类
            return patternDetails.getClass().getSimpleName().equals("AEProcessingPattern");
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

        // 通过反射安全地访问host字段获取Level
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
            // 如果反射失败，返回空列表
            return patternDataList;
        }
        
        if (level == null) {
            return patternDataList;
        }

        // 遍历所有样板槽位
        for (int i = 0; i < patternInventory.size(); i++) {
            ItemStack patternStack = patternInventory.getStackInSlot(i);
            if (!patternStack.isEmpty()) {
                // 解码样板
                IPatternDetails patternDetails = PatternDetailsHelper.decodePattern(patternStack, level);
                if (patternDetails != null) {
                    patternDataList.add(new PatternData(patternDetails, patternStack, i));
                }
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

        // 通过反射安全地访问host字段获取Level
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
            return null;
        }
        
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
        // 检查网格节点是否活跃
        try {
            // 使用反射安全地访问mainNode字段
            var mainNodeField = patternProvider.getClass().getDeclaredField("mainNode");
            mainNodeField.setAccessible(true);
            var mainNode = mainNodeField.get(patternProvider);
            if (mainNode != null) {
                var isActiveMethod = mainNode.getClass().getMethod("isActive");
                return (Boolean) isActiveMethod.invoke(mainNode);
            }
        } catch (Exception e) {
            // 如果反射失败，假设是活跃的
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
     * 
     * @param patternProvider 样板供应器逻辑
     * @param multiplier 倍数（必须大于0）
     * @return 缩放操作结果
     */
    public static PatternScalingResult multiplyPatternAmounts(PatternProviderLogic patternProvider, double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("倍数必须大于0");
        }
        return scalePatternAmounts(patternProvider, multiplier, true);
    }

    /**
     * 对样板供应器中的所有样板进行输入输出数量倍除
     * 
     * @param patternProvider 样板供应器逻辑
     * @param divisor 除数（必须大于0）
     * @return 缩放操作结果
     */
    public static PatternScalingResult dividePatternAmounts(PatternProviderLogic patternProvider, double divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("除数必须大于0");
        }
        return scalePatternAmounts(patternProvider, 1.0 / divisor, false);
    }

    /**
     * 内部方法：执行样板数量缩放
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

                // 替换原样板
                patternInventory.setItemDirect(i, scaledPatternStack);
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
            // 检查样板类型
            boolean isCraftingPattern = originalPattern.getClass().getSimpleName().equals("AECraftingPattern");
            boolean isProcessingPattern = originalPattern.getClass().getSimpleName().equals("AEProcessingPattern");

            if (isCraftingPattern) {
                // 合成样板不参与缩放，直接返回null表示跳过
                return null;
            } else if (isProcessingPattern) {
                // 如果是除法操作（scaleFactor < 1），需要检查是否可以除
                if (scaleFactor < 1.0) {
                    double divisor = 1.0 / scaleFactor;
                    if (!canDivideProcessingPattern(originalPattern, divisor)) {
                        // 不能被除，返回null表示跳过
                        return null;
                    }
                }
                // 只有处理样板才进行缩放
                return scaleProcessingPattern(originalPattern, scaleFactor);
            } else {
                // 对于未知类型的样板，也尝试作为处理样板处理
                if (scaleFactor < 1.0) {
                    double divisor = 1.0 / scaleFactor;
                    if (!canDivideProcessingPattern(originalPattern, divisor)) {
                        return null;
                    }
                }
                return scaleProcessingPattern(originalPattern, scaleFactor);
            }
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
            GenericStack[] originalOutputs = originalPattern.getOutputs();

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
     * 缩放处理样板
     */
    private static ItemStack scaleProcessingPattern(IPatternDetails originalPattern, double scaleFactor) {
        try {
            // 获取原始输入输出
            IPatternDetails.IInput[] originalInputs = originalPattern.getInputs();
            GenericStack[] originalOutputs = originalPattern.getOutputs();

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

            // 创建新的处理样板
            return PatternDetailsHelper.encodeProcessingPattern(
                scaledInputs.toArray(new GenericStack[0]),
                scaledOutputs.toArray(new GenericStack[0])
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