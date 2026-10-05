package com.extendedae_plus.client.event;

import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.mixin.minecraft.accessor.MinecraftPickBlockAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 世界方块强制下单组合键的原始输入入口。
 *
 * <p>命中判定统一委托 {@link ModKeybindings#matchesForceCraft(InputConstants.Key)}，
 * 主键、冲突上下文与修饰键均由标准 {@code KeyMapping} 承载，玩家在按键设置中改键后
 * 模组侧无须额外同步。冲突上下文取 {@code IN_GAME}，界面打开时判定自动失效，
 * 无须另行检查当前是否有屏幕。</p>
 *
 * <p>本处理器不把动作挂在原版取方块键的派发链上。Forge 的
 * {@code KeyMappingLookup#getAll} 一旦在某个修饰键分组中命中绑定便立即返回，
 * 不再查询无修饰键分组；按住修饰键时原版取方块绑定因此收不到该次点击，
 * {@code Minecraft#handleKeybinds} 中消费取方块键的循环不会执行，也就不会产生
 * {@code pickBlock} 调用。若仍依赖该路径，组合键命中后将没有任何反应。</p>
 *
 * <p>因此鼠标入口在组合键完整命中时自行发起一次 {@code pickBlock}，并取消该次鼠标事件。
 * 原版对该键的派发链以循环消费点击计数，计数累积时会重复调用 {@code pickBlock}，
 * 一次物理按下可能拉取多组物品；取消事件使该计数不再累加，动作次数与物理按下
 * 严格一一对应。</p>
 */
public final class ForceCraftKeyHandler {
    private ForceCraftKeyHandler() {}

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        InputConstants.Key pressed = InputConstants.Type.MOUSE.getOrCreate(event.getButton());

        if (event.getAction() == GLFW.GLFW_RELEASE) {
            // 组合键未命中方块时不会产生 pickBlock，标记须在物理松开时回收，
            // 否则将残留并作用于下一次普通中键点击。松开判定不校验修饰键：
            // 玩家可能先松开 Shift 再松开主键，此时组合键已不成立。
            if (ModKeybindings.isForceCraftMainKey(pressed)) {
                ModKeybindings.clearForceCraft();
            }
            return;
        }

        if (event.getAction() != GLFW.GLFW_PRESS || !ModKeybindings.matchesForceCraft(pressed)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 先接管本次点击，再自行执行一次取方块。取消后原版不再为本次点击累加
        // 点击计数，既避免因修饰键命中分组而完全不派发，也避免循环重复派发。
        // 标记由 PickFromWirelessMixin 在 pickBlock 内消费。
        event.setCanceled(true);
        ModKeybindings.markForceCraftPressed();
        ((MinecraftPickBlockAccessor) mc).extendedae_plus$pickBlock();
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }
        InputConstants.Key pressed = InputConstants.Type.KEYSYM.getOrCreate(event.getKey());

        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (ModKeybindings.isForceCraftMainKey(pressed)) {
                ModKeybindings.clearForceCraft();
            }
            return;
        }

        // GLFW 对按住不放的键持续派发 GLFW_REPEAT（值为 2），鼠标不存在该行为。
        // 仅接受物理按下边沿，否则按住期间会反复触发下单。
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (!ModKeybindings.matchesForceCraft(pressed)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 键盘主键不与方块交互共用同一次点击，原版不会因它调用取方块，
        // 须主动走一次；标记由 PickFromWirelessMixin 在该方法内消费。
        ModKeybindings.markForceCraftPressed();
        ((MinecraftPickBlockAccessor) mc).extendedae_plus$pickBlock();
    }
}
