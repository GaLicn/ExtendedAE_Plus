package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
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

@Mixin(value = PatternProviderScreen.class, priority = 2000, remap = false)
public abstract class PatternProviderScreenUpgradesMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$initUpgrades(PatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldAddUpgradePanelToScreen()) {
            return;
        }
        try {
            this.widgets.add("upgrades", new UpgradesPanel(
                    menu.getSlots(SlotSemantics.UPGRADE),
                    this::eap$getCompatibleUpgrades));
        } catch (IllegalStateException already) {
            // 已存在同名面板（可能由 AE2 或其他模组添加），忽略
            com.extendedae_plus.util.ExtendedAELogger.LOGGER.debug("[样板供应器][界面] 升级面板已存在，跳过添加");
        }
        if (menu instanceof AEBaseMenu base && base instanceof com.extendedae_plus.bridge.IUpgradableMenu upg && upg.getToolbox() != null && upg.getToolbox().isPresent()) {
            try {
                this.widgets.add("toolbox", new ToolboxPanel(style, upg.getToolbox().getName()));
            } catch (IllegalStateException already) {
                com.extendedae_plus.util.ExtendedAELogger.LOGGER.debug("[样板供应器][界面] 工具箱面板已存在，跳过添加");
            }
        }
    }

    @Unique
    private List<Component> eap$getCompatibleUpgrades() {
        var list = new ArrayList<Component>();
        list.add(GuiText.CompatibleUpgrades.text());
        if (menu instanceof AEBaseMenu base) {
            var target = base.getTarget();
            if (target instanceof PatternProviderLogicHost host) {
                list.addAll(Upgrades.getTooltipLinesForMachine(host.getTerminalIcon().getItem()));
            }
        }
        return list;
    }

    public PatternProviderScreenUpgradesMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
