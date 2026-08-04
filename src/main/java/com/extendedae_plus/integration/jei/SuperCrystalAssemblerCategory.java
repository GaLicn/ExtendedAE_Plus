package com.extendedae_plus.integration.jei;

import appeng.core.AppEng;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.recipe.SuperCrystalAssemblerRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawable;
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

/** JEI 中与原水晶装配器一致的 3×3 输入、液体输入和竖向进度布局。 */
public final class SuperCrystalAssemblerCategory extends AbstractRecipeCategory<RecipeHolder<SuperCrystalAssemblerRecipe>> {
    // JEI 接收的配方对象是 RecipeHolder，泛型信息在运行时会被擦除。
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static final Class<RecipeHolder<SuperCrystalAssemblerRecipe>> RECIPE_HOLDER_CLASS = (Class) RecipeHolder.class;
    public static final RecipeType<RecipeHolder<SuperCrystalAssemblerRecipe>> TYPE =
            new RecipeType<>(SuperCrystalAssemblerRecipe.ID, RECIPE_HOLDER_CLASS);

    private final IDrawableAnimated progress;
    private final IDrawable background;

    public SuperCrystalAssemblerCategory(IGuiHelper helpers) {
        super(TYPE,
                ModItems.CRYSTAL_ASSEMBLER_PLUS.get().getDescription(),
                helpers.createDrawableItemStack(ModItems.CRYSTAL_ASSEMBLER_PLUS.get().getDefaultInstance()),
                135, 58);
        ResourceLocation texture = AppEng.makeId("textures/guis/crystal_assembler.png");
        background = helpers.createDrawable(texture, 23, 19, 135, 58);
        IDrawableStatic progressDrawable = helpers.drawableBuilder(texture, 176, 0, 6, 18)
                .addPadding(20, 0, 129, 0).build();
        progress = helpers.createAnimatedDrawable(progressDrawable, 40, IDrawableAnimated.StartDirection.BOTTOM, false);
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull RecipeHolder<SuperCrystalAssemblerRecipe> holder,
            @NotNull IFocusGroup focuses) {
        var recipe = holder.value();
        int left = 3;
        int top = 3;
        for (var input : recipe.inputItems()) {
            if (!input.isEmpty()) {
                var slot = builder.addSlot(RecipeIngredientRole.INPUT, left, top);
                for (var stack : input.getIngredient().getItems()) {
                    slot.addItemStack(stack.copyWithCount(input.getAmount()));
                }
                left += 18;
                if (left >= 18 * 3) {
                    top += 18;
                    left = 3;
                }
            }
        }
        recipe.inputFluid().ifPresent(fluid -> {
            var slot = builder.addSlot(RecipeIngredientRole.INPUT, 58, 39).setSlotName("fluid_input");
            slot.setFluidRenderer(fluid.getAmount(), false, 16, 16);
            for (var stack : fluid.getIngredient().getStacks()) {
                slot.addFluidStack(stack.getFluid(), fluid.getAmount());
            }
        });
        builder.addSlot(RecipeIngredientRole.OUTPUT, 107, 21).setSlotName("output")
                .addItemStack(recipe.output().copy());
    }

    @Override
    public void draw(@NotNull RecipeHolder<SuperCrystalAssemblerRecipe> recipe, @NotNull IRecipeSlotsView slots,
            @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        background.draw(graphics);
        progress.draw(graphics);
    }
}
