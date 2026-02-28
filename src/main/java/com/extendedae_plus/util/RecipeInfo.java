package com.extendedae_plus.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/**
 * 配方完整信息
 * 
 * <p>包含配方的所有输入材料（带数量）和输出物品</p>
 */
public class RecipeInfo {
    private final Recipe<?> recipe;
    private final boolean isCraftingRecipe;
    private final List<List<ItemStack>> inputs;  // 每个槽位的所有可能物品（包含数量）
    private final List<ItemStack> outputs;       // 输出物品（包含数量）

    public RecipeInfo(
        Recipe<?> recipe,
        boolean isCraftingRecipe,
        List<List<ItemStack>> inputs,
        List<ItemStack> outputs
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
     * @return 每个槽位的所有可能物品列表（包含数量）
     */
    public List<List<ItemStack>> getInputs() {
        return inputs;
    }

    /**
     * 获取输出物品列表
     * 
     * @return 输出物品列表（包含数量）
     */
    public List<ItemStack> getOutputs() {
        return outputs;
    }

    /**
     * 应用 JEI 书签优先级选择最佳输入材料
     * 
     * @param bookmarkPriorities 书签优先级映射（物品 -> 优先级，数值越小优先级越高）
     * @return 选择的材料列表（每个槽位一个物品）
     */
    public List<ItemStack> selectBestInputs(java.util.Map<net.minecraft.world.item.Item, Integer> bookmarkPriorities) {
        java.util.List<ItemStack> selected = new java.util.ArrayList<>();
        
        for (List<ItemStack> slotOptions : inputs) {
            if (slotOptions.isEmpty()) {
                selected.add(ItemStack.EMPTY);
                continue;
            }
            
            // 选择优先级最高的物品（如果都不在书签中，选第一个）
            ItemStack best = slotOptions.get(0);
            int bestPriority = bookmarkPriorities.getOrDefault(best.getItem(), Integer.MAX_VALUE);
            
            for (int i = 1; i < slotOptions.size(); i++) {
                ItemStack option = slotOptions.get(i);
                int priority = bookmarkPriorities.getOrDefault(option.getItem(), Integer.MAX_VALUE);
                if (priority < bestPriority) {
                    bestPriority = priority;
                    best = option;
                }
            }
            
            selected.add(best.copy());
        }
        
        return selected;
    }
}
