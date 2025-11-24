package com.extendedae_plus.mixin.advancedae.compat;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge;
import com.extendedae_plus.mixin.advancedae.accessor.AdvCraftingCPULogicAccessor;
import com.extendedae_plus.mixin.advancedae.accessor.AdvExecutingCraftingJobAccessor;
import com.extendedae_plus.mixin.advancedae.accessor.AdvExecutingCraftingJobTaskProgressAccessor;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PatternProviderLogic.class, priority = 450, remap = false)
public abstract class PatternProviderLogicVirtualCompletionMixin {

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void eap$advancedaeVirtualCompletion(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (!(this instanceof PatternProviderLogicVirtualCompatBridge bridge)) {
            return;
        }
        if (!bridge.eap$compatIsVirtualCraftingEnabled()) {
            return;
        }

        var grid = bridge.eap$compatGetGrid();
        if (grid == null) {
            return;
        }

        ICraftingService craftingService = grid.getCraftingService();
        if (craftingService == null) {
            return;
        }

        for (ICraftingCPU cpu : craftingService.getCpus()) {
            if (!cpu.isBusy()) {
                continue;
            }

            if (cpu instanceof AdvCraftingCPU advCpu) {
                var logic = advCpu.craftingLogic;
                if (logic instanceof AdvCraftingCPULogicAccessor advLogicAccessor) {
                    var job = advLogicAccessor.eap$getAdvJob();
                    if (job != null && job instanceof AdvExecutingCraftingJobAccessor advJobAccessor) {
                        var tasks = advJobAccessor.eap$getAdvTasks();
                        var progress = tasks.get(patternDetails);
                        if (progress instanceof AdvExecutingCraftingJobTaskProgressAccessor advProgressAccessor) {
                            if (advProgressAccessor.eap$getAdvValue() <= 1) {
                                advCpu.cancelJob();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
