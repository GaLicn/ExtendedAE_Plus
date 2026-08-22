package com.extendedae_plus.recipe;

import com.extendedae_plus.ExtendedAEPlus;
import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Combines local assembler recipes with recipes supplied by ExtendedAE at runtime. */
public final class SuperCrystalAssemblerRecipeManager {
    private static final String EXTENDED_AE = "extendedae";
    private static final Map<RecipeManager, CacheEntry> CACHE = new WeakHashMap<>();

    private SuperCrystalAssemblerRecipeManager() {
    }

    public static synchronized List<RecipeHolder<SuperCrystalAssemblerRecipe>> getAllRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<SuperCrystalAssemblerRecipe>> localRecipes =
                recipeManager.getAllRecipesFor(SuperCrystalAssemblerRecipe.TYPE);
        List<RecipeHolder<CrystalAssemblerRecipe>> extendedAeRecipes = getExtendedAeRecipes(recipeManager);

        CacheEntry cached = CACHE.get(recipeManager);
        if (cached != null && cached.localRecipes == localRecipes && cached.extendedAeRecipes == extendedAeRecipes) {
            return cached.combinedRecipes;
        }

        List<RecipeHolder<SuperCrystalAssemblerRecipe>> combinedRecipes = new ArrayList<>(localRecipes);
        for (RecipeHolder<CrystalAssemblerRecipe> holder : extendedAeRecipes) {
            combinedRecipes.add(convert(holder));
        }

        List<RecipeHolder<SuperCrystalAssemblerRecipe>> result = List.copyOf(combinedRecipes);
        CACHE.put(recipeManager, new CacheEntry(localRecipes, extendedAeRecipes, result));
        return result;
    }

    private static List<RecipeHolder<CrystalAssemblerRecipe>> getExtendedAeRecipes(RecipeManager recipeManager) {
        if (!ModList.get().isLoaded(EXTENDED_AE)) {
            return List.of();
        }
        return recipeManager.getAllRecipesFor(CrystalAssemblerRecipe.TYPE);
    }

    private static RecipeHolder<SuperCrystalAssemblerRecipe> convert(RecipeHolder<CrystalAssemblerRecipe> holder) {
        CrystalAssemblerRecipe recipe = holder.value();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                ExtendedAEPlus.MODID,
                "compat/extendedae/" + holder.id().getPath());
        return new RecipeHolder<>(id, new SuperCrystalAssemblerRecipe(
                recipe.output,
                recipe.getInputs(),
                Optional.ofNullable(recipe.getFluid())));
    }

    private record CacheEntry(
            List<RecipeHolder<SuperCrystalAssemblerRecipe>> localRecipes,
            List<RecipeHolder<CrystalAssemblerRecipe>> extendedAeRecipes,
            List<RecipeHolder<SuperCrystalAssemblerRecipe>> combinedRecipes) {
    }
}
