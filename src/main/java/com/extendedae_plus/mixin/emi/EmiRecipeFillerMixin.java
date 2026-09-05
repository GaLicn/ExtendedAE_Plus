package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.util.Logger;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.registry.EmiRecipeFiller;
import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 记录 EMI 最终选择的样板编码处理器，定位 GTOCore 与 AE2 的处理器竞争。 */
@Mixin(value = EmiRecipeFiller.class, remap = false)
public abstract class EmiRecipeFillerMixin {

    @Inject(method = "getFirstValidHandler", at = @At("RETURN"), remap = false)
    private static void eap$logSelectedHandler(EmiRecipe recipe, AbstractContainerScreen<?> screen,
                                                CallbackInfoReturnable<EmiRecipeHandler<?>> cir) {
        EmiRecipeHandler<?> handler = cir.getReturnValue();
        Logger.EAP$LOGGER.info("[UploadDebug] EMI selected handler: recipeClass={}, recipeId={}, category={}, screen={}, handler={}",
                recipe == null ? null : recipe.getClass().getName(),
                eap$getRecipeId(recipe),
                eap$getCategory(recipe),
                screen == null ? null : screen.getClass().getName(),
                handler == null ? null : handler.getClass().getName());
    }

    @Inject(method = "performFill", at = @At("HEAD"), remap = false)
    private static void eap$captureBeforeFill(EmiRecipe recipe, AbstractContainerScreen<?> screen,
                                               EmiCraftContext.Type type, EmiCraftContext.Destination destination,
                                               int amount, CallbackInfoReturnable<Boolean> cir) {
        if (recipe == null || screen == null
                || !"appeng.menu.me.items.PatternEncodingTermMenu".equals(screen.getMenu().getClass().getName())) {
            return;
        }

        EmiRecipeHandler<?> handler = EmiRecipeFiller.getFirstValidHandler(recipe, screen);
        if (handler == null || !handler.getClass().getName().equals("com.gtocore.integration.emi.GTAe2PatternTerminalHandler")) {
            return;
        }

        Object category = eap$getCategory(recipe);
        String key = eap$resolveCategorySearchKey(recipe, category);
        if (key != null && !key.isBlank()) {
            RecipeTypeNameConfig.setLastProcessingName(key);
        }
        Logger.EAP$LOGGER.info("[UploadDebug] EMI GTO fill capture: recipeClass={}, recipeId={}, category={}, key='{}', type={}, destination={}, amount={}",
                recipe.getClass().getName(), eap$getRecipeId(recipe), eap$getCategory(recipe), key, type, destination, amount);
    }

    private static Object eap$getRecipeId(EmiRecipe recipe) {
        try {
            return recipe == null ? null : recipe.getId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object eap$getCategory(EmiRecipe recipe) {
        try {
            return recipe == null ? null : recipe.getCategory().getId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String eap$resolveCategorySearchKey(EmiRecipe recipe, Object category) {
        try {
            String title = recipe.getCategory().getName().getString();
            var categoryId = recipe.getCategory().getId();
            return RecipeTypeNameConfig.resolveRecipeTypeSearchKey(categoryId, title);
        } catch (Throwable ignored) {
            return category == null ? null : category.toString();
        }
    }
}
