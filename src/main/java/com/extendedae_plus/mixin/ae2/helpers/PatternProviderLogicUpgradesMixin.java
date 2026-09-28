package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.api.bridge.PatternProviderLogicAppfluxBridge;
import com.extendedae_plus.api.bridge.PatternProviderLogicUpgradeCompatBridge;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.mixin.appflux.accessor.PatternProviderLogicAppfluxAccessor;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 当 appflux 存在时，把它的样板供应器升级槽扩展到我们需要的槽位数。
 */
@Mixin(value = PatternProviderLogic.class, priority = 500, remap = false)
public abstract class PatternProviderLogicUpgradesMixin implements PatternProviderLogicAppfluxBridge {

    @Final
    @Shadow
    private PatternProviderLogicHost host;
    
    @Final
    @Shadow
    private IManagedGridNode mainNode;
    
    @Unique
    private IUpgradeInventory eap$upgrades = UpgradeInventories.empty();

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldListenToAppfluxUpgrades()) {
                this.eap$ensureAppliedFluxUpgradeSlots();
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] 初始化失败", t);
        }
    }

    @Dynamic("AppFlux mixin adds IUpgradeableObject#getUpgrades to PatternProviderLogic")
    @Inject(method = "getUpgrades", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void eap$wrapAppfluxGetUpgrades(CallbackInfoReturnable<IUpgradeInventory> cir) {
        if (!UpgradeSlotCompat.shouldListenToAppfluxUpgrades()) {
            return;
        }

        IUpgradeInventory ensured = this.eap$ensureAppliedFluxUpgradeSlots(cir.getReturnValue());
        if (ensured != null && ensured != cir.getReturnValue()) {
            cir.setReturnValue(ensured);
        }
    }
    
    @Override
    public IUpgradeInventory eap$ensureAppfluxUpgradeSlots() {
        return this.eap$ensureAppliedFluxUpgradeSlots();
    }

    @Unique
    private IUpgradeInventory eap$ensureAppliedFluxUpgradeSlots() {
        return this.eap$ensureAppliedFluxUpgradeSlots(null);
    }

    @Unique
    private IUpgradeInventory eap$ensureAppliedFluxUpgradeSlots(IUpgradeInventory currentUpgrades) {
        try {
            PatternProviderLogicAppfluxAccessor accessor = (PatternProviderLogicAppfluxAccessor) (Object) this;
            IUpgradeInventory existingUpgrades = currentUpgrades != null ? currentUpgrades : accessor.eap$getAppfluxUpgrades();
            int targetSlots = UpgradeSlotCompat.getPatternProviderAppfluxUpgradeSlots(this.host);

            if (existingUpgrades != null && existingUpgrades.size() >= targetSlots) {
                this.eap$upgrades = existingUpgrades;
                return existingUpgrades;
            }

            this.eap$upgrades = UpgradeInventories.forMachine(
                    host.getTerminalIcon().getItem(),
                    targetSlots,
                    this::eap$onUpgradesChanged
            );

            if (existingUpgrades != null) {
                for (int i = 0; i < Math.min(existingUpgrades.size(), this.eap$upgrades.size()); i++) {
                    ItemStack stack = existingUpgrades.getStackInSlot(i).copy();
                    if (!stack.isEmpty()) {
                        this.eap$upgrades.insertItem(i, stack, false);
                    }
                }
            }

            accessor.eap$setAppfluxUpgrades(this.eap$upgrades);
            return this.eap$upgrades;
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] 扩展 AppliedFlux 升级槽失败", t);
            return currentUpgrades;
        }
    }
    
    @Unique
    private void eap$onUpgradesChanged() {
        try {
            this.host.saveChanges();
            try {
                ((PatternProviderLogicAppfluxAccessor) (Object) this).eap$invokeAppfluxUpgradesChanged();
            } catch (Throwable ignored) {
            }

            if ((Object) this instanceof PatternProviderLogicUpgradeCompatBridge bridge) {
                bridge.eap$onCompatUpgradesChangedHook();
            }
        } catch (Throwable t) {
            ExtendedAELogger.LOGGER.error("[样板供应器][升级槽] onUpgradesChanged 处理失败", t);
        }
    }
}
