package com.extendedae_plus.mixin.advancedae.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import com.extendedae_plus.mixin.advancedae.accessor.AdvExecutingCraftingJobTaskProgressAccessor;
import com.extendedae_plus.util.smartDoubling.CraftingTaskPatternNbt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** 让 AdvancedAE 量子计算机的合成任务存读档时保留样板的缩放倍率与 Strict 包装。 */
@Mixin(value = ExecutingCraftingJob.class, remap = false)
public abstract class AdvExecutingCraftingJobPatternNbtMixin {

    @Shadow
    @Final
    Map<IPatternDetails, AdvExecutingCraftingJobTaskProgressAccessor> tasks;

    /** 读档循环中当前的任务条目：由 fromTag 记下，紧随其后的 decodePattern 消费。 */
    @Unique
    private @Nullable CompoundTag eap$pendingTask;

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void eap$writeTaskPatterns(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        CraftingTaskPatternNbt.writeTasks(cir.getReturnValue(), registries, this.tasks,
                AdvExecutingCraftingJobTaskProgressAccessor::eap$getAdvValue);
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lnet/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob$CraftingDifferenceListener;Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;)V",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/stacks/AEItemKey;fromTag(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;"))
    private AEItemKey eap$captureTask(HolderLookup.Provider registries, CompoundTag item, Operation<AEItemKey> original) {
        this.eap$pendingTask = item;
        return original.call(registries, item);
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lnet/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob$CraftingDifferenceListener;Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;)V",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/crafting/PatternDetailsHelper;decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;"))
    private IPatternDetails eap$restoreTaskPattern(AEItemKey what, Level level, Operation<IPatternDetails> original) {
        var item = this.eap$pendingTask;
        this.eap$pendingTask = null;
        return CraftingTaskPatternNbt.restore(original.call(what, level), item);
    }
}
