package com.extendedae_plus.client.emi;

import com.extendedae_plus.client.screen.RecipeTypeMappingScreen;
import com.extendedae_plus.network.RequestMappingProvidersC2SPacket;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.screen.BoMScreen;
import com.extendedae_plus.mixin.emi.BoMScreenNodeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 合成链（EMI 配方树 / BoM）上的映射状态标记。
 * <p>
 * 缺少供应器映射的节点在物品格子右上角画一个红色感叹号，点击即弹出机器挑选界面
 * （EAEP 原本的供应器选择界面，可搜索、点选即建立映射）；
 * 只要还存在这类节点，一键编码（A 按钮）就会被拦住，避免整棵树的处理样板全部落进背包。
 * <p>
 * 灰色感叹号表示该节点的配方 ID 无法解析成原版配方，加映射也无从编码，因此不拦一键编码，只跳过。
 */
public final class BoMMappingOverlay {

	/** 标记相对物品格子左上角的偏移与命中范围。 */
	private static final int GLYPH_OFFSET_X = 11;
	private static final int GLYPH_OFFSET_Y = -1;
	private static final int HIT_OFFSET_X = 9;
	private static final int HIT_OFFSET_Y = -2;
	private static final int HIT_WIDTH = 7;
	private static final int HIT_HEIGHT = 9;

	private static final int COLOR_BLOCKING = 0xFFFF5555;
	private static final int COLOR_BLOCKING_HOVER = 0xFFFFCCCC;
	private static final int COLOR_UNENCODABLE = 0xFFAAAAAA;

	/** 当前帧的标记，供点击命中复用；渲染必然发生在同一帧的点击处理之前。 */
	private static final List<Marker> MARKERS = new ArrayList<>();
	/** 当前帧被悬停的标记，工具提示需要在缩放矩阵外按屏幕坐标绘制。 */
	private static Marker hoveredMarker;

	/** countBlocking 的短时缓存：展开/折叠不改变配方集合，无需逐帧重走整棵树。 */
	private static final int BLOCKING_RECHECK_FRAMES = 20;
	private static MaterialNode cachedBlockingGoal;
	private static int cachedBlockingVersion = -1;
	private static int cachedBlockingCount;
	private static int blockingFrameTicker;

	private record Marker(int iconX, int iconY, BoMMappingStatus.Status status, EmiRecipe recipe) {
		boolean contains(int treeX, int treeY) {
			int left = iconX + HIT_OFFSET_X;
			int top = iconY + HIT_OFFSET_Y;
			return treeX >= left && treeX < left + HIT_WIDTH
				&& treeY >= top && treeY < top + HIT_HEIGHT;
		}
	}

	private BoMMappingOverlay() {}

	/**
	 * 在 BoMScreen 的缩放矩阵内绘制，传入的坐标即树坐标（与 EMI 自身节点绘制同一坐标系）。
	 */
	public static void render(GuiGraphics graphics, List<?> nodes, int treeMouseX, int treeMouseY) {
		MARKERS.clear();
		hoveredMarker = null;
		if (nodes == null || nodes.isEmpty()) {
			return;
		}

		var font = Minecraft.getInstance().font;
		for (Object entry : nodes) {
			if (!(entry instanceof BoMScreenNodeAccessor accessor)) {
				continue;
			}
			MaterialNode node = accessor.eap$getNode();
			// recipe 为 null 的叶节点是原料，本来就不需要样板。
			if (node == null || node.recipe == null) {
				continue;
			}
			BoMMappingStatus.Status status = BoMMappingStatus.of(node.recipe);
			if (status == BoMMappingStatus.Status.OK) {
				continue;
			}

			// 与 BoMScreen$Node#render 的物品格子定位保持一致：有配方的节点图标右移 11。
			int iconX = accessor.eap$getX() + 11 - 8 + accessor.eap$getMidOffset();
			int iconY = accessor.eap$getY() - 8;
			Marker marker = new Marker(iconX, iconY, status, node.recipe);
			MARKERS.add(marker);

			boolean hovered = marker.contains(treeMouseX, treeMouseY);
			if (hovered) {
				hoveredMarker = marker;
			}

			int color;
			if (status == BoMMappingStatus.Status.NO_PROVIDER_MAPPING) {
				color = hovered ? COLOR_BLOCKING_HOVER : COLOR_BLOCKING;
			} else {
				color = COLOR_UNENCODABLE;
			}
			graphics.drawString(font, "!", iconX + GLYPH_OFFSET_X, iconY + GLYPH_OFFSET_Y, color, true);
		}
	}

	/**
	 * 当前悬停标记的提示文本；无悬停时返回 null。
	 * 必须在缩放矩阵之外绘制，否则提示框会跟着树一起缩放。
	 */
	public static List<Component> hoveredTooltip() {
		Marker marker = hoveredMarker;
		if (marker == null) {
			return null;
		}
		if (marker.status() == BoMMappingStatus.Status.NO_PROVIDER_MAPPING) {
			ResourceLocation typeId = BoMMappingStatus.mappingKeyOf(marker.recipe());
			return List.of(
				Component.translatable("tooltip.extendedae_plus.bom_marker.no_mapping"),
				Component.translatable("tooltip.extendedae_plus.bom_marker.no_mapping.type",
					typeId == null ? "?" : typeId.toString()),
				Component.translatable("tooltip.extendedae_plus.bom_marker.no_mapping.action")
			);
		}
		return List.of(Component.translatable("tooltip.extendedae_plus.bom_marker.unencodable"));
	}

	/**
	 * 点击感叹号：缺映射的向服务端请求网络中的机器列表，弹出挑选界面（可搜索、可直接选机器）。
	 * 机器列表只有服务端知道，所以走一次往返，界面由 {@code MappingProvidersS2CPacket} 打开。
	 */
	public static boolean mouseClicked(Screen bomScreen, int treeMouseX, int treeMouseY) {
		for (Marker marker : MARKERS) {
			if (!marker.contains(treeMouseX, treeMouseY)) {
				continue;
			}
			Minecraft mc = Minecraft.getInstance();
			if (marker.status() == BoMMappingStatus.Status.NO_PROVIDER_MAPPING) {
				ResourceLocation typeId = BoMMappingStatus.mappingKeyOf(marker.recipe());
				if (typeId == null) {
					// 类型都认不出来时只能手填，退回完整的映射管理界面。
					mc.setScreen(new RecipeTypeMappingScreen(bomScreen, ""));
				} else {
					PacketDistributor.sendToServer(new RequestMappingProvidersC2SPacket(typeId.toString()));
				}
			} else if (mc.player != null) {
				mc.player.displayClientMessage(
					Component.translatable("message.extendedae_plus.bom_encode.unencodable_node"), true);
			}
			return true;
		}
		return false;
	}

	/**
	 * 整棵树里缺少供应器映射的配方数（按配方去重）。
	 * <p>
	 * 必须走完整的 {@link BoM#tree}，不能只数当前帧的标记：折叠起来的子树不在 BoMScreen 的
	 * 节点列表里，只数可见标记会让一键编码在存在隐藏缺映射节点时照样放行。
	 * <p>
	 * 逐帧调用，因此结果做短时缓存；映射表版本变化或目标切换时立即重算。
	 */
	public static int countBlocking() {
		var tree = BoM.tree;
		if (tree == null || tree.goal == null) {
			cachedBlockingGoal = null;
			return 0;
		}

		int version = ExtendedAEPatternUploadUtil.getMappingVersion();
		boolean stale = tree.goal != cachedBlockingGoal
				|| version != cachedBlockingVersion
				|| ++blockingFrameTicker >= BLOCKING_RECHECK_FRAMES;
		if (!stale) {
			return cachedBlockingCount;
		}

		blockingFrameTicker = 0;
		cachedBlockingGoal = tree.goal;
		cachedBlockingVersion = version;
		cachedBlockingCount = walkBlocking(tree.goal);
		return cachedBlockingCount;
	}

	private static int walkBlocking(MaterialNode goal) {
		Set<EmiRecipe> counted = new HashSet<>();
		Deque<MaterialNode> stack = new ArrayDeque<>();
		stack.push(goal);
		while (!stack.isEmpty()) {
			MaterialNode node = stack.pop();
			if (node.children != null) {
				for (MaterialNode child : node.children) {
					stack.push(child);
				}
			}
			if (node.recipe == null) {
				continue;
			}
			if (BoMMappingStatus.of(node.recipe) == BoMMappingStatus.Status.NO_PROVIDER_MAPPING) {
				counted.add(node.recipe);
			}
		}
		return counted.size();
	}

	/** 把屏幕坐标换算到 BoMScreen 的树坐标（复刻 BoMScreen#getHoveredStack 的换算）。 */
	public static int toTreeX(BoMScreen screen, double mouseX, float scale, double offX) {
		return (int) ((mouseX - screen.width / 2.0) / scale - offX);
	}

	public static int toTreeY(BoMScreen screen, double mouseY, float scale, double offY) {
		return (int) ((mouseY - screen.height / 2.0) / scale - offY);
	}
}
