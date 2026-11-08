package com.extendedae_plus.compat.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import appeng.api.stacks.GenericStack;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * EMI 集成辅助（直接引用 dev.emi API，与 {@code JeiRuntimeProxy} 对应）。
 *
 * <p>本类整体暴露 dev.emi 类型，未安装 EMI 时加载即失败。因此调用方（{@code InputEvents}、
 * {@code EmiCtrlQHandler}、{@code CtrlQPatternKeyHandler}）的模组判定必须经
 * {@code ModCheckUtils.isLoaded(MODID_EMI)} 在外部完成，本类不提供也不参与该判定。</p>
 *
 * <p>对外方法的签名与局部变量仅使用 AE2 类型，不出现 dev.emi：JVM 在方法首次执行前校验其
 * 字节码，若调用方方法体中出现 dev.emi 局部变量，校验阶段即须解析该类，方法首行的模组守卫
 * 无法拦截（校验先于方法体执行），未安装 EMI 时会抛 {@link NoClassDefFoundError}。</p>
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
	 * 严格模式：仅查找 EMI 自有区域的悬浮栈，保留化学品等非物品、非流体的栈。
	 * 物品路径无法表达这些栈，点击类交互必须使用本方法。
	 */
	@Nullable
	public static EmiStack getSidebarStackUnderMouse(double mouseX, double mouseY) {
		try {
			return toEmiStack(EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false));
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * 严格模式的门面版本：内部完成 EmiStack → GenericStack 转换，签名与局部变量均不含 dev.emi。
	 *
	 * <p>调用方方法体若直接持有 {@code EmiStack} 局部变量，该类型会写入其 StackMapTable；
	 * JVM 在进入方法体之前校验整个方法，方法首行的模组守卫因此无法拦截缺失类的解析。
	 * 经本方法中转后，调用方的字节码不再出现 dev.emi，校验只在本类内部发生。</p>
	 */
	@Nullable
	public static GenericStack getSidebarGenericStackUnderMouse(double mouseX, double mouseY) {
		return EmiStackCompat.toGenericStack(getSidebarStackUnderMouse(mouseX, mouseY));
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

	/** 从交互结果取悬浮栈本体，不做物品化处理，因此化学品等自定义栈得以保留。 */
	@Nullable
	private static EmiStack toEmiStack(@Nullable EmiStackInteraction interaction) {
		if (interaction == null || interaction.isEmpty()) {
			return null;
		}
		try {
			var stacks = interaction.getStack().getEmiStacks();
			if (stacks == null || stacks.isEmpty()) {
				return null;
			}
			EmiStack stack = stacks.get(0);
			if (stack == null || stack.isEmpty()) {
				return null;
			}
			// 交互层可能未携带数量（收藏项等），此时回退到 EmiIngredient 自身的数量。
			if (stack.getAmount() <= 0) {
				stack = stack.copy().setAmount(Math.max(1, interaction.getStack().getAmount()));
			}
			return stack;
		} catch (Throwable ignored) {
			return null;
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
			ItemStack item = stacks.get(0).getItemStack();
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
