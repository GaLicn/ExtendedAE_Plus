package com.extendedae_plus.mixin.emi;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
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
        EmiFavorites.favorites.forEach(favorite -> {
            Object stack = favorite.getEmiStacks().getFirst().getKey();
            if (stack instanceof Item item) result.put(AEItemKey.of(item), index.getAndDecrement());
            else if (stack instanceof Fluid fluid) result.put(AEFluidKey.of(fluid), index.getAndDecrement());
        });
        cir.setReturnValue(result);

    }
}
