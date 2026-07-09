package com.extendedae_plus.mixin.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobAccessor;
import com.extendedae_plus.util.crafting.SuperMatrixInputPreference;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 让“由超级矩阵提供的可替换样板”在执行期选料时，优先选用叶子原料、避开本作业中间产物，
 * 从而修复高速派发下中间产物被替换样板抢占、上层配方断供卡死的问题。
 */
@Mixin(value = appeng.crafting.execution.CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicInputPreferenceMixin {

    @Shadow
    private ExecutingCraftingJob job;

    @Unique
    private CraftingService eap$craftingService;

    // 按作业缓存，避免每次提取都重建/重查（执行期每秒可达千万级调用）。
    @Unique
    private ExecutingCraftingJob eap$cachedJob;
    @Unique
    private Set<AEKey> eap$cachedIntermediates;
    @Unique
    private final Map<IPatternDetails, Boolean> eap$superMatrixServed = new IdentityHashMap<>();

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void eap$captureService(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        this.eap$craftingService = craftingService;
    }

    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs("
                    + "Lappeng/api/crafting/IPatternDetails;"
                    + "Lappeng/crafting/inv/ICraftingInventory;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lappeng/api/stacks/KeyCounter;"
                    + "Lappeng/api/stacks/KeyCounter;)"
                    + "[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] eap$wrapExtract(IPatternDetails details, ICraftingInventory sourceInv, Level level,
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
        boolean gated = this.eap$isSuperMatrixServed(details);
        if (gated) {
            SuperMatrixInputPreference.push(this.eap$intermediates());
        }
        try {
            return CraftingCpuHelper.extractPatternInputs(details, sourceInv, level,
                    expectedOutputs, expectedContainerItems);
        } finally {
            if (gated) {
                SuperMatrixInputPreference.pop();
            }
        }
    }

    @Unique
    private void eap$ensureCache() {
        if (this.job != this.eap$cachedJob) {
            this.eap$cachedJob = this.job;
            this.eap$superMatrixServed.clear();
            this.eap$cachedIntermediates = null;
        }
    }

    @Unique
    private boolean eap$isSuperMatrixServed(IPatternDetails details) {
        this.eap$ensureCache();
        Boolean cached = this.eap$superMatrixServed.get(details);
        if (cached != null) {
            return cached;
        }
        boolean served = false;
        if (this.eap$craftingService != null) {
            // 完整遍历一轮（不提前 break）：getProviders 返回的是 limit(cycleIterator, size)，
            // 消费整整 size 个元素后轮询偏移保持中性，不影响 AE2 自身的轮询分配。
            for (ICraftingProvider provider : this.eap$craftingService.getProviders(details)) {
                if (provider instanceof SuperAssemblerMatrixPart) {
                    served = true;
                }
            }
        }
        this.eap$superMatrixServed.put(details, served);
        return served;
    }

    @Unique
    private Set<AEKey> eap$intermediates() {
        this.eap$ensureCache();
        if (this.eap$cachedIntermediates != null) {
            return this.eap$cachedIntermediates;
        }
        Set<AEKey> set = new HashSet<>();
        if (this.job != null) {
            var tasks = ((ExecutingCraftingJobAccessor) (Object) this.job).eap$getTasks();
            for (IPatternDetails details : tasks.keySet()) {
                for (var output : details.getOutputs()) {
                    set.add(output.what());
                }
            }
        }
        this.eap$cachedIntermediates = set;
        return set;
    }
}
