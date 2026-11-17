package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobAccessor;
import com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobTaskProgressAccessor;
import com.extendedae_plus.util.smartDoubling.AdaptiveBatchLimitTracker;
import com.extendedae_plus.util.smartDoubling.AdaptiveBatchPreparation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicSmartDoublingMixin {
    @Shadow @Nullable private appeng.crafting.execution.ExecutingCraftingJob job;

    @Unique private IPatternDetails eap$activePattern;
    @Unique private IPatternDetails eap$executionPattern;
    @Unique private long eap$batchMultiplier = 1L;
    @Unique private AdaptiveBatchLimitTracker eap$batchLimits = new AdaptiveBatchLimitTracker();

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void eap$resetBatchContext(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        this.eap$batchLimits.beginJob(this.job);
        this.eap$clearBatchContext();
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void eap$clearBatchContext(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        this.eap$clearBatchContext();
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] eap$extractAdaptiveBatch(IPatternDetails details, ICraftingInventory inventory,
            Level level, KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
            Operation<KeyCounter[]> original) {
        this.eap$activePattern = details;
        this.eap$executionPattern = details;
        this.eap$batchMultiplier = 1L;

        KeyCounter[] oneCraftInputs = original.call(details, inventory, level, expectedOutputs, expectedContainerItems);
        if (oneCraftInputs == null || this.job == null
                || !(details instanceof ISmartDoublingAwarePattern aware) || !aware.eap$allowScaling()) {
            return oneCraftInputs;
        }
        if (!ModConfig.INSTANCE.smartScalingCycleBatchingEnable) {
            return oneCraftInputs;
        }
        var jobAccessor = (ExecutingCraftingJobAccessor) (Object) this.job;
        var progress = jobAccessor.extendedae_plus$getTasks().get(details);
        if (progress == null || progress.extendedae_plus$getValue() <= 1L) return oneCraftInputs;

        Map<IPatternDetails, Long> taskCrafts = new LinkedHashMap<>();
        for (var task : jobAccessor.extendedae_plus$getTasks().entrySet()) {
            taskCrafts.put(task.getKey(), task.getValue().extendedae_plus$getValue());
        }

        var finalOutput = jobAccessor.extendedae_plus$getFinalOutput();
        AdaptiveBatchPreparation.PreparedBatch batch = AdaptiveBatchPreparation.prepare(
                details, progress.extendedae_plus$getValue(), aware.eap$getMultiplierLimit(),
                ModConfig.INSTANCE.smartScalingMaxMultiplier, this.eap$batchLimits.getLimit(details),
                finalOutput == null ? null : finalOutput.what(), oneCraftInputs, expectedOutputs,
                expectedContainerItems, inventory, level, taskCrafts);
        if (batch == null) return oneCraftInputs;

        expectedOutputs.clear();
        expectedOutputs.addAll(batch.outputs());
        expectedContainerItems.clear();
        expectedContainerItems.addAll(batch.containerItems());
        this.eap$executionPattern = batch.pattern();
        this.eap$batchMultiplier = batch.multiplier();
        return batch.inputs();
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean eap$pushAdaptiveBatch(ICraftingProvider provider, IPatternDetails details,
            KeyCounter[] inputHolder, Operation<Boolean> original) {
        IPatternDetails executionPattern = details;
        if (details == this.eap$activePattern && this.eap$batchMultiplier > 1L) {
            executionPattern = this.eap$executionPattern;
        }
        boolean accepted = original.call(provider, executionPattern, inputHolder);
        if (details == this.eap$activePattern) {
            if (accepted) this.eap$batchLimits.recordAccepted(details, this.eap$batchMultiplier);
            else if (this.eap$batchMultiplier > 1L) this.eap$batchLimits.recordRejected(details, this.eap$batchMultiplier);
        }
        return accepted;
    }

    @ModifyExpressionValue(method = "executeCrafting", at = @At(value = "FIELD",
            target = "Lappeng/crafting/execution/ExecutingCraftingJob$TaskProgress;value:J",
            opcode = Opcodes.GETFIELD, ordinal = 1))
    private long eap$subtractLogicalBatchSize(long currentValue) {
        return currentValue - (this.eap$batchMultiplier - 1L);
    }

    @Unique
    private void eap$clearBatchContext() {
        this.eap$activePattern = null;
        this.eap$executionPattern = null;
        this.eap$batchMultiplier = 1L;
        this.eap$batchLimits.clearWhenIdle(this.job);
    }
}
