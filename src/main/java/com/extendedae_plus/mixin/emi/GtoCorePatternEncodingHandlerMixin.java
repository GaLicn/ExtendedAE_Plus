package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GTOCore 使用独立 EMI 处理器时，在填充编码终端前记录供应器搜索关键字。 */
@Mixin(targets = "com.gtocore.integration.emi.GTAe2PatternTerminalHandler", remap = false)
@Pseudo
public abstract class GtoCorePatternEncodingHandlerMixin {

    @Inject(method = "craft", at = @At("HEAD"), remap = false, require = 0)
    private void eap$captureRecipeSearchKey(EmiRecipe recipe, EmiCraftContext<?> context,
            CallbackInfoReturnable<Boolean> cir) {
        if (recipe == null) {
            return;
        }

        if (eap$isCraftingRecipe(recipe)) {
            RecipeTypeNameConfig.presetCraftingProviderSearchKey();
            return;
        }

        String searchKey = eap$resolveProcessingSearchKey(recipe);
        if (searchKey != null && !searchKey.isBlank()) {
            RecipeTypeNameConfig.setLastProcessingName(searchKey);
        }
    }

    @Unique
    private static boolean eap$isCraftingRecipe(EmiRecipe recipe) {
        // GTOCore 将切石机配方作为其自定义合成分支处理。
        return "dev.emi.emi.recipe.EmiStonecuttingRecipe".equals(recipe.getClass().getName());
    }

    @Unique
    private static String eap$resolveProcessingSearchKey(EmiRecipe recipe) {
        try {
            ResourceLocation categoryId = recipe.getCategory().getId();
            String mapped = RecipeTypeNameConfig.resolveRecipeTypeSearchKey(categoryId, null);
            if (mapped != null && !mapped.isBlank()) {
                return mapped;
            }
        } catch (Throwable ignored) {
        }

        // GTOCore 的 GTEMIRecipe ID 通常为 gtceu:recipe_type/recipe_name，取类型前缀。
        try {
            ResourceLocation recipeId = recipe.getId();
            if (recipeId != null) {
                String path = recipeId.getPath();
                int separator = path.indexOf('/');
                if (separator > 0) {
                    return RecipeTypeNameConfig.resolveProviderSearchKey(
                            recipeId.getNamespace() + ":" + path.substring(0, separator));
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
