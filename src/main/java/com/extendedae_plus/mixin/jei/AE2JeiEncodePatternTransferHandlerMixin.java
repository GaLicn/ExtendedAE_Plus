package com.extendedae_plus.mixin.jei;

import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
public abstract class AE2JeiEncodePatternTransferHandlerMixin {

    @Inject(
            method = "transferRecipe(Lnet/minecraft/world/inventory/AbstractContainerMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            remap = false)
    private void extendedae_plus$captureProcessingName(AbstractContainerMenu menu,
                                                       Object recipeBase,
                                                       IRecipeSlotsView slotsView,
                                                       Player player,
                                                       boolean maxTransfer,
                                                       boolean doTransfer,
                                                       CallbackInfoReturnable<mezz.jei.api.recipe.transfer.IRecipeTransferError> cir) {
        if (!doTransfer) return;
        String name = ExtendedAEPatternUploadUtil.mapRecipeObjectToSearchKey(recipeBase);
        if (name != null && !name.isBlank()) {
            ExtendedAEPatternUploadUtil.setLastProcessingName(name);
        }
    }
}
