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
            RecipeTypeNameConfig.presetCraftingProviderSearchKey();
            return;
        }

        String searchKey = recipeBase == null ? null : RecipeTypeNameConfig.mapRecipeTypeToSearchKey(recipeBase);
        if ((searchKey == null || searchKey.isBlank()) && emiRecipe != null) {
            try {
                ResourceLocation categoryId = emiRecipe.getCategory().getId();
                searchKey = RecipeTypeNameConfig.resolveRecipeTypeSearchKey(categoryId, null);
            } catch (Throwable ignored) {
            }
        }

        if (searchKey != null && !searchKey.isBlank()) {
            RecipeTypeNameConfig.setLastProcessingName(searchKey);
        }
    }
}
