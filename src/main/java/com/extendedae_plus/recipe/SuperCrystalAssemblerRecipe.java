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

import java.util.List;
import java.util.Optional;

/** 与 ExtendedAE 水晶装配器格式兼容的专用配方。 */
public record SuperCrystalAssemblerRecipe(ItemStack output, List<IngredientStack.Item> inputItems,
                                          Optional<IngredientStack.Fluid> inputFluid) implements Recipe<RecipeInput> {
    public static final ResourceLocation ID = ExtendedAEPlus.id("crystal_assembler_plus");
    public static final RecipeType<SuperCrystalAssemblerRecipe> TYPE = RecipeType.simple(ID);

    public SuperCrystalAssemblerRecipe {
        output = output.copy();
        inputItems = List.copyOf(inputItems);
    }

    @Override
    public boolean matches(@NotNull RecipeInput input, @NotNull Level level) {
        return false;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull RecipeInput input, @NotNull HolderLookup.Provider registries) {
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
        return ModRecipeSerializers.SUPER_CRYSTAL_ASSEMBLER.get();
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
