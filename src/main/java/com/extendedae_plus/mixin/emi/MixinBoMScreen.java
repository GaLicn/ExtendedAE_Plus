package com.extendedae_plus.mixin.emi;

import appeng.integration.modules.emi.EmiStackHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.integration.emi.HandlerBoMRecipes;
import com.extendedae_plus.integration.emi.PatternFillingHelper;
import com.extendedae_plus.network.RequestProvidersListC2SPacket;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Mixin(BoMScreen.class)
public abstract class MixinBoMScreen {
    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void onMouseClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!(button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && Screen.hasControlDown())) return;
        try {
            var self = (BoMScreen)(Object) this;
            Object hover = self.getHoveredStack((int) mouseX, (int) mouseY);

            // 救救孩子😭😭😭为什么private内部类套内部类aaa
            // Mixins你们好啊, 我是reflection大王, 我要来破坏代码兼容性了😈😈😈
            // 你知道吗: 其实以前这里套了两层reflection😋😋😋
            if (hover != null) {
                Field nodeField = hover.getClass().getDeclaredField("node");
                nodeField.setAccessible(true);
                Object nodeObject = nodeField.get(hover);
                MaterialNode node = (MaterialNode) nodeObject;

                if (node.recipe != null) {
                    if (Minecraft.getInstance().player.containerMenu instanceof PatternEncodingTermMenu menu) {
                        boolean isCraftingRecipe = false;
                        if (node.recipe.getBackingRecipe() != null) {
                            var type = node.recipe.getBackingRecipe().value().getType();
                            if (type == RecipeType.CRAFTING || type == RecipeType.STONECUTTING || type == RecipeType.SMITHING)
                                isCraftingRecipe = true;
                        }
                        menu.clear();

                        if (isCraftingRecipe) {
                            PatternFillingHelper.encodeCraftingRecipe(menu,
                                    node.recipe.getBackingRecipe(),
                                    HandlerBoMRecipes.updateRecipe(node.recipe, BoM.tree.batches,
                                            HandlerBoMRecipes.collectInputs(node)),
                                    stack -> true);

                            menu.encode();
                        } else {
                            EmiStack nodeStack = node.ingredient.getEmiStacks().getFirst();
                            List<EmiStack> outputs = new ArrayList<>(node.recipe.getOutputs());
                            outputs.remove(nodeStack);
                            outputs.addFirst(nodeStack);
                            PatternFillingHelper.encodeProcessingRecipe(menu,
                                    HandlerBoMRecipes.updateRecipe(node.recipe, BoM.tree.batches,
                                            HandlerBoMRecipes.collectInputs(node)),
                                    outputs.stream().map(EmiStackHelper::toGenericStack).toList());

                            menu.encode();

                            String name = ExtendedAEPatternUploadUtil.mapRecipeTypeToSearchKey(node.recipe.getBackingRecipe().value());
                            if (!(name == null || name.isBlank()))
                                ExtendedAEPatternUploadUtil.setLastProcessingName(name);

                            PacketDistributor.sendToServer(RequestProvidersListC2SPacket.INSTANCE);
                        }
                        Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value());
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
