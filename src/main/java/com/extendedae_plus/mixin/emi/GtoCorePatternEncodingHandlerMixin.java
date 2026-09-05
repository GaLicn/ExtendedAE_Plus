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

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/** GTOCore 使用独立 EMI 处理器时，在填充编码终端前记录供应器搜索关键字。 */
@Mixin(targets = "com.gtocore.integration.emi.GTAe2PatternTerminalHandler", remap = false)
@Pseudo
public abstract class GtoCorePatternEncodingHandlerMixin {

    @Inject(method = "craft(Ldev/emi/emi/api/recipe/EmiRecipe;Ldev/emi/emi/api/recipe/handler/EmiCraftContext;)Z",
            at = @At("HEAD"), remap = false, require = 1)
    private void eap$captureRecipeSearchKey(EmiRecipe recipe, EmiCraftContext<?> context,
            CallbackInfoReturnable<Boolean> cir) {
        if (recipe == null) {
            return;
        }

        if (eap$isCraftingRecipe(recipe)) {
            String categoryTitle = eap$getCategoryTitle(recipe);
            RecipeTypeNameConfig.setLastProcessingName(eap$resolveCategoryKey(recipe, categoryTitle));
            EAP$LOGGER.info("[UploadDebug] GTOCore EMI craft: recipeClass={}, id={}, category={}, recordedKey='{}'",
                    recipe.getClass().getName(), eap$getRecipeId(recipe), eap$getCategoryId(recipe),
                    RecipeTypeNameConfig.peekLastProviderSearchKey());
            return;
        }

        String searchKey = eap$resolveProcessingSearchKey(recipe);
        if (searchKey != null && !searchKey.isBlank()) {
            RecipeTypeNameConfig.setLastProcessingName(searchKey);
        }
        EAP$LOGGER.info("[UploadDebug] GTOCore EMI craft: recipeClass={}, id={}, category={}, resolvedKey='{}', recordedKey='{}'",
                recipe.getClass().getName(), eap$getRecipeId(recipe), eap$getCategoryId(recipe), searchKey,
                RecipeTypeNameConfig.peekLastProviderSearchKey());
    }

    @Unique
    private static Object eap$getRecipeId(EmiRecipe recipe) {
        try {
            return recipe.getId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static Object eap$getCategoryId(EmiRecipe recipe) {
        try {
            return recipe.getCategory().getId();
        } catch (Throwable ignored) {
            return null;
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
            String mapped = RecipeTypeNameConfig.resolveRecipeTypeSearchKey(categoryId, eap$getCategoryTitle(recipe));
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

    @Unique
    private static String eap$getCategoryTitle(EmiRecipe recipe) {
        try {
            return recipe.getCategory().getName().getString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static String eap$resolveCategoryKey(EmiRecipe recipe, String title) {
        try {
            return RecipeTypeNameConfig.resolveRecipeTypeSearchKey(recipe.getCategory().getId(), title);
        } catch (Throwable ignored) {
            return title;
        }
    }
}
