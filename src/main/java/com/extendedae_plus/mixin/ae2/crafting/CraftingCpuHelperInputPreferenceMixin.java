package com.extendedae_plus.mixin.ae2.crafting;

import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import com.extendedae_plus.util.crafting.SuperMatrixInputPreference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 执行期选料重排：当当前提取由超级矩阵服务时（{@link SuperMatrixInputPreference} 上下文已设置），
 * 把候选料里属于本作业中间产物的项排到末尾，使可替换样板优先选用叶子原料，
 * 避免中间产物被抢导致上层配方断供。详见 {@link SuperMatrixInputPreference}。
 */
@Mixin(value = CraftingCpuHelper.class, remap = false)
public abstract class CraftingCpuHelperInputPreferenceMixin {

    @Inject(method = "getValidItemTemplates", at = @At("RETURN"), cancellable = true)
    private static void eap$preferLeafOverIntermediate(CallbackInfoReturnable<Iterable<InputTemplate>> cir) {
        if (SuperMatrixInputPreference.current() == null) {
            return;
        }
        var reordered = SuperMatrixInputPreference.reorderOrNull(cir.getReturnValue());
        if (reordered != null) {
            cir.setReturnValue(reordered);
        }
    }
}
