package com.extendedae_plus.integration.emi;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.FoldState;
import dev.emi.emi.bom.MaterialNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HandlerBoMRecipes {
    public static HashMap<ResourceLocation, GenericStack> collectInputs(MaterialNode parentNode, long batches) {
        var recipe = parentNode.recipe;
        if (recipe == null) return new HashMap<>();
        if (parentNode.state != FoldState.EXPANDED) return new HashMap<>();
        if (parentNode.children == null) return new HashMap<>();

        HashMap<ResourceLocation, GenericStack> results = new HashMap<>();
        RecipeType<?> type;
        if (recipe.getBackingRecipe() != null)
            type = recipe.getBackingRecipe().value().getType();
        else type = null;

        parentNode.children.forEach(child -> {
            EmiStack stack;

            if (child.recipe != null && type != RecipeType.STONECUTTING)
                stack = child.recipe.getOutputs().getFirst().getEmiStacks().getFirst();
            else stack = child.ingredient.getEmiStacks().getFirst();

            recipe.getInputs().forEach(input -> {
                if (input.getEmiStacks().contains(stack)) {
                    EmiStack batchedStack = batchAmount(stack, batches);
                    GenericStack genericStack = EmiStackHelper.toGenericStack(batchedStack);
                    if (genericStack == null) return;

                    results.put(input.getEmiStacks().getFirst().getId(), genericStack);
                }
            });
        });
        return results;
    }

    public static List<List<GenericStack>> updateRecipe(EmiRecipe original,
                                                HashMap<ResourceLocation, GenericStack> selectedInputs) {
        List<List<GenericStack>> modifiedInputs = new ArrayList<>();

        EmiStackHelper.ofInputs(original).forEach(stacks -> {
            if (!stacks.isEmpty()) {
                GenericStack originStack = stacks.getFirst();
                ResourceLocation stackId = originStack.what().getId();

                if (selectedInputs.containsKey(stackId))
                    modifiedInputs.add(List.of(
                            selectedInputs.getOrDefault(stackId, originStack)));
            } else modifiedInputs.add(List.of());
        });
        return modifiedInputs;
    }

    public static EmiStack batchAmount(EmiStack original, long batches) {
        return original.copy().setAmount(original.getAmount() * batches);
    }
}
