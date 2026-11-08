package com.extendedae_plus.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * ExtendedAE Plus 快捷键定义
 *
 * <p>全部绑定统一使用标准 {@link KeyMapping} 描述冲突上下文、修饰键与主键类型，
 * 组合键判定交由 {@link KeyMapping#isActiveAndMatches(InputConstants.Key)} 完成。
 * 该做法参考 Just Enough Items 的组合键处理：以 {@code KeyMapping} 承载修饰键，
 * 命中判定不手工拆分主键与修饰键。玩家在按键设置中改键时，修饰键与主键由 Forge
 * 一并更新，模组侧无须额外同步。</p>
 */
public final class ModKeybindings {
    private ModKeybindings() {}

    /**
     * Ctrl+Q 快速创建样板快捷键。
     */
    public static final KeyMapping CREATE_PATTERN_KEY = new KeyMapping(
        "key.extendedae_plus.create_pattern",      // 翻译键
        KeyConflictContext.GUI,                     // 仅在 GUI 中生效
        KeyModifier.CONTROL,                        // Ctrl 修饰键
        InputConstants.Type.KEYSYM,                 // 键盘按键类型
        GLFW.GLFW_KEY_Q,                            // Q 键
        "key.categories.extendedae_plus"            // 分类
    );

    /**
     * 填充 JEI 物品名称到搜索框快捷键。
     */
    public static final KeyMapping FILL_SEARCH_KEY = new KeyMapping(
        "key.extendedae_plus.fill_search",         // 翻译键
        KeyConflictContext.GUI,                     // 仅在 GUI 中生效
        InputConstants.Type.KEYSYM,                 // 键盘按键类型
        GLFW.GLFW_KEY_F,                            // F 键（默认）
        "key.categories.extendedae_plus"            // 分类
    );

    /**
     * 世界方块强制下单绑定（默认 Shift + 鼠标中键）。
     *
     * <p>按住该组合键对准世界方块点击时，跳过「AE 网络是否已有该物品」的判定，
     * 直接打开下单界面；未按住时维持原有的「先拉取、无存量才下单」行为。</p>
     *
     * <p>冲突上下文取 {@link KeyConflictContext#IN_GAME}：该绑定只在世界中生效，
     * 界面打开时其 {@code isActive()} 为假，命中判定自然被排除，无须另行判断
     * {@code Minecraft#screen}。修饰键与主键均由 Forge 按键设置维护，玩家可将其
     * 改绑为任意修饰键与鼠标／键盘主键。</p>
     */
    public static final KeyMapping FORCE_CRAFT_KEY = new KeyMapping(
        "key.extendedae_plus.force_craft",
        KeyConflictContext.IN_GAME,
        KeyModifier.SHIFT,
        InputConstants.Type.MOUSE,
        GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
        "key.categories.extendedae_plus"
    );

    /** 强制下单组合键是否已按下且尚未被消费。 */
    private static boolean forceCraftPending;

    /** 记录一次强制下单组合键的物理按下。 */
    public static void markForceCraftPressed() {
        forceCraftPending = true;
    }

    /** 丢弃尚未被消费的强制下单标记。 */
    public static void clearForceCraft() {
        forceCraftPending = false;
    }

    /**
     * 消费强制下单标记。
     *
     * <p>取值为真后立即清零，保证一次物理按下至多触发一次强制下单。</p>
     *
     * @return true 表示本次 {@code pickBlock} 由强制下单组合键触发
     */
    public static boolean consumeForceCraft() {
        boolean pending = forceCraftPending;
        forceCraftPending = false;
        return pending;
    }

    /**
     * 判断一次物理输入是否完整命中强制下单组合键。
     *
     * <p>主键、冲突上下文与修饰键的判定全部委托 {@link KeyMapping#isActiveAndMatches}，
     * 与按键设置中显示的组合键保持一致，避免手工比对与 Forge 状态脱节。</p>
     */
    public static boolean matchesForceCraft(InputConstants.Key pressed) {
        return FORCE_CRAFT_KEY.isActiveAndMatches(pressed);
    }

    /**
     * 判断一次物理输入是否为强制下单组合键的主键。
     *
     * <p>仅比对主键，不校验修饰键。用于物理松开时回收标记：玩家可能先松开
     * Shift 再松开主键，此时修饰键已不成立，若仍要求组合键完整匹配则标记无法回收。</p>
     */
    public static boolean isForceCraftMainKey(InputConstants.Key pressed) {
        return pressed.equals(FORCE_CRAFT_KEY.getKey());
    }

    /**
     * 注册所有快捷键。
     *
     * @param event Forge 快捷键注册事件
     */
    public static void register(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(CREATE_PATTERN_KEY);
        event.register(FILL_SEARCH_KEY);
        event.register(FORCE_CRAFT_KEY);
    }
}
