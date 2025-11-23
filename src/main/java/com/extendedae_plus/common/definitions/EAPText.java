package com.extendedae_plus.common.definitions;

import appeng.core.localization.LocalizationEnum;

public enum EAPText implements LocalizationEnum {
    Accelerate("Entity Acceleration", Type.TOOLTIP),
    AccelerateEnabled("Accelerate target block entity ticks", Type.TOOLTIP),
    AccelerateDisabled("Do not accelerate target block entities", Type.TOOLTIP),
    AccelerateBlacklisted("Target is blacklisted", Type.TOOLTIP),

    RedstoneControl("Redstone control", Type.TOOLTIP),
    RedstoneControlEnabled("Control acceleration with redstone signal", Type.TOOLTIP),
    RedstoneControlDisabled("Ignore redstone signals", Type.TOOLTIP);

    private final String englishText;
    private final Type type;

    EAPText(String englishText, Type type) {
        this.englishText = englishText;
        this.type = type;
    }

    public String getEnglishText() {
        return this.englishText;
    }

    public String getTranslationKey() {
        return String.format("%s.%s.%s", this.type.root, "extendedae_plus", this.name());
    }

    private enum Type {
        GUI("gui"),
        TOOLTIP("gui.tooltips");

        private final String root;

        Type(String root) {
            this.root = root;
        }
    }
}
