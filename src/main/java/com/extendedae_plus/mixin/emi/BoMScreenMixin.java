package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.emi.BoMMappingOverlay;
import com.extendedae_plus.client.emi.BoMPendingSelectOverlay;
import com.extendedae_plus.client.event.EmiCtrlQHandler;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
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
 * 上传按钮画在“总耗材”标题左侧，与右侧“正在查看配方树”切换按钮（{@code mode}）左右对称；
 * 无背景，只有图标。点击后遍历整棵树，把所有涉及配方的样板批量发送
 * （合成样板进装配矩阵，处理样板按映射进供应器，都不行才落背包）。
 * <p>
 * 若树上还存在缺少供应器映射的节点，按钮换成红色图标并拒绝执行，
 * 引导玩家先点击节点上的感叹号补映射。
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

	@Unique private static final ResourceLocation EAP_UPLOAD_TEX =
			ExtendedAEPlus.id("textures/gui/upload.png");
	@Unique private static final ResourceLocation EAP_UPLOAD_ERROR_TEX =
			ExtendedAEPlus.id("textures/gui/upload_error.png");
	// 与 mode 按钮（总耗材标题右侧 totalCostWidth/2+4 处的 16x16）同尺寸并左右对称
	@Unique private static final int EAP_BTN_SIZE = 16;

	// 按钮范围记录在树坐标系里：绘制与命中判定共用同一套坐标，缩放拖动都不会错位。
	@Unique private int eapBtnX = Integer.MIN_VALUE;
	@Unique private int eapBtnY;
	@Unique private int eapBlockingCount;

	/**
	 * 每帧先清掉上一帧的悬浮状态。
	 * <p>
	 * 不能只在绘制处清：BoM.tree 为空时 EMI 走「没有配方树」分支，
	 * batcher.draw() 不会执行、绘制注入点也不会触发，
	 * 上一棵树留下的按钮范围与节点标记会残留，导致空界面上仍能悬停出提示或点出动作。
	 */
	@Inject(method = "render", at = @At("HEAD"))
	private void eap$resetOverlayState(GuiGraphics raw, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		eapBtnX = Integer.MIN_VALUE;
		BoMMappingOverlay.reset();
		// 迷你选择区要认得自己挂在哪个界面上：界面一走就得放弃队列，样板不能烂在服务端。
		BoMPendingSelectOverlay.noteHost((Screen) (Object) this);
	}

	/**
	 * 在 batcher.draw() 之后绘制：此处仍在 render 的 translate/scale 矩阵内，可直接用树坐标，
	 * 且物品图标已经批绘完成，标记与按钮不会被图标盖掉。
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
	private void eap$drawOverlay(GuiGraphics raw, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		BoMScreen self = (BoMScreen) (Object) this;
		float scale = getScale();
		int treeX = BoMMappingOverlay.toTreeX(self, mouseX, scale, offX);
		int treeY = BoMMappingOverlay.toTreeY(self, mouseY, scale, offY);

		BoMMappingOverlay.render(raw, nodes, treeX, treeY);
		eap$drawUploadButton(raw, treeX, treeY);
	}

	@Unique
	private void eap$drawUploadButton(GuiGraphics raw, int treeX, int treeY) {
		eapBtnX = Integer.MIN_VALUE;
		if (BoM.tree == null || BoM.tree.goal == null) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		int totalCostWidth = font.width(Component.translatable("emi.total_cost"));
		int cy = nodeHeight * NODE_VERTICAL_SPACING * 2;
		// mode 按钮位于 totalCostWidth/2+4，这里镜像到标题左侧的同样间距。
		int x = -(totalCostWidth / 2 + 4) - EAP_BTN_SIZE;
		int y = cy - 20;

		eapBlockingCount = BoMMappingOverlay.countBlocking();
		boolean blocked = eapBlockingCount > 0;
		boolean hovered = treeX >= x && treeX < x + EAP_BTN_SIZE
				&& treeY >= y && treeY < y + EAP_BTN_SIZE;

		// 图标自带颜色（白＝可编码，红＝缺映射），悬停只做明暗提示：
		// 白色图标用 mode 按钮同款蓝色高亮；红色图标改为平时压暗、悬停回满亮，
		// 因为红色乘上蓝色高亮会变成看不清的暗紫。
		if (blocked) {
			float b = hovered ? 1f : 0.75f;
			raw.setColor(b, b, b, 1f);
		} else if (hovered) {
			raw.setColor(0.5f, 0.6f, 1f, 1f);
		}
		raw.blit(blocked ? EAP_UPLOAD_ERROR_TEX : EAP_UPLOAD_TEX, x, y, 0, 0,
				EAP_BTN_SIZE, EAP_BTN_SIZE, EAP_BTN_SIZE, EAP_BTN_SIZE);
		raw.setColor(1f, 1f, 1f, 1f);

		eapBtnX = x;
		eapBtnY = y;
	}

	/** 提示必须画在缩放矩阵之外，否则提示框会跟着树一起缩放。迷你选择区同理，且要压在最上层。 */
	@Inject(method = "render", at = @At("TAIL"), remap = false)
	private void eap$drawOverlayTooltips(GuiGraphics raw, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		BoMScreen self = (BoMScreen) (Object) this;
		Font font = Minecraft.getInstance().font;

		BoMPendingSelectOverlay.render(raw, self.width, self.height, mouseX, mouseY, getScale());
		if (BoMPendingSelectOverlay.contains(mouseX, mouseY)) {
			// 面板盖住的地方不该再弹树上的提示。
			return;
		}

		List<Component> markerTooltip = BoMMappingOverlay.hoveredTooltip();
		if (markerTooltip != null) {
			raw.renderComponentTooltip(font, markerTooltip, mouseX, mouseY);
			return;
		}

		if (eapBtnX == Integer.MIN_VALUE || !eap$isOverButton(mouseX, mouseY)) {
			return;
		}
		Component tip = eapBlockingCount > 0
				? Component.translatable("tooltip.extendedae_plus.bom_encode.blocked", eapBlockingCount)
				: Component.translatable("tooltip.extendedae_plus.bom_encode.ready");
		raw.renderTooltip(font, tip, mouseX, mouseY);
	}

	/** 面板盖住的区域不算悬停在树上：EMI 的物品提示与点击语义都由这个方法派生。 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "getHoveredStack", at = @At("HEAD"), cancellable = true, remap = false)
	private void eap$suppressHoverUnderPanel(int mouseX, int mouseY, CallbackInfoReturnable cir) {
		if (BoMPendingSelectOverlay.contains(mouseX, mouseY)) {
			cir.setReturnValue(null);
		}
	}

	/** 面板上滚轮翻页，不该把整棵树缩掉。 */
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
	private void eap$onOverlayScroll(double mouseX, double mouseY, double horizontal, double amount,
	                                CallbackInfoReturnable<Boolean> cir) {
		if (BoMPendingSelectOverlay.mouseScrolled(mouseX, mouseY, amount)) {
			cir.setReturnValue(true);
		}
	}

	/** 在面板上按住拖动不该平移整棵树。 */
	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, remap = false)
	private void eap$onOverlayDrag(double mouseX, double mouseY, int button, double deltaX, double deltaY,
	                              CallbackInfoReturnable<Boolean> cir) {
		if (BoMPendingSelectOverlay.contains(mouseX, mouseY)) {
			cir.setReturnValue(true);
		}
	}

	/** 面板开着时 ESC 只放弃队列，不连带关掉配方树，否则玩家会连树一起丢。 */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, remap = false)
	private void eap$onOverlayKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && BoMPendingSelectOverlay.escapePressed()) {
			cir.setReturnValue(true);
		}
	}

	@Unique
	private boolean eap$isOverButton(double mouseX, double mouseY) {
		BoMScreen self = (BoMScreen) (Object) this;
		float scale = getScale();
		int treeX = BoMMappingOverlay.toTreeX(self, mouseX, scale, offX);
		int treeY = BoMMappingOverlay.toTreeY(self, mouseY, scale, offY);
		return treeX >= eapBtnX && treeX < eapBtnX + EAP_BTN_SIZE
				&& treeY >= eapBtnY && treeY < eapBtnY + EAP_BTN_SIZE;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
	private void eap$onOverlayClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		// 迷你选择区最优先：它画在屏幕坐标系里、压在树上，点击不能穿透过去展开节点或跳走。
		if (BoMPendingSelectOverlay.mouseClicked(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			return;
		}
		if (button != 0) {
			return;
		}

		BoMScreen self = (BoMScreen) (Object) this;
		float scale = getScale();

		// 感叹号优先：它贴在节点边框上，必须在 EMI 自己的节点点击处理之前拦下。
		if (BoMMappingOverlay.mouseClicked(self,
				BoMMappingOverlay.toTreeX(self, mouseX, scale, offX),
				BoMMappingOverlay.toTreeY(self, mouseY, scale, offY))) {
			cir.setReturnValue(true);
			return;
		}

		if (eapBtnX == Integer.MIN_VALUE || !eap$isOverButton(mouseX, mouseY)) {
			return;
		}
		if (eapBlockingCount > 0) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.translatable(
						"message.extendedae_plus.bom_encode.blocked", eapBlockingCount), true);
			}
		} else {
			EmiCtrlQHandler.encodeBoMTreeAll(
					Screen.hasShiftDown(), Screen.hasAltDown());
		}
		cir.setReturnValue(true);
	}
}
