package com.extendedae_plus_gtladd.mixin.gtceu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import com.extendedae_plus.content.ClientPatternHighlightStore;
import com.extendedae_plus.util.GuiUtil;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.slot.AEPatternViewSlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(
    value = {AEPatternViewSlotWidget.class},
    remap = false
)
public abstract class AEPatternViewSlotWidgetMixin {
    @Inject(
        method = {"drawBackgroundTexture"},
        at = {@At("TAIL")}
    )
    private void onDrawInBackgroundTail(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (AEPatternViewSlotWidget.class.isInstance(this)) {
            SlotWidget self = (SlotWidget) (Object) this;
            Slot handler = self.getHandler();
            if (handler != null) {
                ItemStack displayStack = self.getRealStack(handler.getItem());
                if (displayStack != null && !displayStack.isEmpty()) {
                    ItemStack stack = handler.getItem();
                    Position pos = self.getPosition();

                    try {
                        var details = PatternDetailsHelper.decodePattern(
                                stack,
                                Minecraft.getInstance().level,
                                false);
                        if (details != null && details.getOutputs() != null && details.getOutputs().length > 0) {
                            AEKey key = details.getOutputs()[0].what();
                            if (key != null && ClientPatternHighlightStore.hasHighlight(key)) {
                                GuiUtil.drawSlotRainbowHighlight(graphics, pos.x + 1, pos.y + 1);
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                }
            }
        }
    }
}
