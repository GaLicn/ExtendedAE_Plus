package com.extendedae_plus.mixin.emi;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 针对 AE2 自带的 EMI 样板编码处理器：在点击 EMI 的 "+" 将配方填入编码终端时，
 * 捕获处理配方并记录可用于搜索的关键字，以便 ProviderSelectScreen 自动预填搜索框。
 * 对无原版 RecipeHolder 的自定义配方，回退使用 EMI 配方分类 id 的 path 作为关键字，
 * 避免每次上传都弹出供应器选择界面。
 */
@Mixin(value = EmiEncodePatternHandler.class, remap = false)
public abstract class AE2EmiEncodePatternTransferHandlerMixin {

    @Inject(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Lnet/minecraft/world/item/crafting/RecipeHolder;Ldev/emi/emi/api/recipe/EmiRecipe;Z)Lappeng/integration/modules/emi/AbstractRecipeHandler$Result;",
        at = @At("HEAD"), require = 0, remap = false)
    private void extendedae_plus$captureProcessingName(PatternEncodingTermMenu menu,
                                                       RecipeHolder<?> holder,
                                                       EmiRecipe emiRecipe,
                                                       boolean doTransfer,
                                                       CallbackInfoReturnable<?> cir) {
        if (!doTransfer) return;
        String name = null;
        Recipe<?> recipe = holder != null ? holder.value() : null;
        if (recipe != null) {
            // 合成样板固定使用 "crafting" 关键字（可被用户别名覆盖）
            if (EncodingHelper.isSupportedCraftingRecipe(recipe)) {
                ExtendedAEPatternUploadUtil.presetCraftingProviderSearchKey();
                return;
            }
            name = ExtendedAEPatternUploadUtil.mapRecipeTypeToSearchKey(recipe);
            if (name == null || name.isBlank()) {
                // 注册表反查失败时仍按配方类名生成搜索词，避免搜索框完全为空。
                name = ExtendedAEPatternUploadUtil.deriveSearchKeyFromUnknownRecipe(recipe);
            }
        } else if (emiRecipe != null
                && emiRecipe.getCategory() != null
                && emiRecipe.getCategory().getId() != null) {
            // 无原版 Recipe<?> 的自定义配方显示：以 EMI 分类 id 的 path 作为搜索关键字
            ResourceLocation categoryId = emiRecipe.getCategory().getId();
            name = ExtendedAEPatternUploadUtil.resolveSearchKeyAlias(categoryId.getPath());
        }
        if (name != null && !name.isBlank()) {
            ExtendedAEPatternUploadUtil.setLastProcessingName(name);
        }
    }
}
