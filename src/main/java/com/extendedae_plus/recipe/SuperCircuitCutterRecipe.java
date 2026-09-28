package com.extendedae_plus.recipe;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModRecipeSerializers;
import com.glodblock.github.glodium.recipe.stack.IngredientStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/** 与 ExtendedAE 电路切片机兼容的单物品输入配方。 */
public record SuperCircuitCutterRecipe(ItemStack output, IngredientStack.Item input) implements Recipe<RecipeInput> {
    public static final Identifier ID = ExtendedAEPlus.id("circuit_cutter_plus");
    public static final RecipeType<SuperCircuitCutterRecipe> TYPE = RecipeType.simple(ID);

    public SuperCircuitCutterRecipe {
        output = output.copy();
    }

    @Override
    public boolean matches(@NotNull RecipeInput recipeInput, @NotNull Level level) {
        return false;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull RecipeInput recipeInput) {
        return output.copy();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return ID.toString();
    }

    @Override
    public @NotNull RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return SuperCircuitCutterRecipeSerializer.INSTANCE;
    }

    @Override
    public @NotNull RecipeType<? extends Recipe<RecipeInput>> getType() {
        return TYPE;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
