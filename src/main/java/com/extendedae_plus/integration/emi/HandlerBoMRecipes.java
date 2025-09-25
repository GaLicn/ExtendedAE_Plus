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
    public static HashMap<ResourceLocation, GenericStack> collectInputs(MaterialNode parentNode) {
        var recipe = parentNode.recipe;
        if (parentNode.state != FoldState.EXPANDED) return new HashMap<>();
        if (parentNode.children == null) return new HashMap<>();

        HashMap<ResourceLocation, GenericStack> results = new HashMap<>();
        var type = recipe.getBackingRecipe().value().getType();

        parentNode.children.forEach(child -> {
            EmiStack stack;

            if (child.recipe != null && type != RecipeType.STONECUTTING)
                stack = child.recipe.getOutputs().getFirst().getEmiStacks().getFirst();
            else stack = child.ingredient.getEmiStacks().getFirst();

            recipe.getInputs().forEach(input -> {
                if (input.getEmiStacks().contains(stack)) {
                    stack.setAmount(input.getEmiStacks().getFirst().getAmount());
                    GenericStack genericStack = EmiStackHelper.toGenericStack(stack);
                    if (genericStack == null) return;

                    results.put(input.getEmiStacks().getFirst().getId(), genericStack);
                }
            });
        });
        return results;
    }

//    public static void handle(C2SPacketStoneCuttingID packet, ServerPlayer player) {
//        if (!(player.containerMenu instanceof PatternEncodingTermMenu menu)) return;
////        RecipeHolder<?> recipeHolder = player.level().getRecipeManager()
////                .byKey(packet.recipeID()).orElse(null);
//        EmiRecipe recipe = EmiApi.getRecipeManager()
//                .getRecipe(packet.recipeID());
//
//        if (recipe == null) return;
//
//        try {
//            menu.clear();
//            List<List<GenericStack>> modifiedInputs = updateRecipe(recipe, packet.batches(), packet.selectedStacks());
//
//            // 遍历原配方的每个输入
//            for (EmiIngredient originalInput : recipe.getInputs()) {
//                if (!originalInput.getEmiStacks().isEmpty()) {
//                    EmiStack firstStack = originalInput.getEmiStacks().getFirst();
//                    ResourceLocation stackId = firstStack.getId();
//
//                    if (packet.selectedStacks().containsKey(stackId))
//                        modifiedInputs.add(List.of(
//                                packet.selectedStacks().getOrDefault(stackId, EmiStackHelper.toGenericStack(firstStack))));
//                }
//            }
//
//            // 获取输出（保持不变）
//            List<GenericStack> outputs = EmiStackHelper.ofOutputs(recipe);
//
//            // 编码修改后的配方
////            EncodingHelper.encodeProcessingRecipe(menu, modifiedInputs, outputs);
//        } catch (Exception e) {
//            ExtendedAEPlus.LOGGER.debug("encode failed:", e);
//        }
//    }

    public static List<List<GenericStack>> updateRecipe(EmiRecipe original, long batches,
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
}
