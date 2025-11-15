package com.extendedae_plus.integration.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.util.ModCheckUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.nbt.CompoundTag;
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
                ModItems.ENTITY_SPEED_CARD.get(),
                (stack, context) -> String.valueOf(EntitySpeedCardItem.readMultiplier(stack))
        );

        registration.registerSubtypeInterpreter(
                ModItems.BASIC_CORE.get(),
                (stack, context) -> {
                    CompoundTag tag = stack.getTag();
                    if (tag == null || !tag.contains("core_type") || !tag.contains("core_stage")) {
                        return "untyped";
                    }
                    int type = tag.getInt("core_type");
                    int stage = tag.getInt("core_stage");

                    if (!isCoreTypeAvailable(type)) {
                        return "hidden"; // JEI 忽略
                    }
                    return type + "_" + stage;
                }
        );
    }

    private boolean isCoreTypeAvailable(int typeId) {
        return switch (typeId) {
            case 0, 1 -> true; // storage, spatial
            case 2 -> ModCheckUtils.isAppfluxLoading();
            case 3 -> ModCheckUtils.isAAELoading();
            default -> false;
        };
    }
}
