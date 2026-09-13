package com.extendedae_plus_gtladd.mixin.ae;

import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import com.extendedae_plus_gtladd.init.ModNetwork;
import com.extendedae_plus_gtladd.network.CraftingMonitorOpenGTMProviderC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AEBaseScreen.class})
public abstract class AEBaseScreenMixin {
    @Inject(
        method = {"mouseClicked"},
        at = {@At("HEAD")}
    )
    private void eap$craftingCpuShiftClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;

        if (self instanceof CraftingCPUScreen<?> screen) {
            if (Screen.hasShiftDown() && button == 1) {
                try {
                    StackWithBounds hovered = screen.getStackUnderMouse(mouseX, mouseY);
                    if (hovered == null || hovered.stack() == null) {
                        return;
                    }

                    AEKey key = hovered.stack().what();
                    if (key == null) {
                        return;
                    }

                    ModNetwork.CHANNEL.sendToServer(new CraftingMonitorOpenGTMProviderC2SPacket(key));
                } catch (Throwable ignored) {
                }

            }
        }
    }
}