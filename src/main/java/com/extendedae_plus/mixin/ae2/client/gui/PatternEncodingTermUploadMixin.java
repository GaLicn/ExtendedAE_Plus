package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import com.extendedae_plus.network.upload.EncodeWithShiftFlagC2SPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;

@Mixin(PatternEncodingTermScreen.class)
public class PatternEncodingTermUploadMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/ActionButton;<init>(Lappeng/api/config/ActionItems;Ljava/util/function/Consumer;)V"),
            index = 1)
    private Consumer<ActionItems> eap$encodingButton(Consumer<ActionItems> action) {
        return ignored -> {
            ClientPacketDistributor.sendToServer(new EncodeWithShiftFlagC2SPacket(Minecraft.getInstance().hasShiftDown()));
            var screen = (PatternEncodingTermScreen<?>) (Object) this;
            screen.getMenu().encode();
        };
    }
}
