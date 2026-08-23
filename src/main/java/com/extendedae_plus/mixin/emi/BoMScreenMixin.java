package com.extendedae_plus.mixin.emi;

import dev.emi.emi.bom.BoM;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EMI 合成链（配方树/BoM）界面的一键批量编码入口。
 * 字母 A 绘制在"总耗材"标题左侧，与右侧"正在查看配方树"切换按钮（mode）位置对称；
 * 无背景渲染，仅文字。点击后遍历整棵树，把所有涉及配方的样板批量发送
 * （直传装配矩阵，无矩阵时落背包）。
 */
@Mixin(value = BoMScreen.class, remap = false)
public abstract class BoMScreenMixin {

	@Shadow @Final private static int NODE_VERTICAL_SPACING;
	@Shadow private double offX;
	@Shadow private double offY;
	@Shadow private int nodeHeight;
	@Shadow public abstract float getScale();

	// 与 mode 按钮（总耗材标题右侧 totalCostWidth/2+4 处的 16x16）左右对称
	@Unique private static final int EAP_BTN_SIZE = 16;

	@Unique private int eapBtnL = Integer.MIN_VALUE;
	@Unique private int eapBtnT = Integer.MIN_VALUE;
	@Unique private int eapBtnR = Integer.MIN_VALUE;
	@Unique private int eapBtnB = Integer.MIN_VALUE;

	@Inject(method = "render", at = @At("TAIL"), remap = false)
	private void eap$drawEncodeButton(GuiGraphics raw, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		eapBtnL = Integer.MIN_VALUE;
		if (BoM.tree == null || BoM.tree.goal == null) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int totalCostWidth = font.width(Component.translatable("emi.total_cost"));

		int cy = nodeHeight * NODE_VERTICAL_SPACING * 2;
		float scale = getScale();
		Screen self = (Screen) (Object) this;
		int scaledX = -(totalCostWidth / 2 + 20);
		int scaledY = cy - 20;

		// 缩放坐标系 → 屏幕坐标系（复刻 render 内的 translate/scale 变换）
		int lx = (int) Math.round(self.width / 2.0 + (offX + scaledX) * scale);
		int ty = (int) Math.round(self.height / 2.0 + (offY + scaledY) * scale);
		int rx = (int) Math.round(self.width / 2.0 + (offX + scaledX + EAP_BTN_SIZE) * scale);
		int by = (int) Math.round(self.height / 2.0 + (offY + scaledY + EAP_BTN_SIZE) * scale);

		boolean hovered = mouseX >= lx && mouseX < rx && mouseY >= ty && mouseY < by;
		// 默认白色；悬停使用 mode 按钮（正在查看配方树）同款高亮色 0x80/0x99/0xFF
		raw.drawString(font, "A", lx + 4, ty + 4,
				hovered ? 0xFF8099FF : 0xFFFFFFFF, true);

		eapBtnL = lx;
		eapBtnT = ty;
		eapBtnR = rx;
		eapBtnB = by;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
	private void eap$onEncodeButtonClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (eapBtnL == Integer.MIN_VALUE || button != 0) {
			return;
		}
		if (mouseX >= eapBtnL && mouseX < eapBtnR && mouseY >= eapBtnT && mouseY < eapBtnB) {
			com.extendedae_plus.client.event.EmiCtrlQHandler.encodeBoMTreeAll(
					Screen.hasShiftDown(), Screen.hasAltDown());
			cir.setReturnValue(true);
		}
	}
}
