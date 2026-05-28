package com.extendedae_plus.mixin.advancedae.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge;
import com.extendedae_plus.mixin.advancedae.accessor.AdvCraftingCPUAccessor;
import com.extendedae_plus.mixin.advancedae.accessor.AdvCraftingCPULogicAccessor;
import com.extendedae_plus.mixin.advancedae.accessor.AdvExecutingCraftingJobAccessor;
import com.extendedae_plus.mixin.advancedae.accessor.AdvExecutingCraftingJobTaskProgressAccessor;
import com.extendedae_plus.util.VirtualCraftingHelper;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * AdvancedAE 版本的虚拟完成兼容逻辑。
 */
@Mixin(value = PatternProviderLogic.class, priority = 850, remap = false)
public abstract class PatternProviderLogicVirtualCompletionMixin {

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void eap$advancedaeVirtualCompletion(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                || !(this instanceof PatternProviderLogicVirtualCompatBridge bridge)
                || !bridge.eap$compatIsVirtualCraftingEnabled()) {
            return;
        }

        VirtualCraftingHelper.executeVirtualCompletion(
                bridge.eap$compatGetMainNode(),
                patternDetails,
                new VirtualCraftingHelper.VirtualCompletionHandler<AdvCraftingCPU, AdvExecutingCraftingJobTaskProgressAccessor>() {
                    @Override
                    public boolean isValidCPU(ICraftingCPU cpu) {
                        return cpu instanceof AdvCraftingCPU;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public Map<IPatternDetails, AdvExecutingCraftingJobTaskProgressAccessor> getTasks(
                            AdvCraftingCPU cpu) {
                        var logic = cpu.craftingLogic;
                        if (logic instanceof AdvCraftingCPULogicAccessor la) {
                            var job = la.eap$getAdvJob();
                            if (job instanceof AdvExecutingCraftingJobAccessor ja) {
                                return (Map<IPatternDetails, AdvExecutingCraftingJobTaskProgressAccessor>) (Map<?, ?>) ja.eap$getAdvTasks();
                            }
                        }
                        return null;
                    }

                    @Override
                    public long getProgressValue(AdvExecutingCraftingJobTaskProgressAccessor progress) {
                        return progress.eap$getAdvValue();
                    }

                    @Override
                    public boolean finishJob(AdvCraftingCPU cpu) {
                        try {
                            ((AdvCraftingCPUAccessor) cpu).eap$invokeUpdateOutput(null);
                            if (cpu.craftingLogic instanceof AdvCraftingCPULogicAccessor la) {
                                la.eap$invokeAdvFinishJob(true);
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
