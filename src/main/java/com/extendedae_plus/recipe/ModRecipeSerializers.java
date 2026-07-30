package com.extendedae_plus.recipe;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ExtendedAEPlus.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, SuperCrystalAssemblerRecipeSerializer> SUPER_CRYSTAL_ASSEMBLER =
            SERIALIZERS.register("crystal_assembler_plus", SuperCrystalAssemblerRecipeSerializer::new);

    private ModRecipeSerializers() {
    }
}
