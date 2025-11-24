package com.extendedae_plus.mixin.jei;

import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.integration.modules.rei.transfer.EncodePatternTransferHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import me.shedaniel.rei.api.common.display.Display;

/**
 * 捕获通过 JEI 点击 + 填充到样板编码终端的处理配方，并记录其工艺名称（如“烧炼”）。
 */
@Mixin(value = EncodePatternTransferHandler.class, remap = false)
public abstract class EncodePatternTransferHandlerMixin {

    @Inject(method = "transferRecipe", at = @At("HEAD"), require = 0, remap = false)
    private void extendedae_plus$captureProcessingName(PatternEncodingTermMenu menu,
                                                       RecipeHolder<?> holder,
                                                       Display display,
                                                       boolean doTransfer,
                                                       CallbackInfoReturnable<me.shedaniel.rei.api.client.registry.transfer.TransferHandler.Result> cir) {
        if (!doTransfer) return;
        String name = null;
        Recipe<?> recipe = holder != null ? holder.value() : null;
        if (recipe != null) {
            // 仅记录处理配方（非 3x3 合成）
            if (EncodingHelper.isSupportedCraftingRecipe(recipe)) return;
            name = ExtendedAEPatternUploadUtil.mapRecipeTypeToSearchKey(recipe);
        } else {
            // 非原版 Recipe<?> 的显示，尝试从 display 类名/包名推导关键词
            name = ExtendedAEPatternUploadUtil.deriveSearchKeyFromUnknownRecipe(display);
        }
        if (name != null && !name.isBlank()) {
            ExtendedAEPatternUploadUtil.setLastProcessingName(name);
        }
    }
}
