package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.InterfaceScreen;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.NewIcon;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;
import com.extendedae_plus.network.InterfaceAdjustConfigAmountC2SPacket;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 AE2 的 ME 接口界面注入倍增/除法按钮（x2/÷2、x5/÷5、x10/÷10）。
 * 点击时通过 NeoForge 自定义负载发送到服务端调整配置数量。
 */
@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class InterfaceScreenMixin<T extends AEBaseMenu> {

    @Unique private ActionEPPButton eap$x2Button;
    @Unique private ActionEPPButton eap$divideBy2Button;
    @Unique private ActionEPPButton eap$x5Button;
    @Unique private ActionEPPButton eap$divideBy5Button;
    @Unique private ActionEPPButton eap$x10Button;
    @Unique private ActionEPPButton eap$divideBy10Button;

    @Unique private int eap$lastLeftPos = -1;
    @Unique private int eap$lastTopPos = -1;
    @Unique private int eap$lastImageWidth = -1;
    @Unique private int eap$lastImageHeight = -1;
    // 最近一次在 CONFIG 区域悬停的配置槽索引
    @Unique private int eap$lastConfigIndex = -1;

    @Inject(method = "init", at = @At("TAIL"))
    private void eap$addScaleButtons(CallbackInfo ci) {
        if (!eap$isSupportedInterfaceScreen()) {
            return;
        }
        try { LogUtils.getLogger().info("[EAP][InterfaceMixin] init tail reached, preparing scale buttons."); } catch (Throwable ignored) {}
        if (eap$x2Button == null) {
            eap$x2Button = new ActionEPPButton((b) -> eap$sendAdjustForAllConfigs(false, 2), NewIcon.MULTIPLY2);
            eap$x2Button.setTooltip(null);
            eap$x2Button.setVisibility(true);
        }
        if (eap$divideBy2Button == null) {
            eap$divideBy2Button = new ActionEPPButton((b) -> eap$sendAdjustForAllConfigs(true, 2), NewIcon.DIVIDE2);
            eap$divideBy2Button.setTooltip(null);
            eap$divideBy2Button.setVisibility(true);
        }
        if (eap$x5Button == null) {
            eap$x5Button = new ActionEPPButton((b) -> eap$sendAdjustForAllConfigs(false, 5), NewIcon.MULTIPLY5);
            eap$x5Button.setTooltip(null);
            eap$x5Button.setVisibility(true);
        }
        if (eap$divideBy5Button == null) {
            eap$divideBy5Button = new ActionEPPButton((b) -> eap$sendAdjustForAllConfigs(true, 5), NewIcon.DIVIDE5);
            eap$divideBy5Button.setTooltip(null);
            eap$divideBy5Button.setVisibility(true);
        }
        if (eap$x10Button == null) {
            eap$x10Button = new ActionEPPButton((b) -> eap$sendAdjustForAllConfigs(false, 10), NewIcon.MULTIPLY10);
            eap$x10Button.setTooltip(null);
            eap$x10Button.setVisibility(true);
        }
        if (eap$divideBy10Button == null) {
            eap$divideBy10Button = new ActionEPPButton((b) -> eap$sendAdjustForAllConfigs(true, 10), NewIcon.DIVIDE10);
            eap$divideBy10Button.setTooltip(null);
            eap$divideBy10Button.setVisibility(true);
        }

        // 注册到渲染与交互列表
        var accessor = (ScreenAccessor) (Object) this;
        if (!accessor.eap$getRenderables().contains(eap$divideBy2Button)) accessor.eap$getRenderables().add(eap$divideBy2Button);
        if (!accessor.eap$getChildren().contains(eap$divideBy2Button)) accessor.eap$getChildren().add(eap$divideBy2Button);
        if (!accessor.eap$getRenderables().contains(eap$x2Button)) accessor.eap$getRenderables().add(eap$x2Button);
        if (!accessor.eap$getChildren().contains(eap$x2Button)) accessor.eap$getChildren().add(eap$x2Button);
        if (!accessor.eap$getRenderables().contains(eap$divideBy5Button)) accessor.eap$getRenderables().add(eap$divideBy5Button);
        if (!accessor.eap$getChildren().contains(eap$divideBy5Button)) accessor.eap$getChildren().add(eap$divideBy5Button);
        if (!accessor.eap$getRenderables().contains(eap$x5Button)) accessor.eap$getRenderables().add(eap$x5Button);
        if (!accessor.eap$getChildren().contains(eap$x5Button)) accessor.eap$getChildren().add(eap$x5Button);
        if (!accessor.eap$getRenderables().contains(eap$divideBy10Button)) accessor.eap$getRenderables().add(eap$divideBy10Button);
        if (!accessor.eap$getChildren().contains(eap$divideBy10Button)) accessor.eap$getChildren().add(eap$divideBy10Button);
        if (!accessor.eap$getRenderables().contains(eap$x10Button)) accessor.eap$getRenderables().add(eap$x10Button);
        if (!accessor.eap$getChildren().contains(eap$x10Button)) accessor.eap$getChildren().add(eap$x10Button);

        eap$relayoutButtons();
        try { LogUtils.getLogger().info("[EAP][InterfaceMixin] buttons added and laid out."); } catch (Throwable ignored) {}
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void eap$ensureButtons(CallbackInfo ci) {
        if (!eap$isSupportedInterfaceScreen()) {
            return;
        }
        var accessor = (ScreenAccessor) (Object) this;
        if (eap$divideBy2Button != null && !accessor.eap$getRenderables().contains(eap$divideBy2Button)) {
            accessor.eap$getRenderables().add(eap$divideBy2Button);
            accessor.eap$getChildren().add(eap$divideBy2Button);
        }
        if (eap$x2Button != null && !accessor.eap$getRenderables().contains(eap$x2Button)) {
            accessor.eap$getRenderables().add(eap$x2Button);
            accessor.eap$getChildren().add(eap$x2Button);
        }
        if (eap$divideBy5Button != null && !accessor.eap$getRenderables().contains(eap$divideBy5Button)) {
            accessor.eap$getRenderables().add(eap$divideBy5Button);
            accessor.eap$getChildren().add(eap$divideBy5Button);
        }
        if (eap$x5Button != null && !accessor.eap$getRenderables().contains(eap$x5Button)) {
            accessor.eap$getRenderables().add(eap$x5Button);
            accessor.eap$getChildren().add(eap$x5Button);
        }
        if (eap$divideBy10Button != null && !accessor.eap$getRenderables().contains(eap$divideBy10Button)) {
            accessor.eap$getRenderables().add(eap$divideBy10Button);
            accessor.eap$getChildren().add(eap$divideBy10Button);
        }
        if (eap$x10Button != null && !accessor.eap$getRenderables().contains(eap$x10Button)) {
            accessor.eap$getRenderables().add(eap$x10Button);
            accessor.eap$getChildren().add(eap$x10Button);
        }

        int curLeft = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
        int curTop = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
        int curImgW = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
        int curImgH = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageHeight();
        if (curLeft != eap$lastLeftPos || curTop != eap$lastTopPos || curImgW != eap$lastImageWidth || curImgH != eap$lastImageHeight) {
            eap$lastLeftPos = curLeft;
            eap$lastTopPos = curTop;
            eap$lastImageWidth = curImgW;
            eap$lastImageHeight = curImgH;
            eap$relayoutButtons();
        }
        eap$updateLastConfigFromHover();
    }

    @Unique
    private void eap$sendAdjustForHoveredConfig(boolean divide, int factor) {
        try {
            if (!eap$isSupportedInterfaceScreen()) {
                return;
            }
            Slot hovered = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getHoveredSlot();
            var screen = (AEBaseScreen<?>) (Object) this;
            var menu = screen.getMenu();
            if (!(menu instanceof appeng.menu.implementations.InterfaceMenu interfaceMenu)) {
                return;
            }
            var configSlots = interfaceMenu.getSlots(SlotSemantics.CONFIG);
            if (configSlots == null || configSlots.isEmpty()) {
                return;
            }

            Integer slotFieldObj = null;
            if (hovered != null) {
                for (var s : configSlots) {
                    if (s == hovered) {
                        try {
                            var f = s.getClass().getDeclaredField("slot");
                            f.setAccessible(true);
                            Object v = f.get(s);
                            if (v instanceof Integer i) {
                                slotFieldObj = i;
                            }
                        } catch (Throwable ignored) {}
                        if (slotFieldObj == null) {
                            slotFieldObj = configSlots.indexOf(s);
                        }
                        break;
                    }
                }
            }
            int slotField = -1;
            if (slotFieldObj != null) {
                slotField = slotFieldObj;
            } else if (eap$lastConfigIndex >= 0 && eap$lastConfigIndex < configSlots.size()) {
                slotField = eap$lastConfigIndex;
            }
            if (slotField < 0) {
                return;
            }

            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new InterfaceAdjustConfigAmountC2SPacket(slotField, divide, factor));
        } catch (Throwable ignored) {}
    }

    @Unique
    private void eap$sendAdjustForAllConfigs(boolean divide, int factor) {
        try {
            if (!eap$isSupportedInterfaceScreen()) {
                return;
            }
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new InterfaceAdjustConfigAmountC2SPacket(-1, divide, factor));
        } catch (Throwable ignored) {}
    }

    @Unique
    private boolean eap$isSupportedInterfaceScreen() {
        if (((Object) this) instanceof InterfaceScreen) {
            return true;
        }
        try {
            String cn = ((Object) this).getClass().getName();
            if ("com.glodblock.github.extendedae.client.gui.GuiExInterface".equals(cn)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Unique
    private void eap$relayoutButtons() {
        try {
            int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
            int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
            int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
            int bx = leftPos + imageWidth + 1;
            int by = topPos + 70;
            int spacing = 22;
            if (eap$divideBy2Button != null) { eap$divideBy2Button.setX(bx); eap$divideBy2Button.setY(by); }
            if (eap$x2Button != null) { eap$x2Button.setX(bx); eap$x2Button.setY(by + spacing); }
            if (eap$divideBy5Button != null) { eap$divideBy5Button.setX(bx); eap$divideBy5Button.setY(by + spacing * 2); }
            if (eap$x5Button != null) { eap$x5Button.setX(bx); eap$x5Button.setY(by + spacing * 3); }
            if (eap$divideBy10Button != null) { eap$divideBy10Button.setX(bx); eap$divideBy10Button.setY(by + spacing * 4); }
            if (eap$x10Button != null) { eap$x10Button.setX(bx); eap$x10Button.setY(by + spacing * 5); }
        } catch (Throwable ignored) {}
    }

    @Unique
    private void eap$updateLastConfigFromHover() {
        try {
            if (!(((Object) this) instanceof InterfaceScreen)) {
                return;
            }
            Slot hovered = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getHoveredSlot();
            if (hovered == null) {
                return;
            }
            var screen = (AEBaseScreen<?>) (Object) this;
            var menu = screen.getMenu();
            if (!(menu instanceof appeng.menu.implementations.InterfaceMenu interfaceMenu)) {
                return;
            }
            var configSlots = interfaceMenu.getSlots(SlotSemantics.CONFIG);
            if (configSlots == null || configSlots.isEmpty()) {
                return;
            }
            Integer idx = null;
            for (var s : configSlots) {
                if (s == hovered) {
                    try {
                        var f = s.getClass().getDeclaredField("slot");
                        f.setAccessible(true);
                        Object v = f.get(s);
                        if (v instanceof Integer i) {
                            idx = i;
                        }
                    } catch (Throwable ignored) {}
                    if (idx == null) {
                        idx = configSlots.indexOf(s);
                    }
                    break;
                }
            }
            if (idx != null && idx >= 0) {
                if (eap$lastConfigIndex != idx) {
                    eap$lastConfigIndex = idx;
                }
            }
        } catch (Throwable ignored) {}
    }
}
