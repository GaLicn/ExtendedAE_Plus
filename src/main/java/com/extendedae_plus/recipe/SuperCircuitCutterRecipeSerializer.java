package com.extendedae_plus.recipe;

import com.glodblock.github.glodium.recipe.stack.IngredientStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

public final class SuperCircuitCutterRecipeSerializer implements RecipeSerializer<SuperCircuitCutterRecipe> {
    public static final MapCodec<SuperCircuitCutterRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemStack.CODEC.fieldOf("output").forGetter(SuperCircuitCutterRecipe::output),
            IngredientStack.ITEM_CODEC.fieldOf("input").forGetter(SuperCircuitCutterRecipe::input)
    ).apply(instance, SuperCircuitCutterRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuperCircuitCutterRecipe> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, SuperCircuitCutterRecipe::output,
            IngredientStack.ITEM_STREAM_CODEC, SuperCircuitCutterRecipe::input,
            SuperCircuitCutterRecipe::new);

    @Override
    public @NotNull MapCodec<SuperCircuitCutterRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, SuperCircuitCutterRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
