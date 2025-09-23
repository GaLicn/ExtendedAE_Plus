package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.InterfaceScreen;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.NewIcon;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.InterfaceAdjustConfigAmountC2SPacket;
import appeng.menu.SlotSemantics;
import net.minecraft.world.inventory.Slot;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 AE2 的 ME 接口界面注入倍增/除法按钮（x2/÷2、x5/÷5、x10/÷10）。
 * 点击逻辑暂时留空，仅完成按钮创建、注册与布局维护。
 */
@Mixin(AEBaseScreen.class)
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
    // 记录最近一次在 CONFIG 槽位区域内悬停到的配置槽索引，
    // 以便当鼠标移到按钮上导致 hoveredSlot 为空时仍能进行操作。
    @Unique private int eap$lastConfigIndex = -1;

    @Inject(method = "init", at = @At("TAIL"))
    private void eap$addScaleButtons(CallbackInfo ci) {
        // 仅在 AE2 接口界面或 ExtendedAE 扩展接口界面中添加
        if (!eap$isSupportedInterfaceScreen()) {
            return;
        }
        try { LogUtils.getLogger().info("[EAP][InterfaceMixin] init tail reached, preparing scale buttons."); } catch (Throwable ignored) {}
        // 避免重复创建
        if (eap$x2Button == null) {
            eap$x2Button = new ActionEPPButton((b) -> {
                eap$sendAdjustForAllConfigs(false, 2);
            }, NewIcon.MULTIPLY2);
            eap$x2Button.setTooltip(null);
            eap$x2Button.setVisibility(true);
        }
        if (eap$divideBy2Button == null) {
            eap$divideBy2Button = new ActionEPPButton((b) -> {
                eap$sendAdjustForAllConfigs(true, 2);
            }, NewIcon.DIVIDE2);
            eap$divideBy2Button.setTooltip(null);
            eap$divideBy2Button.setVisibility(true);
        }
        if (eap$x5Button == null) {
            eap$x5Button = new ActionEPPButton((b) -> {
                eap$sendAdjustForAllConfigs(false, 5);
            }, NewIcon.MULTIPLY5);
            eap$x5Button.setTooltip(null);
            eap$x5Button.setVisibility(true);
        }
        if (eap$divideBy5Button == null) {
            eap$divideBy5Button = new ActionEPPButton((b) -> {
                eap$sendAdjustForAllConfigs(true, 5);
            }, NewIcon.DIVIDE5);
            eap$divideBy5Button.setTooltip(null);
            eap$divideBy5Button.setVisibility(true);
        }
        if (eap$x10Button == null) {
            eap$x10Button = new ActionEPPButton((b) -> {
                eap$sendAdjustForAllConfigs(false, 10);
            }, NewIcon.MULTIPLY10);
            eap$x10Button.setTooltip(null);
            eap$x10Button.setVisibility(true);
        }
        if (eap$divideBy10Button == null) {
            eap$divideBy10Button = new ActionEPPButton((b) -> {
                eap$sendAdjustForAllConfigs(true, 10);
            }, NewIcon.DIVIDE10);
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

        // 初次定位
        eap$relayoutButtons();
        try { LogUtils.getLogger().info("[EAP][InterfaceMixin] buttons added and laid out."); } catch (Throwable ignored) {}
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void eap$ensureButtons(CallbackInfo ci) {
        if (!eap$isSupportedInterfaceScreen()) {
            return;
        }
        var accessor = (ScreenAccessor) (Object) this;
        // 若被其他模组清空，补回
        if (eap$divideBy2Button != null && !accessor.eap$getRenderables().contains(eap$divideBy2Button)) {
            accessor.eap$getRenderables().add(eap$divideBy2Button);
            accessor.eap$getChildren().add(eap$divideBy2Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added divide2 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$x2Button != null && !accessor.eap$getRenderables().contains(eap$x2Button)) {
            accessor.eap$getRenderables().add(eap$x2Button);
            accessor.eap$getChildren().add(eap$x2Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added x2 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$divideBy5Button != null && !accessor.eap$getRenderables().contains(eap$divideBy5Button)) {
            accessor.eap$getRenderables().add(eap$divideBy5Button);
            accessor.eap$getChildren().add(eap$divideBy5Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added divide5 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$x5Button != null && !accessor.eap$getRenderables().contains(eap$x5Button)) {
            accessor.eap$getRenderables().add(eap$x5Button);
            accessor.eap$getChildren().add(eap$x5Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added x5 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$divideBy10Button != null && !accessor.eap$getRenderables().contains(eap$divideBy10Button)) {
            accessor.eap$getRenderables().add(eap$divideBy10Button);
            accessor.eap$getChildren().add(eap$divideBy10Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added divide10 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$x10Button != null && !accessor.eap$getRenderables().contains(eap$x10Button)) {
            accessor.eap$getRenderables().add(eap$x10Button);
            accessor.eap$getChildren().add(eap$x10Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added x10 button to renderables."); } catch (Throwable ignored) {}
        }
        // 尺寸变化时重新定位
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
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] relayout due to bounds change: left={}, top={}, w={}, h={}", curLeft, curTop, curImgW, curImgH); } catch (Throwable ignored) {}
        }
        // 每帧根据 hoveredSlot 刷新最近一次的配置槽索引
        eap$updateLastConfigFromHover();
    }

    @Unique
    private void eap$sendAdjustForHoveredConfig(boolean divide, int factor) {
        try {
            // 仅在 InterfaceScreen 中生效
            if (!eap$isSupportedInterfaceScreen()) {
                return;
            }
            // 获取悬停槽位
            Slot hovered = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getHoveredSlot();
            // 获取菜单与配置槽列表
            var screen = (AEBaseScreen<?>) (Object) this;
            var menu = screen.getMenu();
            if (!(menu instanceof appeng.menu.implementations.InterfaceMenu interfaceMenu)) {
                return;
            }
            var configSlots = interfaceMenu.getSlots(SlotSemantics.CONFIG);
            if (configSlots == null || configSlots.isEmpty()) {
                return;
            }

            // 优先根据 hoveredSlot 解析索引；若 hovered 为空，则回退使用最近一次的配置槽索引
            Integer slotFieldObj = null;
            if (hovered != null) {
                for (var s : configSlots) {
                    if (s == hovered) {
                        // 反射读取 AppEngSlot#slot
                        try {
                            var f = s.getClass().getDeclaredField("slot");
                            f.setAccessible(true);
                            Object v = f.get(s);
                            if (v instanceof Integer i) {
                                slotFieldObj = i;
                            }
                        } catch (Throwable ignored) {}
                        if (slotFieldObj == null) {
                            // 回退：使用列表位置当作索引
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
                try { LogUtils.getLogger().info("[EAP][InterfaceMixin] Using last hovered config index: {}", slotField); } catch (Throwable ignored) {}
            }
            if (slotField < 0) {
                try { LogUtils.getLogger().info("[EAP][InterfaceMixin] No hovered slot and no last config index; ignoring adjust."); } catch (Throwable ignored) {}
                return;
            }

            // 发送到服务端
            ModNetwork.CHANNEL.sendToServer(new InterfaceAdjustConfigAmountC2SPacket(slotField, divide, factor));
        } catch (Throwable ignored) {}
    }

    @Unique
    private void eap$sendAdjustForAllConfigs(boolean divide, int factor) {
        try {
            if (!eap$isSupportedInterfaceScreen()) {
                return;
            }
            // 直接发送 -1 表示对所有 CONFIG 槽生效
            ModNetwork.CHANNEL.sendToServer(new InterfaceAdjustConfigAmountC2SPacket(-1, divide, factor));
        } catch (Throwable ignored) {}
    }

    @Unique
    private boolean eap$isSupportedInterfaceScreen() {
        // AE2 原版接口界面
        if (((Object) this) instanceof InterfaceScreen) {
            return true;
        }
        // ExtendedAE 扩展接口界面（使用类名判断避免编译期硬依赖）
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
            // 按照样板供应器界面一致的布局：界面右缘外侧竖排
            int bx = leftPos + imageWidth + 1;
            int by = topPos + 70; // 向下偏移25px (从20改为45)
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
            // 在 CONFIG 槽列表中定位 hovered 对应的索引
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
                    try { LogUtils.getLogger().info("[EAP][InterfaceMixin] lastConfigIndex updated: {}", eap$lastConfigIndex); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }
}
