package com.extendedae_plus.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JEI 运行时查找配方，提取输入输出槽位与数量信息。
 */
public final class RecipeFinderUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAE Plus - RecipeFinder");

	private RecipeFinderUtil() {
	}

	public static List<RecipeInfo> findRecipesByIngredient(ITypedIngredient<?> ingredient) {
		if (ingredient == null) {
			return List.of();
		}

		Object runtimeObj = JeiRuntimeProxy.get();
		if (!(runtimeObj instanceof IJeiRuntime jeiRuntime)) {
			LOGGER.warn("[RecipeFinder] JEI runtime not available");
			return List.of();
		}

		IJeiHelpers jeiHelpers = jeiRuntime.getJeiHelpers();
		IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
		IFocusFactory focusFactory = jeiHelpers.getFocusFactory();
		IFocus<?> outputFocus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, ingredient);
		List<RecipeInfo> results = new ArrayList<>();

		// 1) Crafting recipes
		try {
			IRecipeCategory<RecipeHolder<CraftingRecipe>> category = recipeManager.getRecipeCategory(RecipeTypes.CRAFTING);
			recipeManager.createRecipeLookup(RecipeTypes.CRAFTING)
				.limitFocus(List.of(outputFocus))
				.get()
				.forEach(recipeHolder -> {
					Optional<IRecipeLayoutDrawable<Object>> layoutOpt = createLayout(recipeManager, category, recipeHolder, focusFactory);
					layoutOpt.ifPresent(layout -> {
						RecipeInfo info = extractRecipeInfo(recipeHolder, layout, true);
						if (info != null) {
							results.add(info);
						}
					});
				});
		} catch (Throwable t) {
			LOGGER.warn("[RecipeFinder] Error searching crafting recipes: {}", t.toString());
		}

		// 2) Other recipe types
		try {
			jeiHelpers.getAllRecipeTypes().forEach(recipeType -> {
				if (recipeType.equals(RecipeTypes.CRAFTING)) {
					return;
				}
				try {
					IRecipeCategory<?> category = recipeManager.getRecipeCategory(recipeType);
					@SuppressWarnings({ "rawtypes", "unchecked" })
					IRecipeLookup<Object> lookup = (IRecipeLookup) recipeManager.createRecipeLookup((RecipeType) recipeType);

					lookup.limitFocus(List.of(outputFocus))
						.get()
						.forEach(recipeObj -> {
							Optional<IRecipeLayoutDrawable<Object>> layoutOpt = createLayout(recipeManager, category, recipeObj, focusFactory);
							layoutOpt.ifPresent(layout -> {
								RecipeInfo info = extractRecipeInfo(recipeObj, layout, false);
								if (info != null) {
									results.add(info);
								}
							});
						});
				} catch (Throwable ignored) {
				}
			});
		} catch (Throwable t) {
			LOGGER.warn("[RecipeFinder] Error searching other recipes: {}", t.toString());
		}

		return results;
	}

	public static RecipeInfo selectBestRecipe(List<RecipeInfo> recipes) {
		if (recipes == null || recipes.isEmpty()) {
			return null;
		}
		for (RecipeInfo info : recipes) {
			if (info.isCraftingRecipe()) {
				return info;
			}
		}
		return recipes.get(0);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Optional<IRecipeLayoutDrawable<Object>> createLayout(
		IRecipeManager recipeManager,
		IRecipeCategory<?> category,
		Object recipeObj,
		IFocusFactory focusFactory
	) {
		return (Optional) recipeManager.createRecipeLayoutDrawable(
			(IRecipeCategory) category,
			recipeObj,
			focusFactory.getEmptyFocusGroup()
		);
	}

	private static RecipeInfo extractRecipeInfo(Object recipeObj, IRecipeLayoutDrawable<Object> layout, boolean isCrafting) {
		try {
			ResourceLocation recipeId = extractRecipeId(recipeObj);
			if (recipeId == null) {
				return null;
			}

			IRecipeSlotsView slotsView = layout.getRecipeSlotsView();
			List<List<GenericStack>> inputs = new ArrayList<>();
			List<GenericStack> outputs = new ArrayList<>();

			for (IRecipeSlotView slot : slotsView.getSlotViews(RecipeIngredientRole.INPUT)) {
				List<GenericStack> slotStacks = new ArrayList<>();
				for (ITypedIngredient<?> typed : slot.getAllIngredientsList()) {
					if (typed == null) {
						continue;
					}
					GenericStack stack = convertToGenericStack(typed);
					if (stack != null) {
						slotStacks.add(stack);
					}
				}
				inputs.add(slotStacks);
			}

			for (IRecipeSlotView slot : slotsView.getSlotViews(RecipeIngredientRole.OUTPUT)) {
				for (ITypedIngredient<?> typed : slot.getAllIngredientsList()) {
					if (typed == null) {
						continue;
					}
					GenericStack stack = convertToGenericStack(typed);
					if (stack != null) {
						outputs.add(stack);
					}
				}
			}

			return new RecipeInfo(recipeObj, recipeId, isCrafting, inputs, outputs);
		} catch (Throwable t) {
			return null;
		}
	}

	private static ResourceLocation extractRecipeId(Object recipeObj) {
		if (recipeObj == null) {
			return null;
		}
		if (recipeObj instanceof RecipeHolder<?> holder) {
			return holder.id();
		}
		try {
			var m = recipeObj.getClass().getMethod("getId");
			Object id = m.invoke(recipeObj);
			if (id instanceof ResourceLocation rl) {
				return rl;
			}
		} catch (Throwable ignored) {
		}
		try {
			var m = recipeObj.getClass().getMethod("getRecipeUid");
			Object id = m.invoke(recipeObj);
			if (id instanceof ResourceLocation rl) {
				return rl;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static GenericStack convertToGenericStack(ITypedIngredient<?> typedIngredient) {
		if (typedIngredient.getType() == VanillaTypes.ITEM_STACK) {
			ItemStack itemStack = (ItemStack) typedIngredient.getIngredient();
			if (!itemStack.isEmpty()) {
				AEItemKey itemKey = AEItemKey.of(itemStack);
				if (itemKey != null) {
					return new GenericStack(itemKey, itemStack.getCount());
				}
			}
		} else if (typedIngredient.getType() == NeoForgeTypes.FLUID_STACK) {
			FluidStack fluidStack = (FluidStack) typedIngredient.getIngredient();
			if (!fluidStack.isEmpty()) {
				AEFluidKey fluidKey = AEFluidKey.of(fluidStack);
				if (fluidKey != null) {
					return new GenericStack(fluidKey, fluidStack.getAmount());
				}
			}
		}
		return null;
	}
}
