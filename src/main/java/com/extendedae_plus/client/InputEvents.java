package com.extendedae_plus.client;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.MEStorageScreen;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageScreenAccessor;
import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.network.OpenCraftFromJeiC2SPacket;
import com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public final class InputEvents {
    private InputEvents() {}

    // 临时适配：在缺少 AE2 的 JEI 辅助类时，仅尝试从 JEI 提供的原生 ItemStack 获取；否则不处理。
    private static GenericStack toGenericStack(ITypedIngredient<?> typed) {
        try {
            Optional<ItemStack> maybe = typed.getItemStack();
            if (maybe.isPresent()) {
                ItemStack is = maybe.get();
                // 尝试使用 AE2 的通用构造（若不可用则返回 null）
                try {
                    return GenericStack.fromItemStack(is);
                } catch (Throwable ignored) {
                    return null;
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
            Optional<ITypedIngredient<?>> hovered = JeiRuntimeProxy.getIngredientUnderMouse(mouseX, mouseY);
            if (hovered.isEmpty()) {
                hovered = JeiRuntimeProxy.getIngredientUnderMouse();
            }
            if (hovered.isPresent()) {
                // 若 JEI 作弊模式开启，则放行给 JEI 处理（Shift+左键=一组）
                if (JeiRuntimeProxy.isJeiCheatModeEnabled()) {
                    return;
                }
                ITypedIngredient<?> typed = hovered.get();
                GenericStack stack = toGenericStack(typed);
                if (stack != null) {
                    // 发送到服务端：若网络有库存则拉取一组到空槽，否则若可合成则打开下单界面
                    ModNetwork.CHANNEL.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
                    // 消费此次点击，避免 JEI/原版对左键的其它处理
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // 中键：打开 AE 下单界面（保持原有功能）
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            // 优先在 JEI 配方界面基于坐标获取；若无，再从覆盖层/书签获取
            double mouseX = event.getMouseX();
            double mouseY = event.getMouseY();
            Optional<ITypedIngredient<?>> hovered = JeiRuntimeProxy.getIngredientUnderMouse(mouseX, mouseY);
            if (hovered.isEmpty()) {
                hovered = JeiRuntimeProxy.getIngredientUnderMouse();
            }
            if (hovered.isEmpty()) return;

            ITypedIngredient<?> typed = hovered.get();
            // 若 JEI 作弊模式开启，则放行给 JEI 处理（中键=一组）
            if (JeiRuntimeProxy.isJeiCheatModeEnabled()) {
                return;
            }
            GenericStack stack = toGenericStack(typed);
            if (stack == null) return;

            // 发送到服务端，让其验证并打开 CraftAmountMenu
            ModNetwork.CHANNEL.sendToServer(new OpenCraftFromJeiC2SPacket(stack));

            // 消费此次点击，避免 JEI/原版对中键的其它处理
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != GLFW.GLFW_KEY_F) return;

        // 仅当鼠标确实悬停在 JEI 配料上时触发
        Optional<ITypedIngredient<?>> hovered = JeiRuntimeProxy.getIngredientUnderMouse();
        if (hovered.isEmpty()) return;

        ITypedIngredient<?> typed = hovered.get();

        // 通用获取显示名称（兼容物品/流体等）
        String name = JeiRuntimeProxy.getTypedIngredientDisplayName(typed);
        if (name == null || name.isEmpty()) return;

        // 写入 AE2 终端的搜索框
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof MEStorageScreen<?> me) {
            try {
                MEStorageScreenAccessor acc = (MEStorageScreenAccessor) (Object) me;
                acc.eap$getSearchField().setValue(name);
                acc.eap$setSearchText(name); // 同步到 Repo 并刷新
                event.setCanceled(true);
                return;
            } catch (Throwable ignored) {
            }
        }
    }
}
