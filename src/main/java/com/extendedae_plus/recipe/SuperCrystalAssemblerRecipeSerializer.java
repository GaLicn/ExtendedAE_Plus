package com.extendedae_plus.recipe;

import com.glodblock.github.glodium.recipe.stack.IngredientStack;
import com.glodblock.github.glodium.util.GlodCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

/** 保留原水晶装配器的 JSON 和网络编码格式。 */
public final class SuperCrystalAssemblerRecipeSerializer {
    public static final MapCodec<SuperCrystalAssemblerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemStack.CODEC.fieldOf("output").forGetter(SuperCrystalAssemblerRecipe::output),
            IngredientStack.ITEM_CODEC.listOf().fieldOf("input_items").forGetter(SuperCrystalAssemblerRecipe::inputItems),
            IngredientStack.FLUID_CODEC.optionalFieldOf("input_fluid").forGetter(SuperCrystalAssemblerRecipe::inputFluid)
    ).apply(instance, SuperCrystalAssemblerRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuperCrystalAssemblerRecipe> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, SuperCrystalAssemblerRecipe::output,
            GlodCodecs.list(IngredientStack.ITEM_STREAM_CODEC), SuperCrystalAssemblerRecipe::inputItems,
            GlodCodecs.optional(IngredientStack.FLUID_STREAM_CODEC), SuperCrystalAssemblerRecipe::inputFluid,
            SuperCrystalAssemblerRecipe::new);

    public static final RecipeSerializer<SuperCrystalAssemblerRecipe> INSTANCE = new RecipeSerializer<>(CODEC, STREAM_CODEC);
}
