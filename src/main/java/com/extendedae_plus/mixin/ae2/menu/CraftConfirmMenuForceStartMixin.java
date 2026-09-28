package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.extendedae_plus.api.crafting.IForceCraftStartSync;
import com.extendedae_plus.crafting.ForcedCraftingPlan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuForceStartMixin implements IForceCraftStartSync {
    @Shadow
    private ICraftingPlan result;

    @Unique
    private boolean eap$pendingForceCraftStart;

    @Unique
    private ICraftingPlan eap$originalSimulationResult;

    @Override
    public void eap$clientSetForceCraftStart(boolean forceStart) {
        this.eap$pendingForceCraftStart = forceStart;
    }

    @Override
    public boolean eap$consumeForceCraftStartFlag() {
        boolean flag = this.eap$pendingForceCraftStart;
        this.eap$pendingForceCraftStart = false;
        return flag;
    }

    @Inject(method = "startJob", at = @At("HEAD"))
    private void eap$wrapSimulationPlanForForceStart(CallbackInfo ci) {
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        if (self.isClientSide()) {
            return;
        }
        if (!this.eap$consumeForceCraftStartFlag()) {
            return;
        }
        if (this.result == null || !this.result.simulation()) {
            return;
        }

        this.eap$originalSimulationResult = this.result;
        this.result = new ForcedCraftingPlan(this.result);
    }

    @Inject(method = "startJob", at = @At("RETURN"))
    private void eap$restoreOriginalPlanAfterSubmit(CallbackInfo ci) {
        if (this.eap$originalSimulationResult == null) {
            return;
        }
        this.result = this.eap$originalSimulationResult;
        this.eap$originalSimulationResult = null;
    }
}
