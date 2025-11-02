package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.InterfaceScreen;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.mixin.minecraft.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.ScreenAccessor;
import com.extendedae_plus.network.meInterface.InterfaceAdjustConfigAmountC2SPacket;
import com.extendedae_plus.util.ScaleButtonHelper;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.gui.GuiExInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 AE2 的 ME 接口界面注入倍增/除法按钮（x2/÷2、x5/÷5、x10/÷10）。
 * 点击逻辑直接对所有 CONFIG 槽生效，删除了 hover 回退索引。
 */
@Mixin(AEBaseScreen.class)
public abstract class InterfaceScreenMixin<T extends AEBaseMenu> {

    @Unique private ScaleButtonHelper.ScaleButtonSet eap$scaleButtons;

    @Unique private int eap$lastLeftPos = -1;
    @Unique private int eap$lastTopPos = -1;
    @Unique private int eap$lastImageWidth = -1;
    @Unique private int eap$lastImageHeight = -1;

    @Inject(method = "init", at = @At("TAIL"))
    private void eap$addScaleButtons(CallbackInfo ci) {
        // 仅在 AE2 接口界面或 ExtendedAE 扩展接口界面中添加
        if (!eap$isSupportedInterfaceScreen()) return;

        if (eap$scaleButtons == null) {
            // 使用工具类创建按钮，统一回调
            eap$scaleButtons = ScaleButtonHelper.createButtons((divide, factor) -> {
                // 对所有 CONFIG 槽生效
                ModNetwork.CHANNEL.sendToServer(new InterfaceAdjustConfigAmountC2SPacket(-1, divide, factor));
            });
        }

        // 注册到 renderables 和 children
        var accessor = (ScreenAccessor) this;
        for (ActionEPPButton b : ScaleButtonHelper.all(eap$scaleButtons)) {
            if (!accessor.eap$getRenderables().contains(b)) accessor.eap$getRenderables().add(b);
            if (!accessor.eap$getChildren().contains(b)) accessor.eap$getChildren().add(b);
            b.setVisibility(true);
        }

        // 初次布局
        eap$relayoutButtons();
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void eap$ensureButtons(CallbackInfo ci) {
        if (!eap$isSupportedInterfaceScreen()) return;

        var accessor = (ScreenAccessor) this;
        // 确保按钮在 renderables/children 中
        for (ActionEPPButton b : ScaleButtonHelper.all(eap$scaleButtons)) {
            if (b != null) {
                if (!accessor.eap$getRenderables().contains(b)) accessor.eap$getRenderables().add(b);
                if (!accessor.eap$getChildren().contains(b)) accessor.eap$getChildren().add(b);
            }
        }

        // 检查屏幕尺寸变化
        AbstractContainerScreenAccessor<?> screen = (AbstractContainerScreenAccessor<?>) this;
        int curLeft = screen.eap$getLeftPos();
        int curTop = screen.eap$getTopPos();
        int curImgW = screen.eap$getImageWidth();
        int curImgH = screen.eap$getImageHeight();
        if (curLeft != eap$lastLeftPos ||
                curTop != eap$lastTopPos ||
                curImgW != eap$lastImageWidth ||
                curImgH != eap$lastImageHeight) {
            eap$lastLeftPos = curLeft;
            eap$lastTopPos = curTop;
            eap$lastImageWidth = curImgW;
            eap$lastImageHeight = curImgH;
            eap$relayoutButtons();
        }
    }

    @Unique
    private boolean eap$isSupportedInterfaceScreen() {
        if (((Object) this) instanceof InterfaceScreen) return true;
        if (((Object) this) instanceof GuiExInterface) return true;
        return false;
    }

    @Unique
    private void eap$relayoutButtons() {
        if (eap$scaleButtons == null) return;
        AbstractContainerScreenAccessor<?> screen = (AbstractContainerScreenAccessor<?>) this;
        int leftPos = screen.eap$getLeftPos();
        int topPos = screen.eap$getTopPos();

        // 左侧竖排布局（和原来一致）
        ScaleButtonHelper.layoutButtons(
                eap$scaleButtons,
                leftPos - eap$scaleButtons.divide2().getWidth(), // 左侧外缘
                topPos + eap$scaleButtons.divide2().getWidth() + 30,    // 上偏移
                22,                                                     // 间距
                ScaleButtonHelper.Side.LEFT                             // 左侧布局
        );
    }
}
