package com.extendedae_plus.client;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.MEStorageScreen;
import com.extendedae_plus.compat.EmiHelper;
import com.extendedae_plus.compat.JeiRuntimeCompat;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageScreenAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalAccessor;
import com.extendedae_plus.network.OpenCraftFromJeiC2SPacket;
import com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public final class InputEvents {
	private InputEvents() {}

	/** EMI 路径：按下阶段已处理过动作键时，跳过松开阶段的重复发送。 */
	private static boolean emiActionPressHandled;

	@SubscribeEvent
	public static void onMouseButtonPre(ScreenEvent.MouseButtonPressed.Pre event) {
		// 按查看器来源分派：对应模组的代码只在已安装该模组时执行，
		// 避免另一模组的类在缺失时被加载/校验而触发 NoClassDefFoundError。
		if (EmiHelper.isLoaded()) {
			onMouseEmi(event);
		} else {
			onMouseJei(event);
		}
	}

	@SubscribeEvent
	public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
		if (!ModKeybindings.FILL_SEARCH_KEY.matches(event.getKeyCode(), event.getScanCode())) {
			return;
		}
		var screen = Minecraft.getInstance().screen;
		if (!(screen instanceof MEStorageScreen<?> || screen instanceof GuiExPatternTerminal<?>)) {
			return;
		}
		if (EmiHelper.isLoaded()) {
			onKeyEmi(event, screen);
		} else {
			onKeyJei(event, screen);
		}
	}

	// ---- EMI 路径（仅在 emi 已加载时调用） ----

	private static void onMouseEmi(ScreenEvent.MouseButtonPressed.Pre event) {
		// Shift + 左键：拉取或下单。
		// 不做作弊模式检查：EMI 的给予/合成只作用于它自己的侧边栏栈（那里事件到不了这里）；
		// 对终端网格等屏幕槽位 EMI 零介入，且默认 cheatMode=CREATIVE 会让创造模式误判跳过。
		// 使用严格查找（仅 EMI 侧边栏/provider 区域），避免劫持背包等普通槽位的点击。
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown()) {
			ItemStack hovered = EmiHelper.getSidebarIngredientUnderMouse(event.getMouseX(), event.getMouseY());
			if (!hovered.isEmpty()) {
				GenericStack stack = GenericStack.fromItemStack(hovered);
				if (stack != null) {
					emiActionPressHandled = true;
					PacketDistributor.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
					event.setCanceled(true);
					return;
				}
			}
		}

		// 中键：打开 AE 下单界面。
		// 不做作弊模式检查：EMI 全部点击行为均由可配置按键驱动，源码中不存在中键绑定，
		// 接管中键与 EMI 原生交互零冲突；且默认 cheatMode=CREATIVE 会让创造模式下误判跳过。
		// 同样使用严格查找，仅对 EMI 自有区域的条目生效。
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
			ItemStack hovered = EmiHelper.getSidebarIngredientUnderMouse(event.getMouseX(), event.getMouseY());
			if (hovered.isEmpty()) return;

			GenericStack stack = GenericStack.fromItemStack(hovered);
			if (stack == null) return;
			emiActionPressHandled = true;
			sendViewerAction(true, stack);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onMouseButtonReleasedPre(ScreenEvent.MouseButtonReleased.Pre event) {
		if (!EmiHelper.isLoaded()) return;

		// EMI 对侧边栏栈的"按下"会在 GLFW 层消费掉（本监听器收不到），
		// 但无绑定的按键会放行"松开"；因此中键下单 / Shift+左键拉取在此补一次处理。
		// 按下阶段若已发送（非 EMI 区域），由标志位跳过。
		boolean isMiddle = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
		boolean isShiftLeft = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown();
		if (!isMiddle && !isShiftLeft) return;
		if (emiActionPressHandled) {
			emiActionPressHandled = false;
			return;
		}
		ItemStack hovered = EmiHelper.getSidebarIngredientUnderMouse(event.getMouseX(), event.getMouseY());
		if (hovered.isEmpty()) return;

		GenericStack stack = GenericStack.fromItemStack(hovered);
		if (stack == null) return;
		sendViewerAction(isMiddle, stack);
		event.setCanceled(true);
	}

	/** 中键=打开下单界面，其余（Shift+左键）=拉取或下单。 */
	private static void sendViewerAction(boolean openCraft, GenericStack stack) {
		if (openCraft) {
			PacketDistributor.sendToServer(new OpenCraftFromJeiC2SPacket(stack));
		} else {
			PacketDistributor.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
		}
	}

	private static void onKeyEmi(ScreenEvent.KeyPressed.Pre event, Screen screen) {
		ItemStack hovered = EmiHelper.getIngredientUnderMouse();
		if (hovered.isEmpty()) return;
		fillSearch(screen, buildSearchText(hovered), event);
	}

	// ---- JEI 路径（仅在 jei 已加载时调用） ----

	private static void onMouseJei(ScreenEvent.MouseButtonPressed.Pre event) {
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown()) {
			Optional<?> hovered = JeiRuntimeCompat.getIngredientUnderMouse(event.getMouseX(), event.getMouseY());
			if (hovered.isEmpty()) {
				hovered = JeiRuntimeCompat.getIngredientUnderMouse();
			}
			if (hovered.isPresent()) {
				if (JeiRuntimeCompat.isCheatModeEnabled()) {
					return;
				}
				GenericStack stack = toGenericStack(hovered.get());
				if (stack != null) {
					PacketDistributor.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
					event.setCanceled(true);
					return;
				}
			}
		}

		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
			Optional<?> hovered = JeiRuntimeCompat.getIngredientUnderMouse(event.getMouseX(), event.getMouseY());
			if (hovered.isEmpty()) {
				hovered = JeiRuntimeCompat.getIngredientUnderMouse();
			}
			if (hovered.isEmpty()) return;
			if (JeiRuntimeCompat.isCheatModeEnabled()) return;
			GenericStack stack = toGenericStack(hovered.get());
			if (stack == null) return;
			PacketDistributor.sendToServer(new OpenCraftFromJeiC2SPacket(stack));
			event.setCanceled(true);
		}
	}

	private static void onKeyJei(ScreenEvent.KeyPressed.Pre event, Screen screen) {
		// 坐标查询覆盖 JEI 位于物品列表侧或书签侧的历史记录。
		var minecraft = Minecraft.getInstance();
		double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
		double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
		Optional<?> hovered = JeiRuntimeCompat.getIngredientUnderMouse(mouseX, mouseY);
		if (hovered.isEmpty()) {
			hovered = JeiRuntimeCompat.getIngredientUnderMouse();
		}
		if (hovered.isEmpty()) return;
		Object value = JeiRuntimeCompat.getTypedIngredientValue(hovered.get());
		if (value instanceof ItemStack stack) {
			fillSearch(screen, buildSearchText(stack), event);
			return;
		}
		// 非物品类型（流体等）暂不支持 @modid，退化为显示名
		String name = JeiRuntimeCompat.getTypedIngredientDisplayName(hovered.get());
		if (name == null || name.isEmpty()) return;
		fillSearch(screen, name, event);
	}

	// ---- 共享逻辑 ----

	/**
	 * Alt+F：填充 "@modid"（按模组过滤，对齐 EmiLink 与 AE2 原生 @ 搜索语法）；
	 * 普通 F：填充物品显示名。
	 */
	private static String buildSearchText(ItemStack stack) {
		if (Screen.hasAltDown()) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			return "@" + id.getNamespace();
		}
		return stack.getHoverName().getString();
	}

	private static GenericStack toGenericStack(Object typed) {
		Object value = JeiRuntimeCompat.getTypedIngredientValue(typed);
		if (value instanceof ItemStack stack && !stack.isEmpty()) {
			return GenericStack.fromItemStack(stack);
		}
		if (value instanceof FluidStack stack && !stack.isEmpty()) {
			return GenericStack.fromFluidStack(stack);
		}
		return null;
	}

	private static void fillSearch(Screen screen, String name, ScreenEvent.KeyPressed.Pre event) {
		if (screen instanceof MEStorageScreen<?> me) {
			try {
				MEStorageScreenAccessor acc = (MEStorageScreenAccessor) (Object) me;
				acc.eap$getSearchField().setValue(name);
				acc.eap$setSearchText(name);
				event.setCanceled(true);
				return;
			} catch (Throwable ignored) {
			}
		} else if (screen instanceof GuiExPatternTerminal<?> gpt) {
			try {
				GuiExPatternTerminalAccessor acc = (GuiExPatternTerminalAccessor) gpt;
				acc.getSearchField().setValue(name);
				event.setCanceled(true);
			} catch (Throwable ignored) {
			}
		}
	}
}
