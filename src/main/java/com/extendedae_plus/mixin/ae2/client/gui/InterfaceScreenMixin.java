package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.InterfaceScreen;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.client.gui.NewIcon;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;
import com.extendedae_plus.network.InterfaceAdjustConfigAmountC2SPacket;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
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
        if (!this.eap$isSupportedInterfaceScreen()) {
            return;
        }
        if (this.eap$x2Button == null) {
            this.eap$x2Button = new ActionEPPButton((b) -> this.eap$sendAdjustForAllConfigs(false, 2), NewIcon.MULTIPLY2);
            this.eap$x2Button.setTooltip(null);
            this.eap$x2Button.setVisibility(true);
        }
        if (this.eap$divideBy2Button == null) {
            this.eap$divideBy2Button = new ActionEPPButton((b) -> this.eap$sendAdjustForAllConfigs(true, 2), NewIcon.DIVIDE2);
            this.eap$divideBy2Button.setTooltip(null);
            this.eap$divideBy2Button.setVisibility(true);
        }
        if (this.eap$x5Button == null) {
            this.eap$x5Button = new ActionEPPButton((b) -> this.eap$sendAdjustForAllConfigs(false, 5), NewIcon.MULTIPLY5);
            this.eap$x5Button.setTooltip(null);
            this.eap$x5Button.setVisibility(true);
        }
        if (this.eap$divideBy5Button == null) {
            this.eap$divideBy5Button = new ActionEPPButton((b) -> this.eap$sendAdjustForAllConfigs(true, 5), NewIcon.DIVIDE5);
            this.eap$divideBy5Button.setTooltip(null);
            this.eap$divideBy5Button.setVisibility(true);
        }
        if (this.eap$x10Button == null) {
            this.eap$x10Button = new ActionEPPButton((b) -> this.eap$sendAdjustForAllConfigs(false, 10), NewIcon.MULTIPLY10);
            this.eap$x10Button.setTooltip(null);
            this.eap$x10Button.setVisibility(true);
        }
        if (this.eap$divideBy10Button == null) {
            this.eap$divideBy10Button = new ActionEPPButton((b) -> this.eap$sendAdjustForAllConfigs(true, 10), NewIcon.DIVIDE10);
            this.eap$divideBy10Button.setTooltip(null);
            this.eap$divideBy10Button.setVisibility(true);
        }

        // 注册到渲染与交互列表
        var accessor = (ScreenAccessor) (Object) this;
        if (!accessor.eap$getRenderables().contains(this.eap$divideBy2Button))
            accessor.eap$getRenderables().add(this.eap$divideBy2Button);
        if (!accessor.eap$getChildren().contains(this.eap$divideBy2Button))
            accessor.eap$getChildren().add(this.eap$divideBy2Button);
        if (!accessor.eap$getRenderables().contains(this.eap$x2Button))
            accessor.eap$getRenderables().add(this.eap$x2Button);
        if (!accessor.eap$getChildren().contains(this.eap$x2Button)) accessor.eap$getChildren().add(this.eap$x2Button);
        if (!accessor.eap$getRenderables().contains(this.eap$divideBy5Button))
            accessor.eap$getRenderables().add(this.eap$divideBy5Button);
        if (!accessor.eap$getChildren().contains(this.eap$divideBy5Button))
            accessor.eap$getChildren().add(this.eap$divideBy5Button);
        if (!accessor.eap$getRenderables().contains(this.eap$x5Button))
            accessor.eap$getRenderables().add(this.eap$x5Button);
        if (!accessor.eap$getChildren().contains(this.eap$x5Button)) accessor.eap$getChildren().add(this.eap$x5Button);
        if (!accessor.eap$getRenderables().contains(this.eap$divideBy10Button))
            accessor.eap$getRenderables().add(this.eap$divideBy10Button);
        if (!accessor.eap$getChildren().contains(this.eap$divideBy10Button))
            accessor.eap$getChildren().add(this.eap$divideBy10Button);
        if (!accessor.eap$getRenderables().contains(this.eap$x10Button))
            accessor.eap$getRenderables().add(this.eap$x10Button);
        if (!accessor.eap$getChildren().contains(this.eap$x10Button))
            accessor.eap$getChildren().add(this.eap$x10Button);

        this.eap$relayoutButtons();
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void eap$ensureButtons(CallbackInfo ci) {
        if (!this.eap$isSupportedInterfaceScreen()) {
            return;
        }
        var accessor = (ScreenAccessor) (Object) this;
        if (this.eap$divideBy2Button != null && !accessor.eap$getRenderables().contains(this.eap$divideBy2Button)) {
            accessor.eap$getRenderables().add(this.eap$divideBy2Button);
            accessor.eap$getChildren().add(this.eap$divideBy2Button);
        }
        if (this.eap$x2Button != null && !accessor.eap$getRenderables().contains(this.eap$x2Button)) {
            accessor.eap$getRenderables().add(this.eap$x2Button);
            accessor.eap$getChildren().add(this.eap$x2Button);
        }
        if (this.eap$divideBy5Button != null && !accessor.eap$getRenderables().contains(this.eap$divideBy5Button)) {
            accessor.eap$getRenderables().add(this.eap$divideBy5Button);
            accessor.eap$getChildren().add(this.eap$divideBy5Button);
        }
        if (this.eap$x5Button != null && !accessor.eap$getRenderables().contains(this.eap$x5Button)) {
            accessor.eap$getRenderables().add(this.eap$x5Button);
            accessor.eap$getChildren().add(this.eap$x5Button);
        }
        if (this.eap$divideBy10Button != null && !accessor.eap$getRenderables().contains(this.eap$divideBy10Button)) {
            accessor.eap$getRenderables().add(this.eap$divideBy10Button);
            accessor.eap$getChildren().add(this.eap$divideBy10Button);
        }
        if (this.eap$x10Button != null && !accessor.eap$getRenderables().contains(this.eap$x10Button)) {
            accessor.eap$getRenderables().add(this.eap$x10Button);
            accessor.eap$getChildren().add(this.eap$x10Button);
        }

        int curLeft = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
        int curTop = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
        int curImgW = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
        int curImgH = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageHeight();
        if (curLeft != this.eap$lastLeftPos || curTop != this.eap$lastTopPos || curImgW != this.eap$lastImageWidth || curImgH != this.eap$lastImageHeight) {
            this.eap$lastLeftPos = curLeft;
            this.eap$lastTopPos = curTop;
            this.eap$lastImageWidth = curImgW;
            this.eap$lastImageHeight = curImgH;
            this.eap$relayoutButtons();
        }
        this.eap$updateLastConfigFromHover();
    }

    @Unique
    private void eap$sendAdjustForHoveredConfig(boolean divide, int factor) {
        try {
            if (!this.eap$isSupportedInterfaceScreen()) {
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
            } else if (this.eap$lastConfigIndex >= 0 && this.eap$lastConfigIndex < configSlots.size()) {
                slotField = this.eap$lastConfigIndex;
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
            if (!this.eap$isSupportedInterfaceScreen()) {
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
            int bx = leftPos - this.eap$divideBy2Button.getWidth() - 1;
            int by = topPos + 30;
            int spacing = 22;
            if (this.eap$divideBy2Button != null) {
                this.eap$divideBy2Button.setX(bx);
                this.eap$divideBy2Button.setY(by);
            }
            if (this.eap$x2Button != null) {
                this.eap$x2Button.setX(bx);
                this.eap$x2Button.setY(by + spacing);
            }
            if (this.eap$divideBy5Button != null) {
                this.eap$divideBy5Button.setX(bx);
                this.eap$divideBy5Button.setY(by + spacing * 2);
            }
            if (this.eap$x5Button != null) {
                this.eap$x5Button.setX(bx);
                this.eap$x5Button.setY(by + spacing * 3);
            }
            if (this.eap$divideBy10Button != null) {
                this.eap$divideBy10Button.setX(bx);
                this.eap$divideBy10Button.setY(by + spacing * 4);
            }
            if (this.eap$x10Button != null) {
                this.eap$x10Button.setX(bx);
                this.eap$x10Button.setY(by + spacing * 5);
            }
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
                if (this.eap$lastConfigIndex != idx) {
                    this.eap$lastConfigIndex = idx;
                }
            }
        } catch (Throwable ignored) {}
    }
}
