package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.ActionButton;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.upload.EncodeWithShiftFlagC2SPacket;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PatternEncodingTermScreen.class)
public class PatternEncodingTermUploadMixin {
    @ModifyVariable(method = "<init>", at = @At(value = "STORE"), name = "encodeBtn")
    private ActionButton eap$encodingButton(ActionButton button) {
        return new ActionButton(ActionItems.ENCODE,actionItems -> {
            PacketDistributor.sendToServer(new EncodeWithShiftFlagC2SPacket(Screen.hasShiftDown()));
            var screen=(PatternEncodingTermScreen<?>) (Object)this;
            screen.getMenu().encode();
        });
    }
}
