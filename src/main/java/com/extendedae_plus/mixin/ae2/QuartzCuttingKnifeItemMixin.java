package com.extendedae_plus.mixin.ae2;

import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Nameable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import org.lwjgl.glfw.GLFW;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 AE2 石英切割刀添加：潜行 + 右键 指向世界中的方块/部件（如线缆）时，复制其名称到剪贴板。
 *
 * 设计要点（参考原类 QuartzCuttingKnifeItem 的写法）：
 * - 原本右键会打开 QuartzKnifeMenu。我们在客户端、潜行且命中方块时，优先拦截并复制名称。
 * - 名称优先取方块实体的自定义名（若实现 Nameable），否则使用方块显示名。
 * - 仅在客户端执行剪贴板操作；避免在服务端加载客户端类，使用 level.isClientSide() 的分支内访问 Minecraft 类。
 */
@Mixin(value = QuartzCuttingKnifeItem.class, remap = false)
public abstract class QuartzCuttingKnifeItemMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void eap$copyNameOnShiftRightClick(Level level, Player player, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!level.isClientSide()) {
            return;
        }
        if (player == null || !player.isCrouching()) {
            return;
        }
        // 仅在客户端分支访问 Minecraft 类，防止服务端类加载问题
        Minecraft mc = Minecraft.getInstance();
        HitResult hr = mc.hitResult;
        if (!(hr instanceof BlockHitResult bhr)) {
            return;
        }
        var pos = bhr.getBlockPos();
        var state = level.getBlockState(pos);
        if (state == null || state.isAir()) {
            return;
        }
        String name = null;
        BlockEntity be = level.getBlockEntity(pos);
        // 优先：若为 AE2 线缆总线，选择命中的具体 Part 名称
        if (be instanceof IPartHost partHost) {
            SelectedPart sel = partHost.selectPartWorld(bhr.getLocation());
            if (sel.part != null) {
                var stack = new net.minecraft.world.item.ItemStack(sel.part.getPartItem());
                name = stack.getHoverName().getString();
            } else if (sel.facade != null) {
                var stack = sel.facade.getItemStack();
                if (!stack.isEmpty()) {
                    name = stack.getHoverName().getString();
                }
            }
        }
        if (be instanceof Nameable nm) {
            var custom = nm.getCustomName();
            if (custom != null) {
                name = custom.getString();
            }
        }
        if (name == null || name.isBlank()) {
            name = state.getBlock().getName().getString();
        }
        boolean ok = tryCopyToClipboard(mc, name);
        player.displayClientMessage(Component.literal(ok
                ? ("已复制方块名: " + name)
                : "复制失败：整合包可能限制剪贴板或未聚焦窗口"), true);
        // 拦截默认行为，不再打开刀具界面
        ItemStack held = player.getItemInHand(hand);
        cir.setReturnValue(new InteractionResultHolder<>(InteractionResult.SUCCESS, held));
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void eap$copyNameOnShiftRightClickUseOn(UseOnContext context,
                                                    CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!level.isClientSide() || player == null || !player.isCrouching()) {
            return;
        }
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (state == null || state.isAir()) {
            return;
        }
        String name = null;
        BlockEntity be = level.getBlockEntity(pos);
        // 优先：若为 AE2 线缆总线，选择命中的具体 Part 名称（使用点击位置的世界坐标）
        if (be instanceof IPartHost partHost) {
            // 与 RenderBlockOutlineHook 相同，基于世界坐标选取部件
            SelectedPart sel = partHost.selectPartWorld(context.getClickLocation());
            if (sel.part != null) {
                var stack = new net.minecraft.world.item.ItemStack(sel.part.getPartItem());
                name = stack.getHoverName().getString();
            } else if (sel.facade != null) {
                var stack = sel.facade.getItemStack();
                if (!stack.isEmpty()) {
                    name = stack.getHoverName().getString();
                }
            }
        }
        if (be instanceof Nameable nm) {
            var custom = nm.getCustomName();
            if (custom != null) {
                name = custom.getString();
            }
        }
        if (name == null || name.isBlank()) {
            name = state.getBlock().getName().getString();
        }
        boolean ok = tryCopyToClipboard(Minecraft.getInstance(), name);
        player.displayClientMessage(Component.literal(ok
                ? ("已复制方块/部件名: " + name)
                : "复制失败：整合包可能限制剪贴板或未聚焦窗口"), true);
        // 拦截默认行为
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /**
     * 多级回退的剪贴板写入：
     * 1) KeyboardHandler.setClipboard
     * 2) GLFW.glfwSetClipboardString(Window handle)
     * 3) AWT 系统剪贴板（可能在某些整合包/无头环境不可用）
     */
    private static boolean tryCopyToClipboard(Minecraft mc, String text) {
        if (text == null) return false;
        // 确保在游戏主线程执行
        if (!mc.isSameThread()) {
            AtomicBoolean result = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            mc.execute(() -> {
                try {
                    result.set(doCopy(mc, text));
                } finally {
                    latch.countDown();
                }
            });
            try { latch.await(); } catch (InterruptedException ignored) {}
            return result.get();
        } else {
            return doCopy(mc, text);
        }
    }

    private static boolean doCopy(Minecraft mc, String text) {
        try {
            mc.keyboardHandler.setClipboard(text);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            // GLFW 路径 1：使用窗口句柄
            Window window = mc.getWindow();
            long handle = window == null ? 0L : window.getWindow();
            if (handle != 0L) {
                GLFW.glfwSetClipboardString(handle, text);
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            // GLFW 路径 2：使用当前上下文（部分整合包自定义窗口实现时更稳健）
            long current = GLFW.glfwGetCurrentContext();
            if (current != 0L) {
                GLFW.glfwSetClipboardString(current, text);
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            // 最后回退到 AWT（可能在无头环境不可用）
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
