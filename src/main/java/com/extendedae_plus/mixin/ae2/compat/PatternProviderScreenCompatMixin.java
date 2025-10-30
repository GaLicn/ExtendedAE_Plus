package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.util.Logger;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * PatternProviderScreen的兼容性Mixin
 * 优先级设置为500，避免与appflux冲突
 */
@Mixin(value = PatternProviderScreen.class, priority = 500, remap = false)
public abstract class PatternProviderScreenCompatMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {
    public PatternProviderScreenCompatMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$initCompatUpgrades(PatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        try {
            // 检测是否应该添加升级面板
            if (UpgradeSlotCompat.shouldAddUpgradePanelToScreen()) {
                // 直接添加升级面板，不使用复杂的反射
                this.eap$addUpgradePanelDirect(menu, style);
            }
        } catch (Exception e) {
            // 静默处理异常，确保不会因为升级功能导致崩溃
            Logger.EAP$LOGGER.error("PatternProviderScreen兼容性升级面板初始化失败", e);
        }
    }

    @Unique
    private void eap$addUpgradePanelDirect(PatternProviderMenu menu, ScreenStyle style) {
        try {
            // 直接添加升级面板
            this.widgets.add("upgrades", new UpgradesPanel(
                    menu.getSlots(SlotSemantics.UPGRADE),
                    this::eap$getCompatibleUpgrades));
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("直接添加升级面板失败", e);
        }
    }

    @Unique
    private List<Component> eap$getCompatibleUpgrades() {
        var list = new ArrayList<Component>();
        list.add(GuiText.CompatibleUpgrades.text());

        try {
            if (menu instanceof UpgradeSlotCompat.IUpgradeableMenuCompat compatMenu) {
                var upgrades = compatMenu.getCompatUpgrades();
                if (upgrades != null) {
                    list.addAll(Upgrades.getTooltipLinesForMachine(upgrades.getUpgradableItem()));
                }
            }
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("获取兼容升级列表失败", e);
        }

        return list;
    }
}
