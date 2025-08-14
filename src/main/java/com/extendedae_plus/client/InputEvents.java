package com.extendedae_plus.client;

import java.util.Optional;

import org.lwjgl.glfw.GLFW;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.network.OpenCraftFromJeiC2SPacket;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jei.GenericEntryStackHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InputEvents {
    private InputEvents() {}

    @SubscribeEvent
    public static void onMouseButtonPre(ScreenEvent.MouseButtonPressed.Pre event) {
        // 只处理中键按下
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return;

        // 优先在 JEI 配方界面基于坐标获取；若无，再从覆盖层/书签获取
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        Optional<ITypedIngredient<?>> hovered = JeiRuntimeProxy.getIngredientUnderMouse(mouseX, mouseY);
        if (hovered.isEmpty()) {
            hovered = JeiRuntimeProxy.getIngredientUnderMouse();
        }
        if (hovered.isEmpty()) return;

        ITypedIngredient<?> typed = hovered.get();
        GenericStack stack = GenericEntryStackHelper.ingredientToStack(typed);
        if (stack == null) return;

        // 发送到服务端，让其验证并打开 CraftAmountMenu
        ModNetwork.CHANNEL.sendToServer(new OpenCraftFromJeiC2SPacket(stack));

        // 消费此次点击，避免 JEI/原版对中键的其它处理
        event.setCanceled(true);
    }
}
