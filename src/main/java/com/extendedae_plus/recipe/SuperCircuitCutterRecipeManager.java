package com.extendedae_plus.recipe;

import com.extendedae_plus.ExtendedAEPlus;
import com.glodblock.github.extendedae.recipe.CircuitCutterRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Combines local cutter recipes with recipes supplied by ExtendedAE at runtime. */
public final class SuperCircuitCutterRecipeManager {
    private static final String EXTENDED_AE = "extendedae";
    private static final Map<RecipeManager, CacheEntry> CACHE = new WeakHashMap<>();

    private SuperCircuitCutterRecipeManager() {
    }

    public static synchronized List<RecipeHolder<SuperCircuitCutterRecipe>> getAllRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<SuperCircuitCutterRecipe>> localRecipes =
                recipeManager.getAllRecipesFor(SuperCircuitCutterRecipe.TYPE);
        List<RecipeHolder<CircuitCutterRecipe>> extendedAeRecipes = getExtendedAeRecipes(recipeManager);

        CacheEntry cached = CACHE.get(recipeManager);
        if (cached != null && cached.localRecipes == localRecipes && cached.extendedAeRecipes == extendedAeRecipes) {
            return cached.combinedRecipes;
        }

        List<RecipeHolder<SuperCircuitCutterRecipe>> combinedRecipes = new ArrayList<>(localRecipes);
        for (RecipeHolder<CircuitCutterRecipe> holder : extendedAeRecipes) {
            combinedRecipes.add(convert(holder));
        }

        List<RecipeHolder<SuperCircuitCutterRecipe>> result = List.copyOf(combinedRecipes);
        CACHE.put(recipeManager, new CacheEntry(localRecipes, extendedAeRecipes, result));
        return result;
    }

    private static List<RecipeHolder<CircuitCutterRecipe>> getExtendedAeRecipes(RecipeManager recipeManager) {
        if (!ModList.get().isLoaded(EXTENDED_AE)) {
            return List.of();
        }
        return recipeManager.getAllRecipesFor(CircuitCutterRecipe.TYPE);
    }

    private static RecipeHolder<SuperCircuitCutterRecipe> convert(RecipeHolder<CircuitCutterRecipe> holder) {
        CircuitCutterRecipe recipe = holder.value();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                ExtendedAEPlus.MODID,
                "compat/extendedae/" + holder.id().getPath());
        return new RecipeHolder<>(id, new SuperCircuitCutterRecipe(recipe.output, recipe.getInput()));
    }

    private record CacheEntry(
            List<RecipeHolder<SuperCircuitCutterRecipe>> localRecipes,
            List<RecipeHolder<CircuitCutterRecipe>> extendedAeRecipes,
            List<RecipeHolder<SuperCircuitCutterRecipe>> combinedRecipes) {
    }
}
