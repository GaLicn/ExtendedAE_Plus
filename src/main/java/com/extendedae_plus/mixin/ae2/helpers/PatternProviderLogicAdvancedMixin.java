package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.extendedae_plus.api.advancedBlocking.IAdvancedBlocking;
import com.extendedae_plus.api.ids.EAPComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

@Mixin(value = PatternProviderLogic.class, remap = false)
public class PatternProviderLogicAdvancedMixin implements IAdvancedBlocking {
    @Unique
    private static final String EAP_ADV_BLOCKING_KEY = "epp_advanced_blocking";

    @Unique
    private boolean eap$advancedBlocking = false;

    @Override
    public boolean eap$getAdvancedBlocking() {
        return this.eap$advancedBlocking;
    }

    @Override
    public void eap$setAdvancedBlocking(boolean value) {
        this.eap$advancedBlocking = value;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$writeAdvancedToNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putBoolean(EAP_ADV_BLOCKING_KEY, this.eap$advancedBlocking);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$readAdvancedFromNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
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
        boolean vanillaBlocking = ((PatternProviderLogic) (Object) this).isBlocking();
        if (!vanillaBlocking) {
            return adapter.containsPatternInput(patternInputs);
        }

        // 仅当高级阻挡启用时启用“匹配则不阻挡”
        if (this.eap$advancedBlocking) {
            if (this.eap$targetFullyMatchesPatternInputs(adapter, patternDetails)) {
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

    @Shadow
    public void saveChanges() {}

    @Inject(method = "exportSettings", at = @At("TAIL"))
    private void onExportSettings(DataComponentMap.Builder builder, CallbackInfo ci) {
        builder.set(EAPComponents.ADVANCED_BLOCKING, this.eap$advancedBlocking);
    }

    @Inject(method = "importSettings", at = @At("TAIL"))
    private void onImportSettings(DataComponentMap input, Player player, CallbackInfo ci) {
        this.eap$advancedBlocking = Boolean.TRUE.equals(input.get(EAPComponents.ADVANCED_BLOCKING.get()));
        // 持久化到 world
        this.saveChanges();
    }
}
