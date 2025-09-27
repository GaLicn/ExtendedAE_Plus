package com.extendedae_plus.mixin.recipeViewer.emi;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiEncodePatternHandler.class, remap = false)
public abstract class MixinEncodePatternTransfer {
    @Inject(method = "transferRecipe(Lappeng/menu/AEBaseMenu;Lnet/minecraft/world/item/crafting/RecipeHolder;Ldev/emi/emi/api/recipe/EmiRecipe;Z)Lappeng/integration/modules/emi/AbstractRecipeHandler$Result;",
            at = @At("HEAD"), remap = false, require = 0)
    private static void onTransfer(AEBaseMenu menu, RecipeHolder<?> holder,
                                   EmiRecipe emiRecipe, boolean doTransfer, CallbackInfoReturnable<?> cir) {
        if (!doTransfer) return;
        var recipe = holder != null ? holder.value() : null;
        // 技术力不够,忍痛不兼容gtceu(
        // 对不起老牛😭
        if (recipe == null) return;
        if (EncodingHelper.isSupportedCraftingRecipe(recipe)) return;

        String name = ExtendedAEPatternUploadUtil.mapRecipeTypeToSearchKey(recipe);
        if (!(name == null || name.isBlank()))
            ExtendedAEPatternUploadUtil.addLastProcessingNameList(name);

        ExtendedAEPatternUploadUtil.addLastProcessingNameList(emiRecipe.getCategory().getName().getString());

        if (emiRecipe.getId() != null)
            ExtendedAEPatternUploadUtil.addLastProcessingNameList(emiRecipe.getId().toString().split("/")[0]);
    }
}
