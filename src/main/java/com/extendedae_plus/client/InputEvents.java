package com.extendedae_plus.client;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.MEStorageScreen;
import com.extendedae_plus.compat.JeiRuntimeCompat;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public final class InputEvents {
	private InputEvents() {}

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

	@SubscribeEvent
	public static void onMouseButtonPre(ScreenEvent.MouseButtonPressed.Pre event) {
		// 优先处理：Shift + 左键（拉取或下单）
		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown()) {
			double mouseX = event.getMouseX();
			double mouseY = event.getMouseY();
			Optional<?> hovered = JeiRuntimeCompat.getIngredientUnderMouse(mouseX, mouseY);
			if (hovered.isEmpty()) {
				hovered = JeiRuntimeCompat.getIngredientUnderMouse();
			}
			if (hovered.isPresent()) {
				if (JeiRuntimeCompat.isCheatModeEnabled()) {
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
			Optional<?> hovered = JeiRuntimeCompat.getIngredientUnderMouse(mouseX, mouseY);
			if (hovered.isEmpty()) {
				hovered = JeiRuntimeCompat.getIngredientUnderMouse();
			}
			if (hovered.isEmpty()) return;

			if (JeiRuntimeCompat.isCheatModeEnabled()) {
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
		// 检查是否按下了填充搜索框的快捷键
		if (!ModKeybindings.FILL_SEARCH_KEY.matches(event.getKeyCode(), event.getScanCode())) {
			return;
		}

		var screen = Minecraft.getInstance().screen;
		if (!(screen instanceof MEStorageScreen<?> || screen instanceof GuiExPatternTerminal<?>)) {
			return;
		}
		Optional<?> hovered = JeiRuntimeCompat.getIngredientUnderMouse();
		if (hovered.isEmpty()) {
			return;}

		Object typed = hovered.get();
		String name = JeiRuntimeCompat.getTypedIngredientDisplayName(typed);
		if (name == null || name.isEmpty()) {
			return;}
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
