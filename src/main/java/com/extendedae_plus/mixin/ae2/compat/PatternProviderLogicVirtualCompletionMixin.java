package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge;
import com.extendedae_plus.mixin.ae2.accessor.CraftingCPUClusterAccessor;
import com.extendedae_plus.mixin.ae2.accessor.CraftingCpuLogicAccessor;
import com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobAccessor;
import com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobTaskProgressAccessor;
import com.extendedae_plus.util.VirtualCraftingHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * AE2 版本的虚拟完成兼容逻辑。
 */
@Mixin(value = PatternProviderLogic.class, priority = 900, remap = false)
public abstract class PatternProviderLogicVirtualCompletionMixin {

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void eap$ae2VirtualCompletion(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !(this instanceof PatternProviderLogicVirtualCompatBridge bridge)
                || !bridge.eap$compatIsVirtualCraftingEnabled()) {
            return;
        }

        VirtualCraftingHelper.executeVirtualCompletion(
            bridge.eap$compatGetMainNode(),
            patternDetails,
            new VirtualCraftingHelper.VirtualCompletionHandler<CraftingCPUCluster, ExecutingCraftingJobTaskProgressAccessor>() {
                @Override
                public boolean isValidCPU(ICraftingCPU cpu) {
                    return cpu instanceof CraftingCPUCluster;
                }

                @Override
                public Map<IPatternDetails, ExecutingCraftingJobTaskProgressAccessor> getTasks(CraftingCPUCluster cpu) {
                    var logic = cpu.craftingLogic;
                    if (logic instanceof CraftingCpuLogicAccessor la) {
                        var job = la.eap$getJob();
                        if (job instanceof ExecutingCraftingJobAccessor ja) {
                            return ja.eap$getTasks();
                        }
                    }
                    return null;
                }

                @Override
                public long getProgressValue(ExecutingCraftingJobTaskProgressAccessor progress) {
                    return progress.eap$getValue();
                }

                @Override
                public boolean finishJob(CraftingCPUCluster cpu) {
                    try {
                        ((CraftingCPUClusterAccessor) (Object) cpu).eap$invokeUpdateOutput(null);
                        if (cpu.craftingLogic instanceof CraftingCpuLogicAccessor la) {
                            la.eap$invokeFinishJob(true);
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
