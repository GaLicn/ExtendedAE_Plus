package com.extendedae_plus.recipe;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModRecipeSerializers;
import com.glodblock.github.glodium.recipe.stack.IngredientStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/** 与 ExtendedAE 电路切片机兼容的单物品输入配方。 */
public record SuperCircuitCutterRecipe(ItemStack output, IngredientStack.Item input) implements Recipe<RecipeInput> {
    public static final ResourceLocation ID = ExtendedAEPlus.id("circuit_cutter_plus");
    public static final RecipeType<SuperCircuitCutterRecipe> TYPE = RecipeType.simple(ID);

    public SuperCircuitCutterRecipe {
        output = output.copy();
    }

    @Override
    public boolean matches(@NotNull RecipeInput recipeInput, @NotNull Level level) {
        return false;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull RecipeInput recipeInput, @NotNull HolderLookup.Provider registries) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(@NotNull HolderLookup.Provider registries) {
        return output;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.SUPER_CIRCUIT_CUTTER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
