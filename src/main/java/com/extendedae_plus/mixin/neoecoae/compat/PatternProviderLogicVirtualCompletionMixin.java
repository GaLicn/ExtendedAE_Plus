package com.extendedae_plus.mixin.neoecoae.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge;
import com.extendedae_plus.mixin.neoecoae.accessor.ECOCraftingCPULogicAccessor;
import com.extendedae_plus.mixin.neoecoae.accessor.ECOExecutingCraftingJobAccessor;
import com.extendedae_plus.mixin.neoecoae.accessor.ECOTaskProgressAccessor;
import com.extendedae_plus.util.VirtualCraftingHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * NeoECOAE 版本的虚拟完成兼容逻辑。
 */
@Mixin(value = PatternProviderLogic.class, priority = 840, remap = false)
public abstract class PatternProviderLogicVirtualCompletionMixin {

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void eap$neoecoaeVirtualCompletion(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                || !(this instanceof PatternProviderLogicVirtualCompatBridge bridge)
                || !bridge.eap$compatIsVirtualCraftingEnabled()) {
            return;
        }

        VirtualCraftingHelper.executeVirtualCompletion(
                bridge.eap$compatGetMainNode(),
                patternDetails,
                new VirtualCraftingHelper.VirtualCompletionHandler<ECOCraftingCPU, ECOTaskProgressAccessor>() {
                    @Override
                    public boolean isValidCPU(ICraftingCPU cpu) {
                        return cpu instanceof ECOCraftingCPU;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public Map<IPatternDetails, ECOTaskProgressAccessor> getTasks(ECOCraftingCPU cpu) {
                        var logic = cpu.getLogic();
                        if (logic instanceof ECOCraftingCPULogicAccessor la) {
                            var job = la.eap$getECOJob();
                            if (job instanceof ECOExecutingCraftingJobAccessor ja) {
                                return (Map<IPatternDetails, ECOTaskProgressAccessor>) (Map<?, ?>) ja.eap$getECOTasks();
                            }
                        }
                        return null;
                    }

                    @Override
                    public long getProgressValue(ECOTaskProgressAccessor progress) {
                        return progress.eap$getECOValue();
                    }

                    @Override
                public boolean finishJob(ECOCraftingCPU cpu) {
                    try {
                        var logic = cpu.getLogic();
                        if (logic instanceof ECOCraftingCPULogicAccessor la) {
                            la.eap$invokeECOFinishJob(true);
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                    cpu.cancelJob();
                    return true;
                }
                }
        );
    }
}
