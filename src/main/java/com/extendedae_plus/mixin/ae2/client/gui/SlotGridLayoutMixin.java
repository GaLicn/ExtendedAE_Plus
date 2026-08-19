package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.Point;
import appeng.client.gui.layout.SlotGridLayout;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.api.bridge.ExPatternProviderMenuPageBridge;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlotGridLayout.class)
public abstract class SlotGridLayoutMixin {
    @Unique
    private static final int SLOTS_PER_PAGE = 36;

    @Inject(method = "getRowBreakPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private static void eap$onGetRowBreakPosition(int x, int y, int semanticIdx, int cols,
                                                  CallbackInfoReturnable<Point> cir) {
        if (cols != 9 || !(Minecraft.getInstance().screen instanceof GuiExPatternProvider provider)) {
            return;
        }

        int currentPage = 0;
        if (provider instanceof IExPatternPage pageAccessor) {
            currentPage = pageAccessor.eap$getCurrentPage();
        } else if (provider.getMenu() instanceof ExPatternProviderMenuPageBridge bridge) {
            currentPage = bridge.eap$getPage();
        }

        if (semanticIdx / SLOTS_PER_PAGE != currentPage) {
            cir.setReturnValue(new Point(-10000, -10000));
            cir.cancel();
            return;
        }

        int slotInPage = semanticIdx % SLOTS_PER_PAGE;
        cir.setReturnValue(new Point(x + (slotInPage % 9) * 18, y + (slotInPage / 9) * 18));
        cir.cancel();
    }
}
