package com.extendedae_plus.client;

import com.extendedae_plus.ExtendedAEPlus;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * ExtendedAE Plus 快捷键定义
 */
public final class ModKeybindings {
	private ModKeybindings() {
	}

	/**
	 * Ctrl+Q 快速创建样板快捷键
	 */
	public static final KeyMapping CREATE_PATTERN_KEY = new KeyMapping(
		"key.extendedae_plus.create_pattern",
		KeyConflictContext.GUI,
		KeyModifier.CONTROL,
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_Q,
		"key.categories.extendedae_plus"
	);

	/**
	 * 填充JEI物品名称到搜索框快捷键
	 */
	public static final KeyMapping FILL_SEARCH_KEY = new KeyMapping(
		"key.extendedae_plus.fill_search",
		KeyConflictContext.GUI,
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_F,
		"key.categories.extendedae_plus"
	);

	/**
	 * 世界方块强制下单绑定（默认 Shift + 鼠标中键）。
	 *
	 * <p>按住该组合键对准世界方块点击时，跳过「AE 网络是否已有该物品」的判定，
	 * 直接打开下单界面。</p>
	 *
	 * <p>冲突上下文取 {@link KeyConflictContext#IN_GAME}：该绑定只在世界中生效，
	 * 界面打开时其 {@code isActive()} 为假，命中判定自然被排除，无须另行判断
	 * {@code Minecraft#screen}。修饰键与主键均由 NeoForge 按键设置维护，玩家可将其
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

	/**
	 * 判断一次物理输入是否完整命中强制下单组合键。
	 *
	 * <p>主键、冲突上下文与修饰键的判定全部委托 {@link KeyMapping#isActiveAndMatches}，
	 * 与按键设置中显示的组合键保持一致，避免手工比对与 NeoForge 状态脱节。</p>
	 */
	public static boolean matchesForceCraft(InputConstants.Key pressed) {
		return FORCE_CRAFT_KEY.isActiveAndMatches(pressed);
	}
}

