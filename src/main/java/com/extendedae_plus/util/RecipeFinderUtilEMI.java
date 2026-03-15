package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

/**
 * 基于 EMI 查找配方
 */
public final class RecipeFinderUtilEMI {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static EmiRecipeManager manager = EmiApi.getRecipeManager();

    @Nullable
    public static EmiRecipe findRecipeById(ResourceLocation location) {
        return manager.getRecipe(location);
    }

    @Nullable
    public static EmiRecipe findRecipeById(String id) {
        return findRecipeById(ResourceLocation.parse(id));
    }

    @Nullable
    public static Component getWorkstationComponentByRecipeId(String id) {
        EmiRecipe recipe = RecipeFinderUtilEMI.findRecipeById(id);
        if (recipe != null) {
            List<EmiIngredient> workstations = EmiApi.getRecipeManager().getWorkstations(recipe.getCategory());
            Component c;
            if (!workstations.isEmpty()) {
                c = workstations.getFirst().getEmiStacks().getFirst().getName();
            } else {
                c = recipe.getCategory().getName();
            }
            return c;
        }
        return null;
    }

    public static boolean isRecipeEqualToPattern(ItemStack itemStack, ResourceLocation location, Level level) {
        EmiRecipe recipe = findRecipeById(location);
        // 检查样板与配方是否对应
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(itemStack, level);
        if (pattern != null) {
            List<GenericStack> stacks = pattern.getOutputs();
            if (stacks != null && recipe != null) {
                List<EmiStack> stacks2 = recipe.getOutputs();

                List<ResourceLocation> ids1 = stacks.stream().map(s -> s.what().getId()).sorted().toList();
                List<ResourceLocation> ids2 = stacks2.stream().map(EmiStack::getId).sorted().toList();

                return ids1.equals(ids2);
            }
        }
        return false;
    }
}
