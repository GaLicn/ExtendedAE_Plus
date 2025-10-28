package com.extendedae_plus.mixin.gtceu;

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
@Mixin(value = SlotWidget.class, remap = false)
public abstract class SlotWidgetMixin {
    /**
     * 在 SlotWidget.drawInBackground(...) 尾部注入绘制数字逻辑
     */
    @Inject(method = "drawInBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void onDrawInBackgroundTail(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!AEPatternViewSlotWidget.class.isInstance(this)) return;
        // 把 this 强转为 SlotWidget（目标类继承自 SlotWidget）
        SlotWidget self = (SlotWidget) (Object) this;

        Slot handler = self.getHandler();
        if (handler == null) return;

        // 使用 getRealStack 来尊重 setItemHook 的渲染替换
        ItemStack displayStack = self.getRealStack(handler.getItem());
        if (displayStack == null || displayStack.isEmpty()) return;

        ItemStack stack = handler.getItem();

        String patternOutputText = GuiUtil.getPatternOutputText(stack);

        Position pos = self.getPosition(); // 来自 Widget 的方法，继承可用
        Size size = self.getSize();       // 同上

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        RenderSystem.disableDepthTest();
        DrawerHelper.drawStringFixedCorner(
                graphics,
                patternOutputText,
                pos.x + size.width,
                pos.y + size.height,
                0xFFFFFFFF,
                true,
                0.75f);
        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
    }
}