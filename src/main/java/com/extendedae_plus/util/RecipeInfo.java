package com.extendedae_plus.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方完整信息
 * 
 * <p>包含配方的所有输入材料（带数量）和输出物品/流体</p>
 */
public class RecipeInfo {
    private final Recipe<?> recipe;
    private final boolean isCraftingRecipe;
    private final List<List<GenericStack>> inputs;  // 每个槽位的所有可能材料（物品或流体，包含数量）
    private final List<GenericStack> outputs;       // 输出材料（物品或流体，包含数量）

    public RecipeInfo(
        Recipe<?> recipe,
        boolean isCraftingRecipe,
        List<List<GenericStack>> inputs,
        List<GenericStack> outputs
    ) {
        this.recipe = recipe;
        this.isCraftingRecipe = isCraftingRecipe;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    /**
     * 获取原始配方对象
     */
    public Recipe<?> getRecipe() {
        return recipe;
    }

    /**
     * 是否为工作台配方
     */
    public boolean isCraftingRecipe() {
        return isCraftingRecipe;
    }

    /**
     * 获取输入材料列表
     * 
     * @return 每个槽位的所有可能材料列表（物品或流体，包含数量）
     */
    public List<List<GenericStack>> getInputs() {
        return inputs;
    }

    /**
     * 获取输出材料列表
     * 
     * @return 输出材料列表（物品或流体，包含数量）
     */
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    /**
     * 应用 JEI 书签优先级选择最佳输入材料
     * 
     * @param bookmarkPriorities 书签优先级映射（物品 -> 优先级，数值越小优先级越高）
     * @return 选择的材料列表（每个槽位一个材料，转换为 ItemStack 用于网络传输）
     */
    public List<ItemStack> selectBestInputs(java.util.Map<net.minecraft.world.item.Item, Integer> bookmarkPriorities) {
        List<ItemStack> selected = new ArrayList<>();
        
        for (List<GenericStack> slotOptions : inputs) {
            if (slotOptions.isEmpty()) {
                selected.add(ItemStack.EMPTY);
                continue;
            }
            
            // 选择优先级最高的材料（如果都不在书签中，选第一个）
            GenericStack best = slotOptions.get(0);
            int bestPriority = getPriority(best, bookmarkPriorities);
            
            for (int i = 1; i < slotOptions.size(); i++) {
                GenericStack option = slotOptions.get(i);
                int priority = getPriority(option, bookmarkPriorities);
                if (priority < bestPriority) {
                    bestPriority = priority;
                    best = option;
                }
            }
            
            // 转换为 ItemStack（流体会被包装）
            selected.add(toItemStack(best));
        }
        
        return selected;
    }

    /**
     * 获取材料的优先级
     */
    private int getPriority(GenericStack stack, java.util.Map<net.minecraft.world.item.Item, Integer> priorities) {
        if (stack.what() instanceof AEItemKey itemKey) {
            return priorities.getOrDefault(itemKey.getItem(), Integer.MAX_VALUE);
        }
        // 流体没有书签优先级，返回默认值
        return Integer.MAX_VALUE;
    }

    /**
     * 将 GenericStack 转换为 ItemStack
     * 
     * <p>物品直接转换，流体会被包装成 GenericStack.wrapInItemStack</p>
     */
    private ItemStack toItemStack(GenericStack stack) {
        if (stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) stack.amount());
        } else if (stack.what() instanceof AEFluidKey) {
            // 流体需要包装成特殊的 ItemStack
            return GenericStack.wrapInItemStack(stack);
        }
        return ItemStack.EMPTY;
    }
}
