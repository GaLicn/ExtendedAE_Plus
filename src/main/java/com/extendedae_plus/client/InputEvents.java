package com.extendedae_plus.client;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.core.AEConfig;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.integration.RecipeViewer.RecipeViewerHelper;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageScreenAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalAccessor;
import com.extendedae_plus.network.C2SPacketTargetKeyTriggered;
import com.extendedae_plus.network.OpenCraftFromJeiC2SPacket;
import com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public final class InputEvents {
	private InputEvents() {}

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (Minecraft.getInstance().screen == null) return;
        // 优先处理：Shift + 左键（拉取或下单）
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown() &&
                !RecipeViewerHelper.isCheatMode()) {
            List<GenericStack> stacks = RecipeViewerHelper.getHoveredStacks();
            GenericStack stack = stacks.isEmpty() ? null : stacks.getFirst();
            if (stack == null) return;
            PacketDistributor.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
        }

        // 中键：打开 AE 下单界面（保持原有功能）
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            // 优先在 JEI 配方界面基于坐标获取；若无，再从覆盖层/书签获取
            List<GenericStack> stacks = RecipeViewerHelper.getHoveredStacks();
            GenericStack stack = stacks.isEmpty() ? null : stacks.getFirst();
            if (stack == null) return;

			PacketDistributor.sendToServer(new OpenCraftFromJeiC2SPacket(stack));
			event.setCanceled(true);
		}
	}

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_F) {
            // 仅当鼠标确实悬停在 JEI 配料上时触发
            // 大概会在一格有多个(?)stack的时候出bug, 但是真的会有那种时候吗?
            GenericStack stack = RecipeViewerHelper.getHoveredStacks().getFirst();
            if (stack == null) return;
            String name = stack.what().getDisplayName().getString();

            // 写入 AE2 终端的搜索框
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof MEStorageScreen<?> me) {
                try {
                    // 如果用EMI搜索框
                    if (AEConfig.instance().isUseExternalSearch()) RecipeViewerHelper.setSearchText(name);
                    else {
                        MEStorageScreenAccessor acc = (MEStorageScreenAccessor) me;
                        acc.eap$getSearchField().setValue(name);
                        acc.eap$setSearchText(name); // 同步到 Repo 并刷新
                    }
                    event.setCanceled(true);
                } catch (Throwable ignored) {
                }
            } else if (screen instanceof GuiExPatternTerminal<?> gpt) {
                try {
                    if (AEConfig.instance().isUseExternalSearch()) RecipeViewerHelper.setSearchText(name);
                    else {
                        GuiExPatternTerminalAccessor acc = (GuiExPatternTerminalAccessor) gpt;
                        acc.getSearchField().setValue(name);
                    }
                    event.setCanceled(true);
                } catch (Throwable ignored) {
                }
            }
        } else if (event.getKeyCode() == GLFW.GLFW_KEY_LEFT_CONTROL)
            PacketDistributor.sendToServer(new C2SPacketTargetKeyTriggered(C2SPacketTargetKeyTriggered.KeyType.CTRL_DOWN));
    }

    @SubscribeEvent
    public static void onKeyReleasePre(ScreenEvent.KeyReleased.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_LEFT_CONTROL)
            PacketDistributor.sendToServer(new C2SPacketTargetKeyTriggered(C2SPacketTargetKeyTriggered.KeyType.CTRL_UP));
    }
}
