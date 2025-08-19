package com.extendedae_plus.mixin.ae2;

import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 为 AE2 石英切割刀添加：潜行 + 右键 指向世界中的方块/部件（如线缆）时，复制其名称到剪贴板。
 * <p>
 * 设计要点（参考原类 QuartzCuttingKnifeItem 的写法）：
 * - 原本右键会打开 QuartzKnifeMenu。我们在客户端、潜行且命中方块时，优先拦截并复制名称。
 * - 名称优先取方块实体的自定义名（若实现 Nameable），否则使用方块显示名。
 * - 仅在客户端执行剪贴板操作；避免在服务端加载客户端类，使用 level.isClientSide() 的分支内访问 Minecraft 类。
 */
@Mixin(value = QuartzCuttingKnifeItem.class)
public abstract class QuartzCuttingKnifeItemMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAEPlus");

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
        if (state == null || state.isAir()) return;

        // 获取方块名称
        String name = eap$getBlockName(level, pos, hr.getLocation());

        // 复制到剪贴板并反馈
        boolean success = eap$tryCopyToClipboard(Minecraft.getInstance(), name);
        player.displayClientMessage(Component.literal(success
                ? ("已复制方块/部件名: " + name)
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
        if (state.isAir()) return;

        // 获取方块名称
        String name = eap$getBlockName(level, pos, context.getClickLocation());

        // 复制到剪贴板并反馈
        boolean success = eap$tryCopyToClipboard(Minecraft.getInstance(), name);
        player.displayClientMessage(Component.literal(success
                ? ("已复制方块/部件名: " + name)
                : "复制失败：整合包可能限制剪贴板或未聚焦窗口"), true);

        // 拦截默认行为
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /**
     * 获取方块或部件的名称，优先级：自定义名称 > AE2 部件 > GregTech 配方翻译 > 方块名称
     */
    @Unique
    private String eap$getBlockName(Level level, BlockPos pos, Vec3 clickLocation) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);

        // 1. 自定义名称
        if (blockEntity instanceof Nameable nameable && nameable.getCustomName() != null) {
            return nameable.getCustomName().getString();
        }

        // 2. AE2 部件
        String ae2Name = eap$handleAE2Block(blockEntity, clickLocation);
        if (ae2Name != null && !ae2Name.isBlank()) {
            return ae2Name;
        }

        // 3. GregTech CEu 配方翻译
        String gtceuName = eap$handleGTCEuBlock(blockEntity);
        if (gtceuName != null && !gtceuName.isBlank()) {
            return gtceuName;
        }

        // 4. 方块名称
        return state.getBlock().getName().getString();
    }

    /**
     * 处理 GregTech CEu 方块，获取配方翻译名
     */
    @Unique
    private String eap$handleGTCEuBlock(BlockEntity blockEntity) {
        if (blockEntity instanceof MetaMachineBlockEntity meta) {
            if (meta.metaMachine instanceof WorkableElectricMultiblockMachine workable) {
                String recipeName = workable.getRecipeType().toString();
                // e.g., gtceu.cracker
                String translationKey = "gtceu." + recipeName.replace("gtceu:","");
                // 客户端直接使用 I18n
                return I18n.get(translationKey, recipeName); // e.g., 裂化机
            }
        }
        return null;
    }

    /**
     * 处理 AE2 方块，获取部件或外观名称
     */
    @Unique
    private String eap$handleAE2Block(BlockEntity blockEntity, Vec3 clickLocation) {
        if (blockEntity instanceof IPartHost partHost) {
            SelectedPart sel = partHost.selectPartWorld(clickLocation);
            if (sel.part != null) {
                ItemStack stack = new ItemStack(sel.part.getPartItem());
                return stack.getHoverName().getString();
            } else if (sel.facade != null) {
                ItemStack stack = sel.facade.getItemStack();
                if (!stack.isEmpty()) {
                    return stack.getHoverName().getString();
                }
            }
        }
        return null;
    }

    /**
     * 多级回退的剪贴板写入：
     * 1) KeyboardHandler.setClipboard
     * 2) GLFW.glfwSetClipboardString(Window handle)
     * 3) AWT 系统剪贴板（可能在某些整合包/无头环境不可用）
     */
    @Unique
    private static boolean eap$tryCopyToClipboard(Minecraft mc, String text) {
        if (text == null || text.isBlank()) return false;
        // 确保在游戏主线程执行
        if (!mc.isSameThread()) {
            AtomicBoolean result = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            mc.execute(() -> {
                try {
                    result.set(eap$doCopy(mc, text));
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                LOGGER.error("剪贴板复制线程中断: {}", e.getMessage());
            }
            return result.get();
        } else {
            return eap$doCopy(mc, text);
        }
    }

    @Unique
    private static boolean eap$doCopy(Minecraft mc, String text) {
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
