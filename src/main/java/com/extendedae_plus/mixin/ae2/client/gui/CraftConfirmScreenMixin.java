package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.mixin.ae2.accessor.WidgetContainerAccessor;
import com.extendedae_plus.util.NumberFormatUtil;
import com.extendedae_plus.network.crafting.ForceCraftStartFlagC2SPacket;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import java.text.NumberFormat;

@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {
    protected CraftConfirmScreenMixin(CraftConfirmMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Unique
    private static final Component EAP_BOOKMARK_TEXT = Component.translatable("gui.extendedae_plus.add_bookmark");

    @Unique
    private static final Component EAP_BOOKMARK_TOOLTIP = Component.translatable("tooltip.extendedae_plus.add_missing_to_jei_bookmark");

    @Unique
    private static final Component EAP_CANCEL_TEXT = GuiText.Cancel.text();

    @Unique
    private static final Component EAP_START_TEXT = GuiText.Start.text();

    @Unique
    private static final Component EAP_FORCE_START_TEXT = Component.translatable("gui.extendedae_plus.force_start");

    @Unique
    private static final Component EAP_FORCE_START_TOOLTIP = Component.translatable("tooltip.extendedae_plus.force_start");

    @Unique
    private static String eap$formatCraftBytes(long number) {
        if (number < 10_000) {
            return NumberFormat.getInstance().format(number);
        }
        return NumberFormatUtil.formatNumber(number);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"), remap = false)
    private void eap$rewriteCraftingPlanTitle(CallbackInfo ci) {
        CraftConfirmScreen self = (CraftConfirmScreen) (Object) this;
        CraftingPlanSummary plan = self.getMenu().getPlan();
        if (plan == null) {
            return;
        }

        String byteUsed = eap$formatCraftBytes(plan.getUsedBytes());
        Component planDetails = GuiText.BytesUsed.text(byteUsed);
        this.setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.CraftingPlan.text(planDetails));
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void eap$updateButtons(CallbackInfo ci) {
        CraftConfirmScreen self = (CraftConfirmScreen) (Object) this;
        try {
            WidgetContainer widgets = ((AEBaseScreenAccessor<?>) self).eap$getWidgets();
            if (widgets == null) {
                return;
            }

            var widgetsMap = ((WidgetContainerAccessor) widgets).eap$getWidgetsMap();
            boolean shiftDown = Screen.hasShiftDown();
            CraftingPlanSummary plan = self.getMenu().getPlan();
            boolean forceStart = shiftDown && plan != null && plan.isSimulation();

            AbstractWidget startWidget = widgetsMap.get("start");
            if (startWidget instanceof Button startButton) {
                if (forceStart) {
                    startButton.active = !self.getMenu().hasNoCPU();
                    startButton.setMessage(EAP_FORCE_START_TEXT);
                    startButton.setTooltip(Tooltip.create(EAP_FORCE_START_TOOLTIP));
                } else {
                    startButton.setMessage(EAP_START_TEXT);
                    startButton.setTooltip(null);
                }
            }

            AbstractWidget selectCpuWidget = widgetsMap.get("selectCpu");
            if (forceStart && selectCpuWidget instanceof Button selectCpuButton) {
                selectCpuButton.active = true;
            }

            if (!ModList.get().isLoaded("jei")) {
                return;
            }

            AbstractWidget cancelWidget = widgetsMap.get("cancel");
            if (!(cancelWidget instanceof Button cancelButton)) {
                return;
            }

            if (shiftDown) {
                cancelButton.setMessage(EAP_BOOKMARK_TEXT);
                cancelButton.setTooltip(Tooltip.create(EAP_BOOKMARK_TOOLTIP));
            } else {
                cancelButton.setMessage(EAP_CANCEL_TEXT);
                cancelButton.setTooltip(null);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "start", at = @At("HEAD"), remap = false)
    private void eap$syncForceStartFlagBeforeStart(CallbackInfo ci) {
        CraftConfirmScreen self = (CraftConfirmScreen) (Object) this;
        var plan = self.getMenu().getPlan();
        boolean forceStart = Screen.hasShiftDown() && plan != null && plan.isSimulation();
        ModNetwork.CHANNEL.sendToServer(new ForceCraftStartFlagC2SPacket(forceStart));
    }
}
