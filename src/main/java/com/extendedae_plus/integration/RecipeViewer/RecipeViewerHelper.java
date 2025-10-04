package com.extendedae_plus.integration.RecipeViewer;

import appeng.api.stacks.GenericStack;
import com.extendedae_plus.integration.RecipeViewer.emi.EMIHelper;
import com.extendedae_plus.integration.RecipeViewer.jei.JeiHelper;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Optional;

public class RecipeViewerHelper {
    private static IRecipeViewerHelper activeViewer;

    public static void init() {
        if (ModList.get().isLoaded("emi")) activeViewer = new EMIHelper();
        else if (ModList.get().isLoaded("jei")) activeViewer = new JeiHelper();
        else activeViewer = new EmptyHelper();
    }

    public static Optional<IRecipeViewerHelper> getViewer() {
        if (activeViewer == null) init();
        return Optional.ofNullable(activeViewer);
    }

    public static List<GenericStack> getHoveredStacks() {
        return getViewer().map(IRecipeViewerHelper::getHoveredStacks).orElse(List.of());
    }

    public static List<GenericStack> getFavorites() {
        return getViewer().map(IRecipeViewerHelper::getFavorites).orElse(List.of());
    }

    public static boolean isCheatMode() {
        return getViewer().map(IRecipeViewerHelper::isCheatMode).orElse(false);
    }

    public static void addFavorite(GenericStack stack) {
        getViewer().ifPresent(viewer -> viewer.addFavorite(stack));
    }

    public static void setSearchText(String text) {
        getViewer().ifPresent(viewer -> viewer.setSearch(text));
    }
}
