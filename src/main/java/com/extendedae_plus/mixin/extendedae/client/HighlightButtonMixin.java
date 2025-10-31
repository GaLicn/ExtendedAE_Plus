package com.extendedae_plus.mixin.extendedae.client;

import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.HighlightButtonAccessor;
import com.extendedae_plus.util.Logger;
import com.glodblock.github.extendedae.client.button.HighlightButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Mixin(value = HighlightButton.class)
public abstract class HighlightButtonMixin {
	@Inject(method = "highlight", at = @At("TAIL"), remap = false)
	private static void onHighlight(Button btn, CallbackInfo ci) {
		if (!(btn instanceof HighlightButton hb)) return;

		var minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof GuiExPatternTerminal<?> terminal)) return;

		try {
			// 使用 Accessor 获取 HighlightButton 的字段
			BlockPos btnPos = ((HighlightButtonAccessor) hb).getPos();
			Direction btnFace = ((HighlightButtonAccessor) hb).getFace();
			if (btnPos == null) return;

			// 使用 Accessor 获取 infoMap
			HashMap<Long, GuiExPatternTerminal.PatternProviderInfo> infoMap = ((GuiExPatternTerminalAccessor) terminal).getInfoMap();

			for (Map.Entry<Long, GuiExPatternTerminal.PatternProviderInfo> entry : infoMap.entrySet()) {
				GuiExPatternTerminal.PatternProviderInfo info = entry.getValue();

				BlockPos infoPos = info.pos();
				Direction infoFace = info.face();

				// 匹配规则：pos 必须相等；face 允许为 null，null 仅与 null 匹配
				boolean posEqual = Objects.equals(btnPos, infoPos);
				boolean faceEqual = (btnFace == null && infoFace == null) || Objects.equals(btnFace, infoFace);
				if (posEqual && faceEqual) {
					long serverId = entry.getKey();
					var setMethod = terminal.getClass().getMethod("setCurrentlyChoicePatternProvider", long.class);
					setMethod.setAccessible(true);
					setMethod.invoke(terminal, serverId);
					break;
				}
			}
		} catch (Throwable t) {
			Logger.EAP$LOGGER.warn("HighlightButton onHighlight 处理异常", t);
		}
	}
}