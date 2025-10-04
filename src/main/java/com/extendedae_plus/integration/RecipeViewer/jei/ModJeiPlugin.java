package com.extendedae_plus.integration.RecipeViewer.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
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
public class ModJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "jei_plugin");

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
                VanillaTypes.ITEM_STACK,
                com.extendedae_plus.init.ModItems.ENTITY_SPEED_CARD.get(),
                new ISubtypeInterpreter<>() {
                    @Override
                    public @NotNull Object getSubtypeData(@NotNull ItemStack ingredient, @NotNull UidContext context) {
                        // 返回你想让 JEI 区分子类型的数据，这里用 multiplier
                        return EntitySpeedCardItem.readMultiplier(ingredient);
                    }

                    @Override
                    public @NotNull String getLegacyStringSubtypeInfo(@NotNull ItemStack ingredient, @NotNull UidContext context) {
                        // 返回同样的值给旧接口兼容
                        return String.valueOf(EntitySpeedCardItem.readMultiplier(ingredient));
                    }
                }
        );

    }
}
