package com.extendedae_plus.mixin.emi;

import appeng.api.stacks.GenericStack;
import com.extendedae_plus.compat.emi.EmiHelper;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在 EMI 点击处理入口优先接管 Shift+左键拉取。 */
@Pseudo
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMouseClickMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), remap = false, cancellable = true)
    private static void eap$pullFromEmiOnShiftClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !Screen.hasShiftDown()) {
            return;
        }

        GenericStack stack = EmiHelper.getSidebarGenericStackUnderMouse(mouseX, mouseY);
        if (stack == null) {
            return;
        }

        ModNetwork.CHANNEL.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
        cir.setReturnValue(true);
    }
}
