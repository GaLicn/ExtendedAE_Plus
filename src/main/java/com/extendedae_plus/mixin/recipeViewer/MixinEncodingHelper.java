package com.extendedae_plus.mixin.recipeViewer;

import appeng.api.stacks.AEKey;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import com.extendedae_plus.integration.RecipeViewer.RecipeViewerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(EncodingHelper.class)
public class MixinEncodingHelper {
    // 客户端：注入优先使用JEI书签的物品，流体
    @Inject(method = "getIngredientPriorities", at = @At("TAIL"), cancellable = true, remap = false)
    private static void epp$addJeiIngredientPriorities(MEStorageMenu menu, Comparator<GridInventoryEntry> comparator, CallbackInfoReturnable<Map<AEKey, Integer>> cir){
        Map<AEKey, Integer> result = cir.getReturnValue();
        AtomicInteger index = new AtomicInteger(Integer.MAX_VALUE);
        RecipeViewerHelper.getFavorites().forEach(favorite -> {
            if (favorite != null) result.put(favorite.what(), index.getAndDecrement());
        });
        cir.setReturnValue(result);
    }
}
