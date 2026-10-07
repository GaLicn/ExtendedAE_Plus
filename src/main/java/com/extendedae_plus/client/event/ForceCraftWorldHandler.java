package com.extendedae_plus.client.event;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.network.ForceCraftFromWorldC2SPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 世界方块强制下单组合键的原始输入入口。
 *
 * <p>命中判定统一委托 {@link ModKeybindings#matchesForceCraft(InputConstants.Key)}，
 * 主键、冲突上下文与修饰键均由标准 {@code KeyMapping} 承载，玩家在按键设置中改键后
 * 模组侧无须额外同步。冲突上下文取 {@code IN_GAME}，界面打开时判定自动失效。</p>
 *
 * <p>本处理器不接入原版取方块键的派发链，也不注入 {@code Minecraft#pickBlock}。
 * 1.21 分支已移除自建的取方块拉取路径，改由 AE2 无线终端自身承担；若在此处
 * 复用该路径，组合键命中后将同时触发 AE2 的拉取与本模组的下单，行为分叉。</p>
 *
 * <p>命中时由本处理器直接读取 {@code Minecraft#hitResult} 并向服务端发送下单请求，
 * 同时取消该次鼠标事件。取消使原版不再为该键累加点击计数，动作次数与物理按下
 * 严格一一对应。</p>
 */
@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public final class ForceCraftWorldHandler {
    private ForceCraftWorldHandler() {}

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        InputConstants.Key pressed = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        if (!ModKeybindings.matchesForceCraft(pressed)) {
            return;
        }

        // 命中方块时接管本次点击：取消后原版不再为取方块键累加点击计数，
        // 避免一次物理按下重复派发。未命中方块时不取消，保持该次点击的原有语义。
        if (sendForceCraft()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }
        // GLFW 对按住不放的键持续派发 GLFW_REPEAT（值为 2），鼠标不存在该行为。
        // 仅接受物理按下边沿，否则按住期间会反复触发下单。
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        InputConstants.Key pressed = InputConstants.Type.KEYSYM.getOrCreate(event.getKey());
        if (!ModKeybindings.matchesForceCraft(pressed)) {
            return;
        }

        sendForceCraft();
    }

    /**
     * 读取当前准星指向的方块并发送强制下单请求。
     *
     * @return true 表示已发送请求；准星未指向方块或玩家不在世界中时为 false
     */
    private static boolean sendForceCraft() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return false;
        }

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        BlockHitResult bhr = (BlockHitResult) hit;
        PacketDistributor.sendToServer(new ForceCraftFromWorldC2SPacket(
                bhr.getBlockPos(), bhr.getDirection(), bhr.getLocation()));
        return true;
    }
}
