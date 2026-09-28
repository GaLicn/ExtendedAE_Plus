package com.extendedae_plus.mixin.extendedae.client;

import com.extendedae_plus.api.IExPatternTerminalSelection;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.HighlightButtonAccessor;
import com.glodblock.github.extendedae.client.button.HighlightButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Objects;

@Mixin(value = HighlightButton.class, priority = 1000)
public abstract class HighlightButtonMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAEPlus");

    @Inject(method = "highlight", at = @At("TAIL"), remap = false)
    private static void eap$onHighlight(Button button, CallbackInfo ci) {
        if (!(button instanceof HighlightButton highlightButton)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof GuiExPatternTerminal<?> terminal)) {
            return;
        }

        try {
            HighlightButtonAccessor buttonAccessor = (HighlightButtonAccessor) (Object) highlightButton;
            BlockPos buttonPos = buttonAccessor.eap$getPos();
            Direction buttonFace = buttonAccessor.eap$getFace();
            if (buttonPos == null) {
                return;
            }

            GuiExPatternTerminalAccessor terminalAccessor = (GuiExPatternTerminalAccessor) (Object) terminal;
            for (Map.Entry<Long, GuiExPatternTerminal.PatternProviderInfo> entry
                    : terminalAccessor.eap$getInfoMap().entrySet()) {
                GuiExPatternTerminal.PatternProviderInfo info = entry.getValue();
                boolean samePosition = Objects.equals(buttonPos, info.pos());
                boolean sameFace = (buttonFace == null && info.face() == null)
                        || Objects.equals(buttonFace, info.face());
                if (!samePosition || !sameFace) {
                    continue;
                }

                if (terminal instanceof IExPatternTerminalSelection selection) {
                    selection.eap$setCurrentlyChoicePatternProvider(entry.getKey());
                }
                break;
            }
        } catch (Throwable error) {
            LOGGER.debug("Unable to update the selected pattern provider", error);
        }
    }
}
