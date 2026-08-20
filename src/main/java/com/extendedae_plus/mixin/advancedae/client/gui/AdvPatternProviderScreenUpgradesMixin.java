package com.extendedae_plus.mixin.advancedae.client.gui;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.pedroksl.advanced_ae.client.gui.AdvPatternProviderScreen;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 AdvancedAE 高级供应器界面显示频道卡槽。 */
@Mixin(value = AdvPatternProviderScreen.class, remap = false)
public abstract class AdvPatternProviderScreenUpgradesMixin extends AEBaseScreen<AdvPatternProviderMenu> {
    protected AdvPatternProviderScreenUpgradesMixin(AdvPatternProviderMenu menu, Inventory inventory,
                                                     Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$addUpgradePanel(AdvPatternProviderMenu menu, Inventory inventory, Component title,
                                     ScreenStyle style, CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) return;
        this.widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), this::eap$getTooltip));
    }

    @Unique
    private List<Component> eap$getTooltip() {
        var lines = new ArrayList<Component>();
        lines.add(GuiText.CompatibleUpgrades.text());
        if (((AEBaseMenu) this.menu).getTarget() instanceof AdvPatternProviderLogicHost host) {
            lines.addAll(Upgrades.getTooltipLinesForMachine(host.getTerminalIcon().getItem()));
        }
        return lines;
    }
}
