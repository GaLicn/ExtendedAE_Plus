package com.extendedae_plus.mixin.advancedae.helpers;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.extendedae_plus.api.AdvancedBlockingHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public class AdvPatternProviderLogicAdvancedMixin implements AdvancedBlockingHolder {
    @Unique
    private static final String EAP_ADV_BLOCKING_KEY = "eap_advanced_blocking";

    @Unique
    private boolean eap$advancedBlocking = false;

    @Override
    public boolean eap$getAdvancedBlocking() {
        return eap$advancedBlocking;
    }

    @Override
    public void eap$setAdvancedBlocking(boolean value) {
        this.eap$advancedBlocking = value;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$writeAdvancedToNbt(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(EAP_ADV_BLOCKING_KEY, this.eap$advancedBlocking);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$readAdvancedFromNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(EAP_ADV_BLOCKING_KEY)) {
            this.eap$advancedBlocking = tag.getBoolean(EAP_ADV_BLOCKING_KEY);
        }
    }

    // 在 pushPattern 中，重定向对 adapter.containsPatternInput(...) 的调用
    @Redirect(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/helpers/patternprovider/PatternProviderTarget;containsPatternInput(Ljava/util/Set;)Z"))
    private boolean eap$redirectBlockingContains(PatternProviderTarget adapter,
                                                 java.util.Set<AEKey> patternInputs,
                                                 IPatternDetails patternDetails,
                                                 appeng.api.stacks.KeyCounter[] inputHolder) {
        // 原版是否打开阻挡
        boolean vanillaBlocking = ((AdvPatternProviderLogic)(Object)this).isBlocking();
        if (!vanillaBlocking) {
            return adapter.containsPatternInput(patternInputs);
        }

        // 仅当高级阻挡启用时启用“匹配则不阻挡”
        if (this.eap$advancedBlocking) {
            if (eap$targetFullyMatchesPatternInputs(adapter, patternDetails)) {
                // 返回 false 表示“不包含阻挡关键物”，从而不触发 continue，允许发配
                return false;
            }
        }
        // 否则使用原判定
        return adapter.containsPatternInput(patternInputs);
    }

    @Unique
    private boolean eap$targetFullyMatchesPatternInputs(PatternProviderTarget adapter, IPatternDetails patternDetails) {
        for (IInput in : patternDetails.getInputs()) {
            boolean slotMatched = false;
            for (GenericStack candidate : in.getPossibleInputs()) {
                AEKey key = candidate.what().dropSecondary();
                if (adapter.containsPatternInput(Collections.singleton(key))) {
                    slotMatched = true;
                    break;
                }
            }
            if (!slotMatched) {
                return false; // 任一输入槽未匹配则失败
            }
        }
        return true; // 每个输入槽都至少匹配了一个候选输入
    }

    @Shadow public void saveChanges() {}

    @Inject(method = "exportSettings(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void onExportSettings(CompoundTag output, CallbackInfo ci) {
        System.out.println(this.eap$advancedBlocking);
        output.putBoolean(EAP_ADV_BLOCKING_KEY, this.eap$advancedBlocking);
    }

    @Inject(method = "importSettings(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
    private void onImportSettings(CompoundTag input, Player player, CallbackInfo ci) {
        if (input.contains(EAP_ADV_BLOCKING_KEY)) {
            this.eap$advancedBlocking = input.getBoolean(EAP_ADV_BLOCKING_KEY);
            // 持久化到 world
            this.saveChanges();
        }
    }
}
