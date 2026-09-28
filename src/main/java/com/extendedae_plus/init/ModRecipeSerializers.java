package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.recipe.SuperCircuitCutterRecipe;
import com.extendedae_plus.recipe.SuperCircuitCutterRecipeSerializer;
import com.extendedae_plus.recipe.SuperCrystalAssemblerRecipe;
import com.extendedae_plus.recipe.SuperCrystalAssemblerRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ExtendedAEPlus.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SuperCrystalAssemblerRecipe>> SUPER_CRYSTAL_ASSEMBLER =
            SERIALIZERS.register("crystal_assembler_plus", () -> SuperCrystalAssemblerRecipeSerializer.INSTANCE);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SuperCircuitCutterRecipe>> SUPER_CIRCUIT_CUTTER =
            SERIALIZERS.register("circuit_cutter_plus", () -> SuperCircuitCutterRecipeSerializer.INSTANCE);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ExtendedAEPlus.MODID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<SuperCrystalAssemblerRecipe>> SUPER_CRYSTAL_ASSEMBLER_TYPE =
            TYPES.register("crystal_assembler_plus", () -> SuperCrystalAssemblerRecipe.TYPE);
    public static final DeferredHolder<RecipeType<?>, RecipeType<SuperCircuitCutterRecipe>> SUPER_CIRCUIT_CUTTER_TYPE =
            TYPES.register("circuit_cutter_plus", () -> SuperCircuitCutterRecipe.TYPE);

    private ModRecipeSerializers() {
    }
}
