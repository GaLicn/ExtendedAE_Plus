package com.extendedae_plus.integration.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.item.BasicCoreItem;
import com.extendedae_plus.util.ModCheckUtils;
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
        // Entity Speed Card
        registration.registerSubtypeInterpreter(
                ModItems.ENTITY_SPEED_CARD.get(),
                (stack, ctx) -> String.valueOf(EntitySpeedCardItem.readMultiplier(stack))
        );

        // Basic Core – 使用 CustomModelData + core_stage
        registration.registerSubtypeInterpreter(
                ModItems.BASIC_CORE.get(),
                (stack, ctx) -> {
                    if (!BasicCoreItem.isTyped(stack)) {
                        return "untyped";
                    }

                    BasicCoreItem.CoreType type = BasicCoreItem.getType(stack).orElse(null);
                    if (type == null) {
                        return "untyped";
                    }

                    int stage = BasicCoreItem.getStage(stack);

                    // 依赖检查
                    if (!isCoreTypeAvailable(type.id)) {
                        return "hidden"; // JEI 隐藏
                    }

                    return type.id + "_" + stage;  // 如 "0_1", "1_4"
                }
        );
    }

    private boolean isCoreTypeAvailable(int typeId) {
        return switch (typeId) {
            case 1, 2 -> true;                     // storage, spatial
            case 3 -> ModCheckUtils.isAppfluxLoading();
            case 4 -> ModCheckUtils.isAAELoading();
            default -> false;
        };
    }
}