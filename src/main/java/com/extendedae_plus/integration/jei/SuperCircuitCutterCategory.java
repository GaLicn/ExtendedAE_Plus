package com.extendedae_plus.integration.jei;

import appeng.core.AppEng;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.recipe.SuperCircuitCutterRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

/** JEI 的超级电路切片机配方分类。 */
public final class SuperCircuitCutterCategory extends AbstractRecipeCategory<RecipeHolder<SuperCircuitCutterRecipe>> {
    public static final RecipeType<RecipeHolder<SuperCircuitCutterRecipe>> TYPE =
            RecipeType.createFromVanilla(SuperCircuitCutterRecipe.TYPE);
    private final IDrawable background;
    private final IDrawableAnimated progress;

    public SuperCircuitCutterCategory(IGuiHelper helpers) {
        super(TYPE,
                ModItems.CIRCUIT_CUTTER_PLUS.get().getDescription(),
                helpers.createDrawableItemStack(ModItems.CIRCUIT_CUTTER_PLUS.get().getDefaultInstance()), 94, 26);
        ResourceLocation texture = AppEng.makeId("textures/guis/circuit_cutter.png");
        background = helpers.createDrawable(texture, 43, 32, 94, 26);
        IDrawableStatic progressDrawable = helpers.drawableBuilder(texture, 176, 0, 6, 18)
                .addPadding(4, 0, 88, 0).build();
        progress = helpers.createAnimatedDrawable(progressDrawable, 40, IDrawableAnimated.StartDirection.BOTTOM, false);
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull RecipeHolder<SuperCircuitCutterRecipe> holder,
            @NotNull IFocusGroup focuses) {
        var input = holder.value().input();
        var slot = builder.addSlot(RecipeIngredientRole.INPUT, 3, 5).setSlotName("input");
        for (var stack : input.getIngredient().getItems()) {
            slot.addItemStack(stack.copyWithCount(input.getAmount()));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 66, 5).setSlotName("output")
                .addItemStack(holder.value().output().copy());
    }

    @Override
    public void draw(@NotNull RecipeHolder<SuperCircuitCutterRecipe> holder, @NotNull IRecipeSlotsView slots,
            @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        background.draw(graphics);
        progress.draw(graphics);
    }
}
