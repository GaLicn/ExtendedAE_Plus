package com.extendedae_plus.client;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.MEStorageScreen;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageScreenAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalAccessor;
import com.extendedae_plus.network.OpenCraftFromJeiC2SPacket;
import com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.Optional;

public final class InputEvents {
	private InputEvents() {}

	private static Optional<?> getIngredientUnderMouse() {
		try {
			Class<?> cls = Class.forName("com.extendedae_plus.integration.jei.JeiRuntimeProxy");
			Method m = cls.getMethod("getIngredientUnderMouse");
			Object r = m.invoke(null);
			return (Optional<?>) r;
		} catch (Throwable ignored) {
			return Optional.empty();
		}
	}

	private static Optional<?> getIngredientUnderMouse(double mouseX, double mouseY) {
		try {
			Class<?> cls = Class.forName("com.extendedae_plus.integration.jei.JeiRuntimeProxy");
			Method m = cls.getMethod("getIngredientUnderMouse", double.class, double.class);
			Object r = m.invoke(null, mouseX, mouseY);
			return (Optional<?>) r;
		} catch (Throwable ignored) {
			return Optional.empty();
		}
	}

	private static boolean isJeiCheatModeEnabled() {
		try {
			Class<?> cls = Class.forName("com.extendedae_plus.integration.jei.JeiRuntimeProxy");
			Method m = cls.getMethod("isJeiCheatModeEnabled");
			Object r = m.invoke(null);
			return r instanceof Boolean b && b;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static String getTypedIngredientDisplayName(Object typed) {
		try {
			Class<?> cls = Class.forName("com.extendedae_plus.integration.jei.JeiRuntimeProxy");
			Method m = cls.getMethod("getTypedIngredientDisplayName", Object.class);
			Object r = m.invoke(null, typed);
			return r instanceof String s ? s : "";
		} catch (Throwable ignored) {
			return "";
		}
	}

	// 在缺少 AE2 的 JEI 辅助类时，仅尝试从 JEI 提供的原生 ItemStack 获取；否则不处理。
	private static GenericStack toGenericStack(Object typed) {
		try {
			// typed.getItemStack(): Optional<ItemStack>
			Method getItemStack = typed.getClass().getMethod("getItemStack");
			Object maybe = getItemStack.invoke(typed);
			if (maybe instanceof Optional<?> opt && opt.isPresent()) {
				Object val = opt.get();
				if (val instanceof ItemStack is) {
					try {
						return GenericStack.fromItemStack(is);
					} catch (Throwable ignored) {
						return null;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	@SubscribeEvent
	public static void onMouseButtonPre(ScreenEvent.MouseButtonPressed.Pre event) {
		// 优先处理：Shift + 左键（拉取或下单）
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown()) {
			double mouseX = event.getMouseX();
			double mouseY = event.getMouseY();
			Optional<?> hovered = getIngredientUnderMouse(mouseX, mouseY);
			if (hovered.isEmpty()) {
				hovered = getIngredientUnderMouse();
			}
			if (hovered.isPresent()) {
				if (isJeiCheatModeEnabled()) {
					return;
				}
				Object typed = hovered.get();
				GenericStack stack = toGenericStack(typed);
				if (stack != null) {
					PacketDistributor.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
					event.setCanceled(true);
					return;
				}
			}
		}

		// 中键：打开 AE 下单界面（保持原有功能）
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
			double mouseX = event.getMouseX();
			double mouseY = event.getMouseY();
			Optional<?> hovered = getIngredientUnderMouse(mouseX, mouseY);
			if (hovered.isEmpty()) {
				hovered = getIngredientUnderMouse();
			}
			if (hovered.isEmpty()) return;

			if (isJeiCheatModeEnabled()) {
				return;
			}
			Object typed = hovered.get();
			GenericStack stack = toGenericStack(typed);
			if (stack == null) return;

			PacketDistributor.sendToServer(new OpenCraftFromJeiC2SPacket(stack));
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
		if (event.getKeyCode() != GLFW.GLFW_KEY_F) return;

		Optional<?> hovered = getIngredientUnderMouse();
		if (hovered.isEmpty()) return;

		Object typed = hovered.get();
		String name = getTypedIngredientDisplayName(typed);
		if (name == null || name.isEmpty()) return;

		var screen = Minecraft.getInstance().screen;
		if (screen instanceof MEStorageScreen<?> me) {
			try {
				MEStorageScreenAccessor acc = (MEStorageScreenAccessor) (Object) me;
				acc.eap$getSearchField().setValue(name);
				acc.eap$setSearchText(name);
				event.setCanceled(true);
				return;
			} catch (Throwable ignored) {
			}
		}else if (screen instanceof GuiExPatternTerminal<?> gpt) {
			try {
				GuiExPatternTerminalAccessor acc = (GuiExPatternTerminalAccessor) gpt;
				acc.getSearchField().setValue(name);
				event.setCanceled(true);
			}catch (Throwable ignored) {}
		}
	}
}
