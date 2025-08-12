package com.extendedae_plus.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ModConfigs {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.IntValue PAGE_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("extendedae_plus");
        PAGE_MULTIPLIER = builder
                .comment(
                        "扩展样板供应器总槽位容量的倍率。",
                        "基础为36，每页仍显示36格，倍率会增加总页数/总容量。",
                        "建议范围 1-16")
                .defineInRange("pageMultiplier", 1, 1, 64);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    private ModConfigs() {}
}
