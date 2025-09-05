package com.extendedae_plus.mixin.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(EncodingHelper.class)
public class EncodingHelperMixin {
    // 客户端：注入优先使用JEI书签的物品，流体
    @Inject(method = "getIngredientPriorities", at = @At("TAIL"), cancellable = true, remap = false)
    private static void epp$addJeiIngredientPriorities(MEStorageMenu menu, Comparator<GridInventoryEntry> comparator, CallbackInfoReturnable<Map<AEKey, Integer>> cir){
        Map<AEKey, Integer> result = cir.getReturnValue();
        AtomicInteger index = new AtomicInteger(Integer.MAX_VALUE);
        List<? extends ITypedIngredient<?>> list = JeiRuntimeProxy.getBookmarkList();
        for (ITypedIngredient<?> ingredient : list) {
            ingredient.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(itemStack -> result.put(AEItemKey.of(itemStack), index.getAndDecrement()));
            ingredient.getIngredient(ForgeTypes.FLUID_STACK).ifPresent(fluidStack -> result.put(AEFluidKey.of(fluidStack), index.getAndDecrement()));
        }
        cir.setReturnValue(result);
    }
}
