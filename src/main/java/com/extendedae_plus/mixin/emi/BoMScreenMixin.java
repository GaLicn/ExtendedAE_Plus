package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.client.emi.BoMMappingOverlay;
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

import java.util.List;

/**
 * EMI 合成链（配方树/BoM）界面的一键批量编码入口与映射状态标记。
 * <p>
 * 字母 A 绘制在“总耗材”标题左侧，与右侧“正在查看配方树”切换按钮（mode）位置对称；
 * 无背景渲染，仅文字。点击后遍历整棵树，把所有涉及配方的样板批量发送
 * （合成样板进装配矩阵，处理样板按映射进供应器，都不行才落背包）。
 * <p>
 * 若树上还存在缺少供应器映射的节点，A 变红并拒绝执行，引导玩家先点击节点上的感叹号补映射。
 */
@Mixin(value = BoMScreen.class, remap = false)
public abstract class BoMScreenMixin {

	@Shadow @Final private static int NODE_VERTICAL_SPACING;
	@Shadow private double offX;
	@Shadow private double offY;
	@Shadow private int nodeHeight;
	// 字段描述符是 Ljava/util/List;，泛型不参与 mixin 匹配；元素是 private 内部类 BoMScreen$Node。
	@Shadow private List<?> nodes;
	@Shadow public abstract float getScale();

	// 与 mode 按钮（总耗材标题右侧 totalCostWidth/2+4 处的 16x16）左右对称
	@Unique private static final int EAP_BTN_SIZE = 16;

	@Unique private int eapBtnL = Integer.MIN_VALUE;
	@Unique private int eapBtnT = Integer.MIN_VALUE;
	@Unique private int eapBtnR = Integer.MIN_VALUE;
	@Unique private int eapBtnB = Integer.MIN_VALUE;
	@Unique private int eapBlockingCount;

	/**
	 * 在 batcher.draw() 之后绘制：此处仍在 render 的 translate/scale 矩阵内，可直接用树坐标，
	 * 且物品图标已经批绘完成，感叹号不会被图标盖掉。
	 */
	@Inject(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Ldev/emi/emi/screen/StackBatcher;draw()V",
					shift = At.Shift.AFTER
			),
			require = 1
	)
	private void eap$drawMappingMarkers(GuiGraphics raw, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		BoMScreen self = (BoMScreen) (Object) this;
		float scale = getScale();
		BoMMappingOverlay.render(raw, nodes,
				BoMMappingOverlay.toTreeX(self, mouseX, scale, offX),
				BoMMappingOverlay.toTreeY(self, mouseY, scale, offY));
	}

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

		eapBlockingCount = BoMMappingOverlay.countBlocking();
		boolean blocked = eapBlockingCount > 0;
		boolean hovered = mouseX >= lx && mouseX < rx && mouseY >= ty && mouseY < by;

		// 缺映射时标红；否则默认白色，悬停使用 mode 按钮（正在查看配方树）同款高亮色 0x80/0x99/0xFF
		int color;
		if (blocked) {
			color = hovered ? 0xFFFFCCCC : 0xFFFF5555;
		} else {
			color = hovered ? 0xFF8099FF : 0xFFFFFFFF;
		}
		raw.drawString(font, "A", lx + 4, ty + 4, color, true);

		eapBtnL = lx;
		eapBtnT = ty;
		eapBtnR = rx;
		eapBtnB = by;

		// 感叹号提示画在缩放矩阵外，否则提示框会跟着树缩放。
		var markerTooltip = com.extendedae_plus.client.emi.BoMMappingOverlay.hoveredTooltip();
		if (markerTooltip != null) {
			raw.renderComponentTooltip(font, markerTooltip, mouseX, mouseY);
			return;
		}

		if (hovered) {
			Component tip = blocked
					? Component.translatable("tooltip.extendedae_plus.bom_encode.blocked", eapBlockingCount)
					: Component.translatable("tooltip.extendedae_plus.bom_encode.ready");
			raw.renderTooltip(font, tip, mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
	private void eap$onEncodeButtonClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (button != 0) {
			return;
		}

		BoMScreen self = (BoMScreen) (Object) this;
		float scale = getScale();

		// 感叹号优先：它压在物品格子上，必须在 EMI 自己的节点点击处理之前拦下。
		if (BoMMappingOverlay.mouseClicked(self,
				BoMMappingOverlay.toTreeX(self, mouseX, scale, offX),
				BoMMappingOverlay.toTreeY(self, mouseY, scale, offY))) {
			cir.setReturnValue(true);
			return;
		}

		if (eapBtnL == Integer.MIN_VALUE) {
			return;
		}
		if (mouseX >= eapBtnL && mouseX < eapBtnR && mouseY >= eapBtnT && mouseY < eapBtnB) {
			if (eapBlockingCount > 0) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.player != null) {
					mc.player.displayClientMessage(Component.translatable(
							"message.extendedae_plus.bom_encode.blocked", eapBlockingCount), true);
				}
			} else {
				com.extendedae_plus.client.event.EmiCtrlQHandler.encodeBoMTreeAll(
						Screen.hasShiftDown(), Screen.hasAltDown());
			}
			cir.setReturnValue(true);
		}
	}
}
