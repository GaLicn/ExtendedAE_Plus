package com.extendedae_plus_gtladd.mixin.gtceu;

import com.extendedae_plus.util.GuiUtil;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.slot.AEPatternViewSlotWidget;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.mojang.blaze3d.systems.RenderSystem;
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
    value = {SlotWidget.class},
    remap = false
)
public abstract class SlotWidgetMixin {
    @Inject(
        method = {"drawInBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"},
        at = {@At("TAIL")}
    )
    private void onDrawInBackgroundTail(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (AEPatternViewSlotWidget.class.isInstance(this)) {
            SlotWidget self = (SlotWidget) (Object) this;
            Slot handler = self.getHandler();
            if (handler != null) {
                ItemStack displayStack = self.getRealStack(handler.getItem());
                if (displayStack != null && !displayStack.isEmpty()) {
                    ItemStack stack = handler.getItem();
                    String patternOutputText = GuiUtil.getPatternOutputText(stack);
                    Position pos = self.getPosition();
                    Size size = self.getSize();
                    graphics.pose().pushPose();
                    graphics.pose().translate(0.0F, 0.0F, 300.0F);
                    RenderSystem.disableDepthTest();
                    DrawerHelper.drawStringFixedCorner(graphics, patternOutputText, (float)(pos.x + size.width), (float)(pos.y + size.height), -1, true, 0.75F);
                    RenderSystem.enableDepthTest();
                    graphics.pose().popPose();
                }
            }
        }
    }
}
