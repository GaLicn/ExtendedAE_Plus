//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.extendedae_plus_gtladd.config;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.Configurable.Comment;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(
    id = "extendedae_plus_gtladd"
)
public final class ModConfig {
    public static ModConfig INSTANCE;
    private static final Object lock = new Object();
    @Configurable
    @Comment({"开启后，合成样板将优先自动上传到分子操纵者"})
    public boolean restrictCraftingPatternToMolecular = true;

    public static void init() {
        synchronized(lock) {
            if (INSTANCE == null) {
                INSTANCE = (ModConfig)Configuration.registerConfig(ModConfig.class, ConfigFormats.yaml()).getConfigInstance();
            }

        }
    }
}
