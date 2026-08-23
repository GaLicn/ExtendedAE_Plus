package com.extendedae_plus.compat;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * EMI 集成辅助（直接引用 dev.emi API，与 {@link JeiRuntimeCompat} 对应）。
 * 仅在 ModList 确认 emi 已加载时调用；未安装 EMI 时触及本类中的 EMI 调用会因类解析失败抛错。
 */
public final class EmiHelper {
	private static Boolean loaded;

	private EmiHelper() {}

	public static boolean isLoaded() {
		if (loaded == null) {
			var modList = ModList.get();
			loaded = modList != null && modList.isLoaded("emi");
		}
		return loaded;
	}

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
