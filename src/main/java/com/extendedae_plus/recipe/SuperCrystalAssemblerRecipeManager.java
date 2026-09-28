package com.extendedae_plus.recipe;

import com.extendedae_plus.ExtendedAEPlus;
import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
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
    private static final Map<RecipeMap, List<RecipeHolder<SuperCrystalAssemblerRecipe>>> CACHE = new WeakHashMap<>();

    private SuperCrystalAssemblerRecipeManager() {
    }

    public static List<RecipeHolder<SuperCrystalAssemblerRecipe>> getAllRecipes(Level level) {
        if (level == null || !(level.recipeAccess() instanceof RecipeManager recipeManager)) {
            return List.of();
        }
        return getAllRecipes(recipeManager.recipeMap());
    }

    public static synchronized List<RecipeHolder<SuperCrystalAssemblerRecipe>> getAllRecipes(RecipeMap recipeMap) {
        var cached = CACHE.get(recipeMap);
        if (cached != null) {
            return cached;
        }
        List<RecipeHolder<SuperCrystalAssemblerRecipe>> combinedRecipes =
                new ArrayList<>(recipeMap.byType(SuperCrystalAssemblerRecipe.TYPE));
        if (ModList.get().isLoaded(EXTENDED_AE)) {
            for (RecipeHolder<CrystalAssemblerRecipe> holder : recipeMap.byType(CrystalAssemblerRecipe.TYPE)) {
                combinedRecipes.add(convert(holder));
            }
        }
        var result = List.copyOf(combinedRecipes);
        CACHE.put(recipeMap, result);
        return result;
    }

    private static RecipeHolder<SuperCrystalAssemblerRecipe> convert(RecipeHolder<CrystalAssemblerRecipe> holder) {
        CrystalAssemblerRecipe recipe = holder.value();
        Identifier id = Identifier.fromNamespaceAndPath(
                ExtendedAEPlus.MODID,
                "compat/extendedae/" + holder.id().identifier().getPath());
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), new SuperCrystalAssemblerRecipe(
                recipe.output.create(),
                recipe.getInputs(),
                Optional.ofNullable(recipe.getFluid())));
    }
}
