package com.extendedae_plus.integration.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.screen.SuperCircuitCutterScreen;
import com.extendedae_plus.client.screen.SuperCrystalAssemblerScreen;
import com.extendedae_plus.compat.JeiRuntimeCompat;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.BasicCoreItem;
import com.extendedae_plus.items.materials.EntitySpeedCardItem;
import com.extendedae_plus.recipe.SuperCircuitCutterRecipe;
import com.extendedae_plus.recipe.SuperCircuitCutterRecipeManager;
import com.extendedae_plus.recipe.SuperCrystalAssemblerRecipe;
import com.extendedae_plus.recipe.SuperCrystalAssemblerRecipeManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@JeiPlugin
public class ExtendedAEJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        JeiRuntimeCompat.setRuntime(jeiRuntime);
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

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new SuperCrystalAssemblerCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new SuperCircuitCutterCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(SuperCrystalAssemblerCategory.TYPE,
                    SuperCrystalAssemblerRecipeManager.getAllRecipes(level));
            registration.addRecipes(SuperCircuitCutterCategory.TYPE,
                    SuperCircuitCutterRecipeManager.getAllRecipes(level));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.CRYSTAL_ASSEMBLER_PLUS.get(), SuperCrystalAssemblerCategory.TYPE);
        registration.addRecipeCatalyst(ModItems.CIRCUIT_CUTTER_PLUS.get(), SuperCircuitCutterCategory.TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(SuperCrystalAssemblerScreen.class,
                new IGuiContainerHandler<SuperCrystalAssemblerScreen>() {
                    @Override
                    public @NotNull Collection<IGuiClickableArea> getGuiClickableAreas(
                            @NotNull SuperCrystalAssemblerScreen screen, double mouseX, double mouseY) {
                        return List.of(IGuiClickableArea.createBasic(81, 42, 40, 12, SuperCrystalAssemblerCategory.TYPE));
                    }
                });
        registration.addGenericGuiContainerHandler(SuperCircuitCutterScreen.class,
                new IGuiContainerHandler<SuperCircuitCutterScreen>() {
                    @Override
                    public @NotNull Collection<IGuiClickableArea> getGuiClickableAreas(
                            @NotNull SuperCircuitCutterScreen screen, double mouseX, double mouseY) {
                        return List.of(IGuiClickableArea.createBasic(65, 39, 35, 12, SuperCircuitCutterCategory.TYPE));
                    }
                });
    }
}
