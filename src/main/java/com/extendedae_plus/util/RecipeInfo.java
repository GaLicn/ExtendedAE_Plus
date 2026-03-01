package com.extendedae_plus.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 配方完整信息（包含配方ID、输入槽位候选项、输出）
 */
public class RecipeInfo {
	private final Object recipeBase;
	private final ResourceLocation recipeId;
	private final boolean craftingRecipe;
	private final List<List<GenericStack>> inputs;
	private final List<GenericStack> outputs;

	public RecipeInfo(
		Object recipeBase,
		ResourceLocation recipeId,
		boolean craftingRecipe,
		List<List<GenericStack>> inputs,
		List<GenericStack> outputs
	) {
		this.recipeBase = recipeBase;
		this.recipeId = recipeId;
		this.craftingRecipe = craftingRecipe;
		this.inputs = inputs;
		this.outputs = outputs;
	}

	public Object getRecipeBase() {
		return recipeBase;
	}

	public ResourceLocation getRecipeId() {
		return recipeId;
	}

	public boolean isCraftingRecipe() {
		return craftingRecipe;
	}

	public List<List<GenericStack>> getInputs() {
		return inputs;
	}

	public List<GenericStack> getOutputs() {
		return outputs;
	}

	public List<ItemStack> selectBestInputs(Map<Item, Integer> bookmarkPriorities) {
		List<ItemStack> selected = new ArrayList<>();
		for (List<GenericStack> slotOptions : inputs) {
			if (slotOptions.isEmpty()) {
				selected.add(ItemStack.EMPTY);
				continue;
			}

			GenericStack best = slotOptions.get(0);
			int bestPriority = getPriority(best, bookmarkPriorities);
			for (int i = 1; i < slotOptions.size(); i++) {
				GenericStack option = slotOptions.get(i);
				int priority = getPriority(option, bookmarkPriorities);
				if (priority < bestPriority) {
					bestPriority = priority;
					best = option;
				}
			}
			selected.add(toItemStack(best));
		}
		return selected;
	}

	private int getPriority(GenericStack stack, Map<Item, Integer> priorities) {
		if (stack.what() instanceof AEItemKey itemKey) {
			return priorities.getOrDefault(itemKey.getItem(), Integer.MAX_VALUE);
		}
		return Integer.MAX_VALUE;
	}

	private ItemStack toItemStack(GenericStack stack) {
		if (stack.what() instanceof AEItemKey itemKey) {
			return itemKey.toStack((int) stack.amount());
		}
		if (stack.what() instanceof AEFluidKey) {
			return GenericStack.wrapInItemStack(stack);
		}
		return ItemStack.EMPTY;
	}
}

