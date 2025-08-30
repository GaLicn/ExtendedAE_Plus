package com.extendedae_plus.mixin.autopattern;

import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.CraftingSimulationState;
import com.extendedae_plus.util.RequestedAmountHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = CraftingTreeNode.class,remap = false)
public class CraftingTreeNodeMixin {
    @Inject(method = "request(Lappeng/crafting/inv/CraftingSimulationState;JLappeng/api/stacks/KeyCounter;)V",
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/CraftingTreeNode;addContainerItems(Lappeng/api/stacks/AEKey;JLappeng/api/stacks/KeyCounter;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void captureRequestedAmount(CraftingSimulationState inv, long requestedAmount, KeyCounter containerItems, CallbackInfo ci) {
        // push the requestedAmount before addContainerItems is called
       RequestedAmountHolder.push(requestedAmount);
    }

    @Inject(method = "request(Lappeng/crafting/inv/CraftingSimulationState;JLappeng/api/stacks/KeyCounter;)V",
            at = @At(value = "RETURN"))
    private void clearRequestedAmountOnReturn(CraftingSimulationState inv, long requestedAmount, KeyCounter containerItems, CallbackInfo ci) {
        // pop the pushed requested amount on return
        RequestedAmountHolder.pop();
    }
}
