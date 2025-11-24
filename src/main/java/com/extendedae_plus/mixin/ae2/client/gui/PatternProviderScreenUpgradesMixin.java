package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.layout.SlotGridLayout;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.SlotPosition;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.IStyleAccessor;
import com.extendedae_plus.compat.AppliedFluxCompat;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = PatternProviderScreen.class, priority = 1500, remap = false)
public abstract class PatternProviderScreenUpgradesMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    public PatternProviderScreenUpgradesMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void eap$initUpgrades(PatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {

        if (!UpgradeSlotCompat.shouldAddUpgradePanelToScreen()) {
            return;
        }

        // 若已安装 AppliedFlux，则由 AE2/AppliedFlux 自己负责渲染升级面板，避免我们重复添加导致界面显示两个槽
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return;
        }

        // 使用改进的AppliedFlux兼容性检测
        @SuppressWarnings("unchecked")
        PatternProviderScreen<PatternProviderMenu> screen = (PatternProviderScreen<PatternProviderMenu>) (Object) this;

        boolean shouldSkip = AppliedFluxCompat.shouldSkipOurUpgradePanel(screen);

        if (shouldSkip) {
        } else {

            // 检查是否已经存在upgrades widget
            try {
                // 尝试添加升级面板
                this.widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), this::eap$getCompatibleUpgrades));
            } catch (IllegalStateException e) {
                com.extendedae_plus.util.ExtendedAELogger.LOGGER.warn("[样板供应器][界面] 升级面板已存在，跳过添加: {}", e.getMessage());
                return; // 如果升级面板已存在，不继续添加其他内容
            }

            // 设置TOOLBOX样式（完全按照AppliedFlux的方式）
            var sp = new SlotPosition();
            sp.setBottom(84);
            sp.setRight(1);
            sp.setGrid(SlotGridLayout.BREAK_AFTER_3COLS);
            var ws = new WidgetStyle();
            ws.setRight(2);
            ws.setBottom(90);
            ws.setWidth(59);
            ws.setHeight(66);
            style.getSlots().put("TOOLBOX", sp);
            ((IStyleAccessor) style).getImages().put("toolbox", Blitter.texture("guis/extra_panels.png", 128, 128).src(69, 62, 59, 66));
            ((IStyleAccessor) style).getWidgets().put("toolbox", ws);

            // 添加工具箱面板
            if (menu instanceof AEBaseMenu base && base instanceof com.extendedae_plus.api.bridge.IUpgradableMenu upg && upg.eap$getToolbox() != null && upg.eap$getToolbox().isPresent()) {
                try {
                    this.widgets.add("toolbox", new ToolboxPanel(style, upg.eap$getToolbox().getName()));
                } catch (IllegalStateException e) {
                    com.extendedae_plus.util.ExtendedAELogger.LOGGER.warn("[样板供应器][界面] 工具箱面板已存在，跳过添加: {}", e.getMessage());
                }
            }

        }
    }

    @Unique
    private List<Component> eap$getCompatibleUpgrades() {
        var list = new ArrayList<Component>();
        list.add(GuiText.CompatibleUpgrades.text());
        if (this.menu instanceof AEBaseMenu base) {
            var target = base.getTarget();
            if (target instanceof PatternProviderLogicHost host) {
                list.addAll(Upgrades.getTooltipLinesForMachine(host.getTerminalIcon().getItem()));
            }
        }
        return list;
    }
}
