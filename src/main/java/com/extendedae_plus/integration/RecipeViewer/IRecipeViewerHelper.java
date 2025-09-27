package com.extendedae_plus.integration.RecipeViewer;

import appeng.api.stacks.GenericStack;

import java.util.List;

public interface IRecipeViewerHelper {
    List<GenericStack> getHoveredStacks(double mouseX, double mouseY);

    List<GenericStack> getHoveredStacks();

    List<GenericStack> getFavorites();

    default boolean isCheatMode() {
        return false;
    }

    void addFavorite(GenericStack stack);

    void setSearch(String text);
}
