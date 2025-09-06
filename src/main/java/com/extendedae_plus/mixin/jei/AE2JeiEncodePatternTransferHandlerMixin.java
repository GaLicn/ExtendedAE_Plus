package com.extendedae_plus.mixin.jei;

import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tamaized.ae2jeiintegration.integration.modules.jei.transfer.EncodePatternTransferHandler;

/**
 * 针对 AE2 JEI Integration 的转移处理器：在点击 JEI 的 "+" 将配方填入编码终端时，
 * 捕获处理配方并记录一个可用于搜索的关键字，以便 ProviderSelectScreen 自动预填搜索框。
 */
@Mixin(value = EncodePatternTransferHandler.class, remap = false)
public abstract class AE2JeiEncodePatternTransferHandlerMixin<T extends PatternEncodingTermMenu> {

    @Inject(method = "transferRecipe", at = @At("HEAD"), require = 0, remap = false)
    private void extendedae_plus$captureProcessingName(T menu,
                                                       Object recipeBase,
                                                       IRecipeSlotsView slotsView,
                                                       Player player,
                                                       boolean maxTransfer,
                                                       boolean doTransfer,
                                                       CallbackInfoReturnable<mezz.jei.api.recipe.transfer.IRecipeTransferError> cir) {
        if (!doTransfer) return;
        String name = null;
        Recipe<?> recipe = null;
        if (recipeBase instanceof RecipeHolder<?> holder) {
            recipe = holder.value();
        }
        if (recipe != null) {
            // 仅记录处理配方（非 3x3 合成）
            if (EncodingHelper.isSupportedCraftingRecipe(recipe)) return;
            name = ExtendedAEPatternUploadUtil.mapRecipeTypeToSearchKey(recipe);
        } else {
            // 非原版 Recipe<?> 的显示，尝试从 recipeBase 类名/包名推导关键词
            name = ExtendedAEPatternUploadUtil.deriveSearchKeyFromUnknownRecipe(recipeBase);
        }
        if (name != null && !name.isBlank()) {
            ExtendedAEPatternUploadUtil.setLastProcessingName(name);
        }
    }
}
