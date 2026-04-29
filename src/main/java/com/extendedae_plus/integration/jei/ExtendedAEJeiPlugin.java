package com.extendedae_plus.integration.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.BasicCoreItem;
import com.extendedae_plus.items.materials.EntitySpeedCardItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class ExtendedAEJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "jei_plugin");

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
        registration.registerSubtypeInterpreter(
                VanillaTypes.ITEM_STACK,
                ModItems.ENTITY_SPEED_CARD.get(),
                new ISubtypeInterpreter<>() {
                    @Override
                    public @NotNull Object getSubtypeData(@NotNull ItemStack ingredient, @NotNull UidContext context) {
                        return EntitySpeedCardItem.readMultiplier(ingredient);
                    }

                    @Override
                    public @NotNull String getLegacyStringSubtypeInfo(@NotNull ItemStack ingredient,
                                                                      @NotNull UidContext context) {
                        // 返回同样的值给旧接口兼容
                        return String.valueOf(EntitySpeedCardItem.readMultiplier(ingredient));
                    }
                }
        );

        // Basic Core - 基础核心的NBT变体支持
        registration.registerSubtypeInterpreter(
                VanillaTypes.ITEM_STACK,
                ModItems.BASIC_CORE.get(),
                new ISubtypeInterpreter<>() {
                    @Override
                    public @NotNull Object getSubtypeData(@NotNull ItemStack stack, @NotNull UidContext context) {
                        if (!BasicCoreItem.isTyped(stack)) {
                            return "untyped";
                        }

                        BasicCoreItem.CoreType type = BasicCoreItem.getType(stack).orElse(null);
                        if (type == null) {
                            return "untyped";
                        }

                        int stage = BasicCoreItem.getStage(stack);
                        return type.id + "_" + stage;  // 如 "1_1", "2_3"
                    }

                    @Override
                    public @NotNull String getLegacyStringSubtypeInfo(@NotNull ItemStack stack,
                                                                      @NotNull UidContext context) {
                        if (!BasicCoreItem.isTyped(stack)) {
                            return "untyped";
                        }

                        BasicCoreItem.CoreType type = BasicCoreItem.getType(stack).orElse(null);
                        if (type == null) {
                            return "untyped";
                        }

                        int stage = BasicCoreItem.getStage(stack);
                        return type.id + "_" + stage;
                    }
                }
        );
    }
}
