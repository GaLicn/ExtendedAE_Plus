package com.extendedae_plus.client;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.integration.modules.jei.GenericEntryStackHelper;
import com.extendedae_plus.compat.EmiHelper;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageScreenAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalAccessor;
import com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket;
import com.extendedae_plus.network.crafting.OpenCraftFromJeiC2SPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
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
            return;
        }
        if (!ModList.get().isLoaded("jei")) {
            return;
        }
        onMouseJei(event);
    }

    @SubscribeEvent
    public static void onMouseButtonReleasedPre(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!EmiHelper.isLoaded()) {
            return;
        }

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
        } else if (ModList.get().isLoaded("jei")) {
            onKeyJei(event, screen);
        }
    }

    // ---- EMI 路径（仅在 emi 已加载时调用） ----

    private static void onMouseEmi(ScreenEvent.MouseButtonPressed.Pre event) {
        // Shift + 左键：拉取或下单。严格查找（仅 EMI 侧边栏/provider 区域），
        // 避免劫持背包等普通槽位的点击；侧边栏栈上该操作由 EMI 原生占用（craftAll），自动让位。
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown()) {
            ItemStack hovered = EmiHelper.getSidebarIngredientUnderMouse(event.getMouseX(), event.getMouseY());
            if (!hovered.isEmpty()) {
                GenericStack stack = GenericStack.fromItemStack(hovered);
                if (stack != null) {
                    emiActionPressHandled = true;
                    ModNetwork.CHANNEL.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // 中键：打开 AE 下单界面。EMI 源码中不存在中键绑定，接管零冲突；
        // 默认 cheatMode=CREATIVE 不做检查，避免创造模式误判跳过。
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

    private static void onKeyEmi(ScreenEvent.KeyPressed.Pre event, Screen screen) {
        ItemStack hovered = EmiHelper.getIngredientUnderMouse();
        if (hovered.isEmpty()) return;
        fillSearch(screen, buildSearchText(hovered), event);
    }

    // ---- JEI 路径（仅在 jei 已加载时调用，保留原有行为） ----

    private static void onMouseJei(ScreenEvent.MouseButtonPressed.Pre event) {
        // 优先处理：Shift + 左键（拉取或下单）
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && Screen.hasShiftDown()) {
            try {
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
                    GenericStack stack = GenericEntryStackHelper.ingredientToStack(typed);
                    if (stack != null) {
                        ModNetwork.CHANNEL.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
                        event.setCanceled(true);
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 中键：打开 AE 下单界面（保持原有功能）
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            try {
                double mouseX = event.getMouseX();
                double mouseY = event.getMouseY();
                Optional<ITypedIngredient<?>> hovered = JeiRuntimeProxy.getIngredientUnderMouse(mouseX, mouseY);
                if (hovered.isEmpty()) {
                    hovered = JeiRuntimeProxy.getIngredientUnderMouse();
                }
                if (hovered.isEmpty()) return;

                ITypedIngredient<?> typed = hovered.get();
                if (JeiRuntimeProxy.isJeiCheatModeEnabled()) {
                    return;
                }
                GenericStack stack = GenericEntryStackHelper.ingredientToStack(typed);
                if (stack == null) return;

                ModNetwork.CHANNEL.sendToServer(new OpenCraftFromJeiC2SPacket(stack));
                event.setCanceled(true);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void onKeyJei(ScreenEvent.KeyPressed.Pre event, Screen screen) {
        try {
            // 坐标查询覆盖 JEI 位于物品列表侧或书签侧的历史记录。
            var minecraft = Minecraft.getInstance();
            double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
            double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
            Optional<ITypedIngredient<?>> hovered = JeiRuntimeProxy.getIngredientUnderMouse(mouseX, mouseY);
            if (hovered.isEmpty()) {
                hovered = JeiRuntimeProxy.getIngredientUnderMouse();
            }
            if (hovered.isEmpty()) return;

            String name = JeiRuntimeProxy.getTypedIngredientDisplayName(hovered.get());
            fillSearch(screen, name, event);
        } catch (Throwable ignored) {
        }
    }

    // ---- 共享逻辑 ----

    /** 中键=打开下单界面，其余（Shift+左键）=拉取或下单。 */
    private static void sendViewerAction(boolean openCraft, GenericStack stack) {
        if (openCraft) {
            ModNetwork.CHANNEL.sendToServer(new OpenCraftFromJeiC2SPacket(stack));
        } else {
            ModNetwork.CHANNEL.sendToServer(new PullFromJeiOrCraftC2SPacket(stack));
        }
    }

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

    private static void fillSearch(Screen screen, String name, ScreenEvent.KeyPressed.Pre event) {
        if (name == null || name.isEmpty()) return;
        if (screen instanceof MEStorageScreen<?> me) {
            try {
                MEStorageScreenAccessor acc = (MEStorageScreenAccessor) me;
                acc.eap$getSearchField().setValue(name);
                acc.eap$setSearchText(name);
                event.setCanceled(true);
                return;
            } catch (Throwable ignored) {
            }
        } else if (screen instanceof GuiExPatternTerminal<?> gpt) {
            try {
                GuiExPatternTerminalAccessor acc = (GuiExPatternTerminalAccessor) gpt;
                acc.getSearchOutField().setValue(name);
                event.setCanceled(true);
            } catch (Throwable ignored) {
            }
        }
    }
}
