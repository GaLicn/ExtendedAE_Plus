package com.extendedae_plus.compat.emi;

import appeng.api.stacks.GenericStack;
import com.extendedae_plus.compat.jei.JeiRuntimeCompat;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * EMI 集成辅助（直接引用 dev.emi API，与 {@link JeiRuntimeCompat} 对应）。
 *
 * <p>本类含有 dev.emi 类型引用，任何方法调用都会触发类链接；因此「EMI 是否加载」的判定
 * 不得由本类承担，调用方须先经 {@link com.extendedae_plus.util.ModCheckUtils#isLoaded(String)}
 * 确认后再进入本类。</p>
 */
public final class EmiHelper {

	private EmiHelper() {}

	/** 获取鼠标悬浮的物品（GUI 缩放坐标），无悬浮或非物品时返回 {@link ItemStack#EMPTY}。 */
	public static ItemStack getIngredientUnderMouse() {
		try {
			// 单参重载使用 EMI 内部记录的鼠标状态，覆盖收藏栏等全部侧边栏空间
			ItemStack item = toItemStack(EmiApi.getHoveredStack(true));
			if (!item.isEmpty()) {
				return item;
			}
			return getIngredientUnderMouse(getGuiMouseX(), getGuiMouseY());
		} catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	public static ItemStack getIngredientUnderMouse(double mouseX, double mouseY) {
		ItemStack item = lookupByCoords(mouseX, mouseY);
		if (!item.isEmpty()) {
			return item;
		}
		return toItemStack(EmiApi.getHoveredStack(true));
	}

	/**
	 * 严格模式：仅查找 EMI 自有区域（收藏栏、搜索/可合成等侧边栏面板、配方槽 provider）的悬浮栈，
	 * 并直接转换为 AE2 栈。
	 *
	 * <p>用于点击类交互（Shift+左键拉取 / 中键下单）。不取 {@code getItemStack()} 中间态：
	 * 流体与 Mekanism 化学品的下单依赖原始栈的 key（{@code Fluid} / {@code Chemical}），
	 * 只取物品栈会在非物品条目上退化为空，使这些条目无法下单。</p>
	 *
	 * <p>返回类型为 AE2 的 {@link GenericStack}，调用方不接触任何 EMI 类型。</p>
	 *
	 * @return 悬浮条目对应的 AE2 栈；无悬浮、非 EMI 自有区域或类型不受支持时返回 null
	 */
	@Nullable
	public static GenericStack getSidebarGenericStackUnderMouse(double mouseX, double mouseY) {
		return EmiRecipeCompat.toGenericStack(getSidebarStackUnderMouse(mouseX, mouseY));
	}

	/**
	 * 严格模式：仅查找 EMI 自有区域的悬浮栈，保留原始栈类型（实际为 EmiStack）。
	 *
	 * <p>返回类型声明为 {@link Object} 以避免方法签名直接引用 EMI 类型；
	 * 仅在包内使用，由 {@link #getSidebarGenericStackUnderMouse} 完成转换。</p>
	 *
	 * @return 悬浮的 EMI 栈；无悬浮或非 EMI 自有区域时返回 null
	 */
	@Nullable
	private static Object getSidebarStackUnderMouse(double mouseX, double mouseY) {
		try {
			EmiStackInteraction interaction = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
			if (interaction == null || interaction.isEmpty()) {
				return null;
			}
			var stacks = interaction.getStack().getEmiStacks();
			return stacks == null || stacks.isEmpty() ? null : stacks.getFirst();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static ItemStack lookupByCoords(double mouseX, double mouseY) {
		try {
			ItemStack item = toItemStack(EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false));
			if (!item.isEmpty()) {
				return item;
			}
			// 收藏栏等侧边栏空间可能不被 EmiApi 覆盖，回退到内部屏幕管理器，
			// 并按 EMI 自行记录的鼠标坐标再试一次（与 EmiLink 的多路查找一致）。
			item = toItemStack(EmiScreenManager.getHoveredStack((int) mouseX, (int) mouseY, false));
			if (!item.isEmpty()) {
				return item;
			}
			item = toItemStack(EmiScreenManager.getHoveredStack(
					EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY, false));
			return item;
		} catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	/** EMI 作弊模式是否开启（开启时不劫持点击/按键）。 */
	public static boolean isCheatModeEnabled() {
		try {
			return EmiApi.isCheatMode();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static ItemStack toItemStack(@Nullable EmiStackInteraction interaction) {
		if (interaction == null || interaction.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try {
			// getStack() 返回 EmiIngredient（收藏项等包装类型），统一拆解取第一个栈
			var stacks = interaction.getStack().getEmiStacks();
			if (stacks == null || stacks.isEmpty()) {
				return ItemStack.EMPTY;
			}
			ItemStack item = stacks.getFirst().getItemStack();
			return item == null ? ItemStack.EMPTY : item;
		} catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	private static double getGuiMouseX() {
		var minecraft = Minecraft.getInstance();
		return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
	}

	private static double getGuiMouseY() {
		var minecraft = Minecraft.getInstance();
		return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
	}
}
