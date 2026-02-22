package com.extendedae_plus.util;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方查找工具类
 *
 * <p>根据物品查找相关配方，优先返回工作台配方（CraftingRecipe）</p>
 */
public class RecipeFinderUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAE Plus - RecipeFinder");

    /**
     * 根据JEI物品查找相关配方
     *
     * @param ingredient JEI物品
     * @param level 当前世界
     * @return 相关配方列表
     */
    public static List<Recipe<?>> findRecipesByIngredient(ITypedIngredient<?> ingredient, Level level) {
        if (ingredient.getType() == VanillaTypes.ITEM_STACK) {
            ItemStack stack = (ItemStack) ingredient.getIngredient();
            return findRecipesByItem(stack, level);
        }

        LOGGER.warn("[RecipeFinder] Unsupported ingredient type: {}", ingredient.getType());
        // TODO: Support fluids, chemicals, and other AE2-compatible types
        return List.of();
    }

    /**
     * 根据物品查找相关配方
     *
     * @param item 目标物品
     * @param level 当前世界
     * @return 配方列表
     */
    private static List<Recipe<?>> findRecipesByItem(ItemStack item, Level level) {
        List<Recipe<?>> results = new ArrayList<>();
        int totalRecipes = level.getRecipeManager().getRecipes().size();

        // 1. 查找以该物品为输出的配方
        int outputMatches = 0;
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            if (matchesOutput(recipe, item)) {
                results.add(recipe);
                outputMatches++;
            }
        }

        // 2. 如果按住Shift，也查找以该物品为输入的配方
        if (Screen.hasShiftDown()) {
            int inputMatches = 0;
            for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
                if (matchesInput(recipe, item) && !results.contains(recipe)) {
                    results.add(recipe);
                    inputMatches++;
                }
            }
        }

        // 3. 优先级排序: CraftingRecipe优先
        results.sort((r1, r2) -> {
            boolean isCrafting1 = r1 instanceof CraftingRecipe;
            boolean isCrafting2 = r2 instanceof CraftingRecipe;
            if (isCrafting1 && !isCrafting2) return -1; // r1优先
            if (!isCrafting1 && isCrafting2) return 1;  // r2优先
            return 0; // 保持原顺序
        });

        return results;
    }

    /**
     * 选择最佳配方（优先选择工作台配方）
     *
     * @param recipes 配方列表
     * @return 最佳配方，如果列表为空返回null
     */
    public static Recipe<?> selectBestRecipe(List<Recipe<?>> recipes) {
        if (recipes.isEmpty()) {
            return null;
        }

        // 优先返回CraftingRecipe
        for (Recipe<?> recipe : recipes) {
            if (recipe instanceof CraftingRecipe) {
                return recipe;
            }
        }

        // 没有工作台配方，返回第一个
        return recipes.get(0);
    }

    /**
     * 检查配方输出是否匹配目标物品
     */
    private static boolean matchesOutput(Recipe<?> recipe, ItemStack target) {
        try {
            ItemStack result = recipe.getResultItem(null);
            boolean matches = ItemStack.isSameItemSameTags(result, target);
            return matches;
        } catch (Exception e) {
            LOGGER.warn("[RecipeFinder] Exception in matchesOutput for recipe {}: {}", recipe.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查配方输入是否包含目标物品
     */
    private static boolean matchesInput(Recipe<?> recipe, ItemStack target) {
        try {
            boolean matches = recipe.getIngredients().stream()
                .anyMatch(ingredient -> ingredient.test(target));
            return matches;
        } catch (Exception e) {
            LOGGER.warn("[RecipeFinder] Exception in matchesInput for recipe {}: {}", recipe.getId(), e.getMessage());
            return false;
        }
    }
}
