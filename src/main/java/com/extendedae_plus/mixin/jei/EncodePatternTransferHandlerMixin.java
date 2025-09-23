package com.extendedae_plus.mixin.jei;

import appeng.integration.modules.jei.transfer.EncodePatternTransferHandler;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 捕获通过 JEI 点击 + 填充到样板编码终端的处理配方，并记录其工艺名称（如“烧炼”）。
 */
@Mixin(value = EncodePatternTransferHandler.class, remap = false)
public abstract class EncodePatternTransferHandlerMixin {

    @Inject(method = "transferRecipe", at = @At("HEAD"), require = 0)
    private void eap$captureProcessingName(PatternEncodingTermMenu menu,
                                                       Object recipeBase,
                                                       IRecipeSlotsView slotsView,
                                                       Player player,
                                                       boolean maxTransfer,
                                                       boolean doTransfer,
                                                       CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (!doTransfer) return;
        String name = null;
        if (recipeBase instanceof Recipe<?> recipe) {
            // 仅记录处理配方（非 3x3 合成）
            if (EncodingHelper.isSupportedCraftingRecipe(recipe)) return;
            name = ExtendedAEPatternUploadUtil.mapRecipeTypeToSearchKey(recipe);
        } else if (recipeBase != null &&
                   "com.gregtechceu.gtceu.api.recipe.GTRecipe".equals(recipeBase.getClass().getName())) {
            // 反射路径：GTCEu 专用，从 GTRecipeType 提取注册ID并映射为中文或path
            name = ExtendedAEPatternUploadUtil.mapGTCEuRecipeToSearchKey(recipeBase);
        } else if ("com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper".equals(recipeBase.getClass().getName())) {
            // 通过反射处理 GTCEu JEI 包装类，避免硬依赖
            try {
                var field = recipeBase.getClass().getField("recipe"); // public final GTRecipe recipe;
                Object inner = field.get(recipeBase);
                // 反射路径：将内部 GTRecipe 以 Object 传入
                name = ExtendedAEPatternUploadUtil.mapGTCEuRecipeToSearchKey(inner);
            } catch (Throwable ignored) {
                // 反射失败则继续走通用回退
            }
        } else {
            // 非原版 Recipe<?> 的 JEI 条目，尝试从类名/包名推导关键词
            name = ExtendedAEPatternUploadUtil.deriveSearchKeyFromUnknownRecipe(recipeBase);
        }
        if (name != null && !name.isBlank()) {
            ExtendedAEPatternUploadUtil.setLastProcessingName(name);
        }
    }
}
