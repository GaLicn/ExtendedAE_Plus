package com.extendedae_plus.mixin.advancedae.client.gui;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.layout.SlotGridLayout;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.SlotPosition;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.api.IStyleAccessor;
import com.extendedae_plus.api.bridge.IUpgradableMenu;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.pedroksl.advanced_ae.client.gui.SmallAdvPatternProviderScreen;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import net.pedroksl.advanced_ae.gui.advpatternprovider.SmallAdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** 为 AdvancedAE 小型供应器显示本地频道卡槽。 */
@Mixin(value = SmallAdvPatternProviderScreen.class, remap = false)
public abstract class SmallAdvPatternProviderScreenUpgradesMixin extends AEBaseScreen<SmallAdvPatternProviderMenu> {
    protected SmallAdvPatternProviderScreenUpgradesMixin(
            SmallAdvPatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void eap$addChannelCardPanel(
            SmallAdvPatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            return;
        }

        this.widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), this::eap$getCompatibleUpgrades));

        var slotPosition = new SlotPosition();
        slotPosition.setBottom(84);
        slotPosition.setRight(1);
        slotPosition.setGrid(SlotGridLayout.BREAK_AFTER_3COLS);
        var widgetStyle = new WidgetStyle();
        widgetStyle.setRight(2);
        widgetStyle.setBottom(90);
        widgetStyle.setWidth(59);
        widgetStyle.setHeight(66);
        style.getSlots().put("TOOLBOX", slotPosition);
        ((IStyleAccessor) style).getImages().put(
                "toolbox", Blitter.texture("guis/extra_panels.png", 128, 128).src(69, 62, 59, 66));
        ((IStyleAccessor) style).getWidgets().put("toolbox", widgetStyle);

        if (menu instanceof IUpgradableMenu upgradable
                && upgradable.eap$getToolbox() != null
                && upgradable.eap$getToolbox().isPresent()) {
            this.widgets.add("toolbox", new ToolboxPanel(style, upgradable.eap$getToolbox().getName()));
        }
    }

    @Unique
    private List<Component> eap$getCompatibleUpgrades() {
        var lines = new ArrayList<Component>();
        lines.add(GuiText.CompatibleUpgrades.text());
        if (((AEBaseMenu) menu).getTarget() instanceof AdvPatternProviderLogicHost host) {
            lines.addAll(Upgrades.getTooltipLinesForMachine(host.getTerminalIcon().getItem()));
        }
        return lines;
    }
}
