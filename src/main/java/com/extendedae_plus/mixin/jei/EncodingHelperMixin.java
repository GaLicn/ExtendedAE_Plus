package com.extendedae_plus.mixin.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import com.extendedae_plus.compat.jei.JeiRuntimeCompat;
import com.extendedae_plus.util.ModCheckUtils;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
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
    // 客户端：注入优先使用JEI书签的物品，流体。
    // getIngredientPriorities 是 AE2 的 EMI / JEI 两条集成共用的方法，而 TMRV 会注册一个冒充的 jei，
    // 但其类路径上没有 mezz.jei.gui.*：一旦调用 JeiRuntimeCompat.getBookmarkList()，
    // 就会在解析 BookmarkOverlay 时抛 NoClassDefFoundError（表现为 EMI 的 "Error handling widget input"）。
    // 真 JEI 环境（含 EMI + JEI 共存）行为不变。
    @Inject(method = "getIngredientPriorities", at = @At("TAIL"), cancellable = true, remap = false)
    private static void epp$addJeiIngredientPriorities(MEStorageMenu menu, Comparator<GridInventoryEntry> comparator, CallbackInfoReturnable<Map<AEKey, Integer>> cir) {
        if (ModCheckUtils.isLoaded(ModCheckUtils.MODID_TMRV)) {
            return;
        }
        Map<AEKey, Integer> result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        AtomicInteger index = new AtomicInteger(Integer.MAX_VALUE);
        List<ITypedIngredient<?>> list = JeiRuntimeCompat.getBookmarkList();
        for (ITypedIngredient<?> ingredient : list) {
            ingredient.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(itemStack -> result.put(AEItemKey.of(itemStack), index.getAndDecrement()));
            ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).ifPresent(fluidStack -> result.put(AEFluidKey.of(fluidStack), index.getAndDecrement()));
        }
        cir.setReturnValue(result);
    }
}