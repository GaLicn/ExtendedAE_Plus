package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.network.upload.EncodeWithRecipeIdC2SPacket;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EmiRecipeFiller.class)
public class EmiRecipeFillerMixin {
    @Inject(method = "performFill", at = @At("RETURN"), remap = false)
    private static <T extends AbstractContainerMenu> void onPerformFill(EmiRecipe recipe, AbstractContainerScreen<T> screen, EmiCraftContext.Type type, EmiCraftContext.Destination destination, int amount, CallbackInfoReturnable<Boolean> cir) {
        PacketDistributor.sendToServer(new EncodeWithRecipeIdC2SPacket(recipe.getId()));
    }
}
