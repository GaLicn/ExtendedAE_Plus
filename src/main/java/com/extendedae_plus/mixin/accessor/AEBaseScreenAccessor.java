package com.extendedae_plus.mixin.accessor;

import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import appeng.client.gui.AEBaseScreen;

@Mixin(AEBaseScreen.class)
public interface AEBaseScreenAccessor {
    @Invoker(value = "addToLeftToolbar", remap = false)
    <B extends Button> B epp$addToLeftToolbar(B button);
}
