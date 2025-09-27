package com.extendedae_plus.integration.RecipeViewer;

import appeng.api.stacks.GenericStack;

import java.util.List;

public class EmptyHelper implements IRecipeViewerHelper{
    @Override
    public List<GenericStack> getHoveredStacks(double mouseX, double mouseY) {
        return List.of();
    }

    @Override
    public List<GenericStack> getHoveredStacks() {
        return List.of();
    }

    @Override
    public List<GenericStack> getFavorites() {
        return List.of();
    }

    @Override
    public void addFavorite(GenericStack stack) {
    }

    @Override
    public void setSearch(String text) {

    }
}
