package com.extendedae_plus.util;

import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 配方查找工具类
 *
 * <p>使用 JEI API 根据物品查找相关配方，返回包含完整数量信息的 RecipeInfo</p>
 */
public class RecipeFinderUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAE Plus - RecipeFinder");

    /**
     * 根据JEI物品查找相关配方（仅搜索以该物品为输出的配方）
     *
     * @param ingredient JEI物品
     * @return 相关配方信息列表（包含完整的输入输出数量）
     */
    public static List<RecipeInfo> findRecipesByIngredient(ITypedIngredient<?> ingredient) {
        // 获取 JEI Runtime
        IJeiRuntime jeiRuntime = JeiRuntimeProxy.get();
        if (jeiRuntime == null) {
            LOGGER.warn("[RecipeFinder] JEI Runtime not available");
            return List.of();
        }

        // 只支持物品类型
        if (ingredient.getType() != VanillaTypes.ITEM_STACK) {
            LOGGER.warn("[RecipeFinder] Unsupported ingredient type: {}", ingredient.getType());
            // TODO: Support fluids, chemicals, and other AE2-compatible types
            return List.of();
        }

        IJeiHelpers jeiHelpers = jeiRuntime.getJeiHelpers();
        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
        IFocusFactory focusFactory = jeiHelpers.getFocusFactory();
        
        // 创建输出焦点（OUTPUT role）
        IFocus<?> outputFocus = focusFactory.createFocus(
            RecipeIngredientRole.OUTPUT,
            ingredient
        );

        List<RecipeInfo> results = new ArrayList<>();

        // 查找工作台配方
        try {
            IRecipeCategory<CraftingRecipe> craftingCategory = recipeManager.getRecipeCategory(RecipeTypes.CRAFTING);
            
            recipeManager.createRecipeLookup(RecipeTypes.CRAFTING)
                .limitFocus(List.of(outputFocus))
                .get()
                .forEach(recipe -> {
                    // 创建配方布局以获取完整信息
                    Optional<IRecipeLayoutDrawable<CraftingRecipe>> layoutOpt = 
                        recipeManager.createRecipeLayoutDrawable(
                            craftingCategory,
                            recipe,
                            focusFactory.getEmptyFocusGroup()
                        );
                    
                    layoutOpt.ifPresent(layout -> {
                        RecipeInfo info = extractRecipeInfo(recipe, layout, true);
                        if (info != null) {
                            results.add(info);
                        }
                    });
                });
        } catch (Exception e) {
            LOGGER.warn("[RecipeFinder] Error searching crafting recipes: {}", e.getMessage());
        }

        // 查找其他所有配方类型（排除工作台配方）
        try {
            jeiHelpers.getAllRecipeTypes().forEach(recipeType -> {
                // 跳过工作台配方（已经处理过）
                if (recipeType.equals(RecipeTypes.CRAFTING)) {
                    return;
                }

                try {
                    @SuppressWarnings("unchecked")
                    IRecipeCategory<Recipe<?>> category = (IRecipeCategory<Recipe<?>>) recipeManager.getRecipeCategory(recipeType);
                    
                    recipeManager.createRecipeLookup(recipeType)
                        .limitFocus(List.of(outputFocus))
                        .get()
                        .forEach(recipe -> {
                            if (recipe instanceof Recipe<?> rawRecipe) {
                                // 创建配方布局以获取完整信息
                                Optional<IRecipeLayoutDrawable<Recipe<?>>> layoutOpt = 
                                    recipeManager.createRecipeLayoutDrawable(
                                        category,
                                        rawRecipe,
                                        focusFactory.getEmptyFocusGroup()
                                    );
                                
                                layoutOpt.ifPresent(layout -> {
                                    RecipeInfo info = extractRecipeInfo(rawRecipe, layout, false);
                                    if (info != null) {
                                        results.add(info);
                                    }
                                });
                            }
                        });
                } catch (Exception e) {
                    // 某些配方类型可能不支持，静默忽略
                }
            });
        } catch (Exception e) {
            LOGGER.warn("[RecipeFinder] Error searching other recipe types: {}", e.getMessage());
        }

        LOGGER.debug("[RecipeFinder] Found {} recipes for output: {}", 
            results.size(), 
            ((ItemStack) ingredient.getIngredient()).getDescriptionId());

        return results;
    }

    /**
     * 从配方布局中提取完整的配方信息
     *
     * @param recipe 原始配方对象
     * @param layout JEI 配方布局（包含完整的槽位和数量信息）
     * @param isCrafting 是否为工作台配方
     * @return 配方信息，如果提取失败返回 null
     */
    private static <T> RecipeInfo extractRecipeInfo(
        Recipe<?> recipe,
        IRecipeLayoutDrawable<T> layout,
        boolean isCrafting
    ) {
        try {
            IRecipeSlotsView slotsView = layout.getRecipeSlotsView();
            
            // 提取输入槽位
            List<IRecipeSlotView> inputSlots = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
            List<List<ItemStack>> inputs = new ArrayList<>();
            
            for (IRecipeSlotView slot : inputSlots) {
                List<ItemStack> slotItems = slot.getItemStacks()
                    .map(ItemStack::copy)  // 复制以保留数量信息
                    .toList();
                inputs.add(slotItems);
            }
            
            // 提取输出槽位
            List<IRecipeSlotView> outputSlots = slotsView.getSlotViews(RecipeIngredientRole.OUTPUT);
            List<ItemStack> outputs = new ArrayList<>();
            
            for (IRecipeSlotView slot : outputSlots) {
                slot.getItemStacks()
                    .map(ItemStack::copy)  // 复制以保留数量信息
                    .forEach(outputs::add);
            }
            
            return new RecipeInfo(recipe, isCrafting, inputs, outputs);
            
        } catch (Exception e) {
            LOGGER.warn("[RecipeFinder] Failed to extract recipe info for {}: {}", 
                recipe.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 选择最佳配方（优先选择工作台配方）
     *
     * @param recipes 配方信息列表
     * @return 最佳配方信息，如果列表为空返回null
     */
    public static RecipeInfo selectBestRecipe(List<RecipeInfo> recipes) {
        if (recipes.isEmpty()) {
            return null;
        }

        // 优先返回工作台配方
        for (RecipeInfo info : recipes) {
            if (info.isCraftingRecipe()) {
                return info;
            }
        }

        // 没有工作台配方，返回第一个
        return recipes.get(0);
    }
}
