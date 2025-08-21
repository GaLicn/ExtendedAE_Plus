package com.extendedae_plus.mixin.ae2;

import java.util.Collections;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.api.stacks.GenericStack;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extendedae_plus.api.AdvancedBlockingHolder;

@Mixin(PatternProviderLogic.class)
public class PatternProviderLogicAdvancedMixin implements AdvancedBlockingHolder {
    @Unique
    private static final String EPP_ADV_BLOCKING_KEY = "epp_advanced_blocking";

    @Unique
    private boolean epp$advancedBlocking = false;

    @Override
    public boolean ext$getAdvancedBlocking() {
        return epp$advancedBlocking;
    }

    @Override
    public void ext$setAdvancedBlocking(boolean value) {
        this.epp$advancedBlocking = value;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
    private void epp$writeAdvancedToNbt(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(EPP_ADV_BLOCKING_KEY, this.epp$advancedBlocking);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void epp$readAdvancedFromNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(EPP_ADV_BLOCKING_KEY)) {
            this.epp$advancedBlocking = tag.getBoolean(EPP_ADV_BLOCKING_KEY);
        } else {
        }
    }

    // 在 pushPattern 中，重定向对 adapter.containsPatternInput(...) 的调用
    @Redirect(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/helpers/patternprovider/PatternProviderTarget;containsPatternInput(Ljava/util/Set;)Z"), remap = false)
    private boolean epp$redirectBlockingContains(PatternProviderTarget adapter,
                                                 java.util.Set<AEKey> patternInputs,
                                                 IPatternDetails patternDetails,
                                                 appeng.api.stacks.KeyCounter[] inputHolder) {
        // 原版是否打开阻挡
        boolean vanillaBlocking = ((PatternProviderLogic)(Object)this).isBlocking();
        if (!vanillaBlocking) {
            return adapter.containsPatternInput(patternInputs);
        }

        // 仅当高级阻挡启用时启用“匹配则不阻挡”
        if (this.epp$advancedBlocking) {
            if (epp$targetFullyMatchesPatternInputs(adapter, patternDetails)) {
                // 返回 false 表示“不包含阻挡关键物”，从而不触发 continue，允许发配
                return false;
            }
        }
        // 否则使用原判定
        return adapter.containsPatternInput(patternInputs);
    }

    @Unique
    private boolean epp$targetFullyMatchesPatternInputs(PatternProviderTarget adapter, IPatternDetails patternDetails) {
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
}
