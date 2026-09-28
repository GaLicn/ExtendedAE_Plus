package com.extendedae_plus.client;

import com.extendedae_plus.ExtendedAEPlus;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class ModKeybindings {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            ExtendedAEPlus.id("key.categories.extendedae_plus"));

    private ModKeybindings() {
    }

    public static final KeyMapping CREATE_PATTERN_KEY = new KeyMapping(
            "key.extendedae_plus.create_pattern", KeyConflictContext.GUI, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, CATEGORY);

    public static final KeyMapping FILL_SEARCH_KEY = new KeyMapping(
            "key.extendedae_plus.fill_search", KeyConflictContext.GUI, KeyModifier.NONE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
}
