package com.extendedae_plus;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ExampleMod.MOD_ID)
public final class ExampleMod {
    public static final String MOD_ID = "extendedae_plus";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public ExampleMod() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like registries and resources) may still be uninitialized.
        // Proceed with mild caution.
        LOGGER.info("ExtendedAE Plus mod initialized! Pattern provider slots will be increased to 108.");
    }
}
