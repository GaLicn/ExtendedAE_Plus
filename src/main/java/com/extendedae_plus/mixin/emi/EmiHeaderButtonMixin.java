package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.compat.emi.EmiNetworkOverlayButton;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在 EMI 左下角徽标、配方树按钮之后添加网络库存开关。 */
@Pseudo
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiHeaderButtonMixin {
    @Unique private static final int EAP_BUTTON_SIZE = EmiNetworkOverlayButton.BUTTON_SIZE;
    @Unique private static final int EAP_BUTTON_X = 46;
    @Unique private static final int EAP_BUTTON_Y_OFFSET = 22;

    @Shadow public static int lastMouseX;
    @Shadow public static int lastMouseY;

    /** 上一帧是否绘制了按钮；未绘制时不应响应点击。 */
    @Unique private static boolean eap$buttonVisible;

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private static void eap$drawHeaderButton(EmiDrawContext context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        // RETURN 在 emi 内部的每个提前返回处都会触发，必须自行确认「有可用界面且 EMI 未禁用」。
        eap$buttonVisible = false;
        // EMI 1.20.1 通过 null 表示没有可用界面。
        if (EmiScreenManager.isDisabled() || EmiScreenBase.getCurrent() == null) {
            return;
        }
        GuiGraphics guiGraphics = context.raw();
        if (guiGraphics == null) {
            return;
        }
        eap$buttonVisible = true;
        int y = buttonY();
        var pose = guiGraphics.pose();
        pose.pushPose();
        // 与 EMI 自身的按钮同层（z=100），保证在侧边栏之上、tooltip 之下。
        pose.translate(EAP_BUTTON_X, y, 100);
        EmiNetworkOverlayButton.draw(guiGraphics, 0, 0);
        pose.popPose();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), remap = false, cancellable = true)
    private static void eap$onHeaderButtonClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!eap$buttonVisible || button != 0 || EmiScreenManager.isDisabled()) {
            return;
        }
        if (mouseX >= EAP_BUTTON_X && mouseX < EAP_BUTTON_X + EAP_BUTTON_SIZE
                && mouseY >= buttonY() && mouseY < buttonY() + EAP_BUTTON_SIZE) {
            EmiNetworkOverlayButton.toggle();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "drawForeground", at = @At("RETURN"), remap = false)
    private static void eap$drawHeaderButtonTooltip(EmiDrawContext context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        if (!eap$buttonVisible) {
            return;
        }
        GuiGraphics guiGraphics = context.raw();
        if (guiGraphics == null) {
            return;
        }
        int x = EAP_BUTTON_X;
        int y = buttonY();
        if (lastMouseX >= x && lastMouseX < x + EAP_BUTTON_SIZE
                && lastMouseY >= y && lastMouseY < y + EAP_BUTTON_SIZE) {
            EmiNetworkOverlayButton.drawTooltip(guiGraphics, lastMouseX, lastMouseY);
        }
    }

    @Unique
    private static int buttonY() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight() - EAP_BUTTON_Y_OFFSET;
    }
}
