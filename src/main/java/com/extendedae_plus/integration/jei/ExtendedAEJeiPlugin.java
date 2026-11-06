package com.extendedae_plus.integration.jei;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.screen.SuperCircuitCutterScreen;
import com.extendedae_plus.client.screen.SuperCrystalAssemblerScreen;
import com.extendedae_plus.compat.jei.JeiRuntimeCompat;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.recipe.SuperCircuitCutterRecipeManager;
import com.glodblock.github.extendedae.xmod.jei.recipe.CrystalAssemblerCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // 超级水晶装配器复用 ExtendedAE 原机的 JEI 分类，不再单独建分类（同 Mekanism 多机器共用一个分类）。
        registration.addRecipeCategories(new SuperCircuitCutterCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            // 超级水晶装配器直接复用 ExtendedAE 的配方，这些配方已由 ExtendedAE 的插件
            // 注册进同一个分类，此处只需补充催化剂，不重复注册配方。
            registration.addRecipes(SuperCircuitCutterCategory.TYPE,
                    SuperCircuitCutterRecipeManager.getAllRecipes(level));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.CRYSTAL_ASSEMBLER_PLUS.get(), CrystalAssemblerCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.CIRCUIT_CUTTER_PLUS.get(), SuperCircuitCutterCategory.TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(SuperCrystalAssemblerScreen.class,
                new IGuiContainerHandler<SuperCrystalAssemblerScreen>() {
                    @Override
                    public @NotNull Collection<IGuiClickableArea> getGuiClickableAreas(
                            @NotNull SuperCrystalAssemblerScreen screen, double mouseX, double mouseY) {
                        return List.of(IGuiClickableArea.createBasic(81, 42, 40, 12, CrystalAssemblerCategory.RECIPE_TYPE));
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
