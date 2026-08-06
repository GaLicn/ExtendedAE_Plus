package com.extendedae_plus.mixin.advancedae.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import com.extendedae_plus.api.crafting.IForcedCraftingPlan;
import com.extendedae_plus.api.crafting.IManualCraftingState;
import com.extendedae_plus.crafting.ForcedCraftingPlan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public abstract class AdvCraftingCPULogicManualWaitingMixin implements IManualCraftingState {
    @Unique
    private static final String EAP_MANUAL_WAITING_NBT_KEY = "extendedae_plus:manual_waiting";

    @Shadow
    private AdvCraftingCPU cpu;

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    private ListCraftingInventory inventory;

    @Shadow
    protected abstract void postChange(AEKey what);

    @Unique
    private final Map<AEKey, Long> eap$manualWaitingFor = new LinkedHashMap<>();

    @Override
    public void eap$setManualWaiting(KeyCounter manualWaiting) {
        this.eap$clearManualWaitingInternal(false);
        for (var entry : manualWaiting) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                this.eap$manualWaitingFor.put(entry.getKey(), entry.getLongValue());
                this.postChange(entry.getKey());
            }
        }
        if (!this.eap$manualWaitingFor.isEmpty()) {
            this.cpu.markDirty();
        }
    }

    @Override
    public long eap$getManualWaitingAmount(AEKey what) {
        if (what == null) {
            return 0;
        }
        return this.eap$manualWaitingFor.getOrDefault(what, 0L);
    }

    @Override
    public Map<AEKey, Long> eap$getManualWaitingSnapshot() {
        return new LinkedHashMap<>(this.eap$manualWaitingFor);
    }

    @Inject(method = "trySubmitJob", at = @At("RETURN"))
    private void eap$initManualWaitingForForcedPlan(IGrid grid, ICraftingPlan plan, IActionSource src,
            @Nullable ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (!cir.getReturnValue().successful()) {
            return;
        }

        KeyCounter manualMissing = null;
        if (plan instanceof IForcedCraftingPlan forcedPlan) {
            manualMissing = forcedPlan.eap$getManualMissingItems();
        } else if (plan instanceof ForcedCraftingPlan forcedCraftingPlan) {
            manualMissing = forcedCraftingPlan.eap$getManualMissingItems();
        }

        if (manualMissing != null) {
            this.eap$setManualWaiting(manualMissing);
        } else {
            this.eap$clearManualWaitingInternal(false);
        }
    }

    @Inject(method = "insert", at = @At("RETURN"), cancellable = true)
    private void eap$consumeManualWaitingAfterVanilla(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        if (what == null || this.job == null) {
            return;
        }

        long vanillaInserted = cir.getReturnValue();
        long remainingAmount = amount - vanillaInserted;
        if (remainingAmount <= 0) {
            return;
        }

        long manualWaiting = this.eap$getManualWaitingAmount(what);
        if (manualWaiting <= 0) {
            return;
        }

        long consumed = Math.min(remainingAmount, manualWaiting);
        if (type == Actionable.MODULATE) {
            this.eap$decreaseManualWaiting(what, consumed);
            this.cpu.markDirty();
            this.postChange(what);
            this.inventory.insert(what, consumed, Actionable.MODULATE);
        }

        cir.setReturnValue(vanillaInserted + consumed);
    }

    @Inject(method = "getWaitingFor", at = @At("RETURN"), cancellable = true)
    private void eap$appendManualWaitingToStatus(AEKey template, CallbackInfoReturnable<Long> cir) {
        if (template == null) {
            return;
        }
        long manualWaiting = this.eap$getManualWaitingAmount(template);
        if (manualWaiting > 0) {
            cir.setReturnValue(cir.getReturnValue() + manualWaiting);
        }
    }

    @Inject(method = "getAllWaitingFor", at = @At("TAIL"))
    private void eap$appendManualWaitingKeys(Set<AEKey> waitingFor, CallbackInfo ci) {
        waitingFor.addAll(this.eap$manualWaitingFor.keySet());
    }

    @Inject(method = "getAllItems", at = @At("TAIL"))
    private void eap$appendManualWaitingItems(KeyCounter out, CallbackInfo ci) {
        for (var entry : this.eap$manualWaitingFor.entrySet()) {
            out.add(entry.getKey(), entry.getValue());
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void eap$clearManualWaitingOnFinish(boolean success, CallbackInfo ci) {
        this.eap$clearManualWaitingInternal(true);
    }

    @Inject(method = "writeToNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void eap$writeManualWaitingToNbt(CompoundTag data, CallbackInfo ci) {
        data.remove(EAP_MANUAL_WAITING_NBT_KEY);
        if (this.job == null || this.eap$manualWaitingFor.isEmpty()) {
            return;
        }

        var entries = new ListTag();
        for (var entry : this.eap$manualWaitingFor.entrySet()) {
            var entryTag = entry.getKey().toTagGeneric();
            entryTag.putLong("#", entry.getValue());
            entries.add(entryTag);
        }
        data.put(EAP_MANUAL_WAITING_NBT_KEY, entries);
    }

    @Inject(method = "readFromNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void eap$readManualWaitingFromNbt(CompoundTag data, CallbackInfo ci) {
        this.eap$manualWaitingFor.clear();
        if (this.job == null) {
            return;
        }

        var entries = data.getList(EAP_MANUAL_WAITING_NBT_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entryTag = entries.getCompound(i);
            var key = AEKey.fromTagGeneric(entryTag);
            long amount = entryTag.getLong("#");
            if (key != null && amount > 0) {
                this.eap$manualWaitingFor.put(key, amount);
            }
        }
    }

    @Unique
    private void eap$decreaseManualWaiting(AEKey what, long amount) {
        long current = this.eap$manualWaitingFor.getOrDefault(what, 0L);
        if (current <= amount) {
            this.eap$manualWaitingFor.remove(what);
        } else {
            this.eap$manualWaitingFor.put(what, current - amount);
        }
    }

    @Unique
    private void eap$clearManualWaitingInternal(boolean notifyChanges) {
        if (this.eap$manualWaitingFor.isEmpty()) {
            return;
        }

        var previousKeys = new ArrayList<>(this.eap$manualWaitingFor.keySet());
        this.eap$manualWaitingFor.clear();
        if (notifyChanges) {
            for (var key : previousKeys) {
                this.postChange(key);
            }
        }
        this.cpu.markDirty();
    }
}
