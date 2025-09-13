package com.extendedae_plus.integration.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class ExtendedAEJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(ExtendedAEPlus.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRuntimeProxy.setRuntime(jeiRuntime);
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Register NBT-based subtype interpreter so JEI treats different multipliers as distinct items
        registration.registerSubtypeInterpreter(
                com.extendedae_plus.init.ModItems.ENTITY_SPEED_CARD.get(),
                (stack, context) -> String.valueOf(EntitySpeedCardItem.readMultiplier(stack))
        );
    }
}
