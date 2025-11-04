package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import com.extendedae_plus.api.smartDoubling.ICraftingSimulationStateExt;
import com.extendedae_plus.api.smartDoubling.ICraftingTreeProcessExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings({"AddedMixinMembersNamePattern"})
@Mixin(value = CraftingTreeProcess.class, remap = false)
public class CraftingTreeProcessMixin implements ICraftingTreeProcessExt {
    @Unique private ICraftingService craftingService;

    @Inject(method = "<init>",at = @At("RETURN"))
    private void init(ICraftingService cc, CraftingCalculation job, IPatternDetails details, CraftingTreeNode craftingTreeNode, CallbackInfo ci) {
        this.craftingService = cc;
    }

    @Inject(
        method = "request",
        at = @At("HEAD")
    )
    private void bindSimulationState(CraftingSimulationState inv, long times, CallbackInfo ci) {
        ((ICraftingSimulationStateExt) inv).setSourceProcess((CraftingTreeProcess) (Object) this);
    }

    @Override
    public ICraftingService getCraftingService() {
        return this.craftingService;
    }
}
