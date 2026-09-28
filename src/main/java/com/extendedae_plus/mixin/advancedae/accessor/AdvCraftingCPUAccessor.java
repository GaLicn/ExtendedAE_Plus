package com.extendedae_plus.mixin.advancedae.accessor;

import appeng.api.stacks.GenericStack;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AdvCraftingCPU.class, remap = false)
public interface AdvCraftingCPUAccessor {
    @Invoker("updateOutput")
    void eap$invokeUpdateOutput(GenericStack stack);
}
