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
}

