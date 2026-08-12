package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import appeng.api.util.AEColor;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingStatusEntry;
import com.extendedae_plus.content.ClientManualCraftingStatusStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = CraftingStatusTableRenderer.class, remap = false)
public abstract class CraftingStatusTableRendererMixin {
    @Unique
    private static final int EAP_BACKGROUND_ALPHA = 0x5A000000;

    @Inject(method = "getEntryBackgroundColor", at = @At("RETURN"), cancellable = true)
    private void eap$markManualWaitingEntries(CraftingStatusEntry entry, CallbackInfoReturnable<Integer> cir) {
        if (!AEConfig.instance().isUseColoredCraftingStatus()) {
            return;
        }

        if (this.eap$getManualWaitingAmount(entry) > 0) {
            cir.setReturnValue(AEColor.PURPLE.blackVariant | EAP_BACKGROUND_ALPHA);
        }
    }

    @Inject(
            method = "getEntryDescription(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true)
    private void eap$replaceManualWaitingDescription(CraftingStatusEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        long manualWaiting = this.eap$getManualWaitingAmount(entry);
        if (manualWaiting <= 0 || entry.getWhat() == null) {
            return;
        }

        List<Component> lines = new ArrayList<>(2);
        lines.add(GuiText.FromStorage.text(
                entry.getWhat().formatAmount(entry.getStoredAmount(), AmountFormat.SLOT)));
        lines.add(this.eap$manualWaitingLine(entry, manualWaiting, AmountFormat.SLOT));
        cir.setReturnValue(lines);
    }

    @Inject(
            method = "getEntryTooltip(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true)
    private void eap$replaceManualWaitingTooltip(CraftingStatusEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        long manualWaiting = this.eap$getManualWaitingAmount(entry);
        if (manualWaiting <= 0 || entry.getWhat() == null) {
            return;
        }

        List<Component> lines = AEKeyRendering.getTooltip(entry.getWhat());
        lines.add(GuiText.FromStorage.text(
                entry.getWhat().formatAmount(entry.getStoredAmount(), AmountFormat.FULL)));
        lines.add(this.eap$manualWaitingLine(entry, manualWaiting, AmountFormat.FULL));
        cir.setReturnValue(lines);
    }

    @Unique
    private Component eap$manualWaitingLine(CraftingStatusEntry entry, long manualWaiting, AmountFormat format) {
        return Component.translatable(
                "tooltip.extendedae_plus.crafting.manual_waiting",
                entry.getWhat().formatAmount(manualWaiting, format)).withStyle(ChatFormatting.AQUA);
    }

    @Unique
    private long eap$getManualWaitingAmount(CraftingStatusEntry entry) {
        if (entry == null || entry.getWhat() == null) {
            return 0;
        }

        var mc = Minecraft.getInstance();
        if (mc == null || !(mc.screen instanceof CraftingCPUScreen<?> craftingScreen)) {
            return 0;
        }

        return ClientManualCraftingStatusStore.getManualWaitingAmount(
                craftingScreen.getMenu().containerId,
                entry.getWhat());
    }
}
