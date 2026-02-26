package com.extendedae_plus.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * ExtendedAE Plus 快捷键定义
 */
public final class ModKeybindings {
    private ModKeybindings() {}

    /**
     * Ctrl+Q 快速创建样板快捷键
     */
    public static final KeyMapping CREATE_PATTERN_KEY = new KeyMapping(
        "key.extendedae_plus.create_pattern",      // 翻译键
        KeyConflictContext.GUI,                     // 仅在GUI中生效
        KeyModifier.CONTROL,                        // Ctrl 修饰键
        InputConstants.Type.KEYSYM,                 // 键盘按键类型
        GLFW.GLFW_KEY_Q,                           // Q 键
        "key.categories.extendedae_plus"           // 分类
    );

    /**
     * 注册所有快捷键
     *
     * @param event Forge快捷键注册事件
     */
    public static void register(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(CREATE_PATTERN_KEY);
    }
}
