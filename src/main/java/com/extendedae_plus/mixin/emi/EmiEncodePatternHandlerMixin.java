package com.extendedae_plus.mixin.emi;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.integration.modules.jeirei.EncodingHelper;
import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import appeng.menu.me.items.PatternEncodingTermMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/** 捕获 AE2 原生 EMI 填充样板动作，供随后点击上传按钮时预填供应器搜索框。 */
@Mixin(value = EmiEncodePatternHandler.class, remap = false)
public abstract class EmiEncodePatternHandlerMixin {

    @Inject(method = "transferRecipe", at = @At("HEAD"), remap = false, require = 0)
    private void eap$captureRecipeSearchKey(PatternEncodingTermMenu menu, Recipe<?> recipeBase,
            EmiRecipe emiRecipe, boolean doTransfer, CallbackInfoReturnable<Object> cir) {
        if (!doTransfer) {
            return;
        }

        if (recipeBase != null && EncodingHelper.isSupportedCraftingRecipe(recipeBase)) {
            String categoryTitle = eap$getCategoryTitle(emiRecipe);
            RecipeTypeNameConfig.setLastProcessingName(eap$resolveCategoryKey(emiRecipe, categoryTitle));
            EAP$LOGGER.info("[UploadDebug] AE2 EMI transfer: recipeClass={}, recipeType={}, emiId={}, category={}, resolvedKey='{}', recordedKey='{}'",
                    recipeBase.getClass().getName(), eap$getRecipeType(recipeBase),
                    emiRecipe == null ? null : emiRecipe.getId(),
                    emiRecipe == null ? null : eap$getCategory(emiRecipe), categoryTitle,
                    RecipeTypeNameConfig.peekLastProviderSearchKey());
            return;
        }

        String searchKey = recipeBase == null ? null : RecipeTypeNameConfig.mapRecipeTypeToSearchKey(recipeBase);
        if ((searchKey == null || searchKey.isBlank()) && emiRecipe != null) {
            try {
                ResourceLocation categoryId = emiRecipe.getCategory().getId();
                searchKey = RecipeTypeNameConfig.resolveRecipeTypeSearchKey(categoryId, eap$getCategoryTitle(emiRecipe));
            } catch (Throwable ignored) {
            }
        }

        if (searchKey != null && !searchKey.isBlank()) {
            RecipeTypeNameConfig.setLastProcessingName(searchKey);
        }
        EAP$LOGGER.info("[UploadDebug] AE2 EMI transfer: recipeClass={}, recipeType={}, emiId={}, category={}, resolvedKey='{}', recordedKey='{}'",
                recipeBase == null ? null : recipeBase.getClass().getName(),
                recipeBase == null ? null : eap$getRecipeType(recipeBase),
                emiRecipe == null ? null : emiRecipe.getId(),
                emiRecipe == null ? null : eap$getCategory(emiRecipe), searchKey,
                RecipeTypeNameConfig.peekLastProviderSearchKey());
    }

    private static Object eap$getRecipeType(Recipe<?> recipe) {
        try {
            return recipe.getType();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object eap$getCategory(EmiRecipe recipe) {
        try {
            return recipe.getCategory().getId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String eap$getCategoryTitle(EmiRecipe recipe) {
        try {
            return recipe == null ? null : recipe.getCategory().getName().getString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String eap$resolveCategoryKey(EmiRecipe recipe, String title) {
        try {
            return recipe == null ? title : RecipeTypeNameConfig.resolveRecipeTypeSearchKey(recipe.getCategory().getId(), title);
        } catch (Throwable ignored) {
            return title;
        }
    }
}
