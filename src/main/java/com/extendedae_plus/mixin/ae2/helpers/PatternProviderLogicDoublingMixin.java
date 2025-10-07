package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderLogic.class, remap = false)
public class PatternProviderLogicDoublingMixin implements ISmartDoublingHolder {
    @Unique
    private static final String EAP_SMART_DOUBLING_KEY = "eap_smart_doubling";

    @Unique
    private boolean eap$smartDoubling = false;
    @Unique
    private static final Object EAP_SCALING_LOCK = new Object();
    @Unique
    private static final java.util.concurrent.ScheduledExecutorService EAP_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "eap-scaling-save"));
    @Unique
    private java.util.concurrent.ScheduledFuture<?> eap$pendingScalingSave = null;

    @Override
    public boolean eap$getSmartDoubling() {
        return eap$smartDoubling;
    }

    @Override
    public void eap$setSmartDoubling(boolean value) {
        this.eap$smartDoubling = value;
        // 立即将开关状态应用到当前 Provider 的样板上，避免等待下一次 updatePatterns
        try {
            var list = ((PatternProviderLogicAccessor) this).eap$patterns();
            for (IPatternDetails details : list) {
                if (details instanceof AEProcessingPattern proc && proc instanceof ISmartDoublingAwarePattern aware) {
                    aware.eap$setAllowScaling(value);
                }
            }
            // 触发一次刷新，让网络及时拿到最新状态（也会触发 ICraftingProvider.requestUpdate(mainNode)）
            ((PatternProviderLogic) (Object) this).updatePatterns();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$writeSmartDoublingToNbt(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(EAP_SMART_DOUBLING_KEY, this.eap$smartDoubling);
        // persist any pattern-level scaling limits
        try {
            var list = ((PatternProviderLogicAccessor) this).eap$patterns();
            int[] limits = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                var details = list.get(i);
                if (details instanceof com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern sp) {
                    limits[i] = sp.getPerProviderScalingLimit();
                } else if (details instanceof appeng.crafting.pattern.AEProcessingPattern base && base instanceof ISmartDoublingAwarePattern aware) {
                    limits[i] = aware.eap$getScalingLimit();
                } else {
                    limits[i] = 0;
                }
            }
            var listTag = new net.minecraft.nbt.ListTag();
            for (int v : limits) {
                var c = new CompoundTag();
                c.putInt("limit", v);
                listTag.add(c);
            }
            tag.put("eap_scaling_limits", listTag);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$readSmartDoublingFromNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(EAP_SMART_DOUBLING_KEY)) {
            this.eap$smartDoubling = tag.getBoolean(EAP_SMART_DOUBLING_KEY);
        }
        try {
            if (tag.contains("eap_scaling_limits")) {
                var list = ((PatternProviderLogicAccessor) this).eap$patterns();
                var limitsTag = tag.getList("eap_scaling_limits", net.minecraft.nbt.Tag.TAG_COMPOUND);
                int n = Math.min(list.size(), limitsTag.size());
                for (int i = 0; i < n; i++) {
                    var c = limitsTag.getCompound(i);
                    int lim = c.getInt("limit");
                    var details = list.get(i);
                    if (details instanceof com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern sp) {
                        sp.setPerProviderScalingLimit(lim);
                    } else if (details instanceof appeng.crafting.pattern.AEProcessingPattern base && base instanceof ISmartDoublingAwarePattern aware) {
                        aware.eap$setScalingLimit(lim);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "updatePatterns", at = @At("TAIL"))
    private void eap$applySmartDoublingToPatterns(CallbackInfo ci) {
        try {
            var list = ((PatternProviderLogicAccessor) this).eap$patterns();
            boolean allow = this.eap$smartDoubling;
            for (IPatternDetails details : list) {
                if (details instanceof AEProcessingPattern proc && proc instanceof ISmartDoublingAwarePattern aware) {
                    aware.eap$setAllowScaling(allow);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // called by UI when user changes per-provider limit; apply with debounce and save
    @Unique
    public void eap$onPerProviderLimitChanged(int patternIndex, int newLimit) {
        try {
            var list = ((PatternProviderLogicAccessor) this).eap$patterns();
            if (patternIndex < 0 || patternIndex >= list.size()) return;
            var details = list.get(patternIndex);
            if (details instanceof com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern sp) {
                sp.setPerProviderScalingLimit(newLimit);
            } else if (details instanceof appeng.crafting.pattern.AEProcessingPattern base && base instanceof ISmartDoublingAwarePattern aware) {
                aware.eap$setScalingLimit(newLimit);
            }

            synchronized (EAP_SCALING_LOCK) {
                if (eap$pendingScalingSave != null) {
                    eap$pendingScalingSave.cancel(false);
                }
                eap$pendingScalingSave = EAP_EXECUTOR.schedule(() -> {
                    try {
                        this.saveChanges();
                    } catch (Throwable ignored) {}
                }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (Throwable ignored) {}
    }

    @Shadow
    public void saveChanges() {}

    @Inject(method = "exportSettings(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void onExportSettings(CompoundTag output, CallbackInfo ci) {
        output.putBoolean(EAP_SMART_DOUBLING_KEY, this.eap$smartDoubling);
    }

    @Inject(method = "importSettings(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
    private void onImportSettings(CompoundTag input, Player player, CallbackInfo ci) {
        if (input.contains(EAP_SMART_DOUBLING_KEY)) {
            this.eap$smartDoubling = input.getBoolean(EAP_SMART_DOUBLING_KEY);
            // 持久化到 world
            this.saveChanges();
        }
    }
}
