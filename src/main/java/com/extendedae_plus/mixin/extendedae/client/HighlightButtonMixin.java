package com.extendedae_plus.mixin.extendedae.client;

import com.glodblock.github.extendedae.client.button.HighlightButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.gui.components.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(value = HighlightButton.class, priority = 1000)
public abstract class HighlightButtonMixin {
	@Shadow(remap = false)
	private static void highlight(Button btn) {}

	private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAEPlus");

	@Inject(method = "highlight", at = @At("TAIL"), remap = false)
	private static void onHighlight(Button btn, CallbackInfo ci) {
		if (btn instanceof HighlightButton hb) {
			var minecraft = net.minecraft.client.Minecraft.getInstance();
			if (minecraft.screen instanceof GuiExPatternTerminal<?> terminal) {
				try {
					var fPos = HighlightButton.class.getDeclaredField("pos");
					fPos.setAccessible(true);
					Object btnPos = fPos.get(hb);
					if (btnPos == null) {
						return;
					}
					var fFace = HighlightButton.class.getDeclaredField("face");
					fFace.setAccessible(true);
					Object btnFace = fFace.get(hb); // 允许为 null：方块形

					var infoMapField = GuiExPatternTerminal.class.getDeclaredField("infoMap");
					infoMapField.setAccessible(true);
					@SuppressWarnings("unchecked")
					var infoMap = (java.util.Map<Long, Object>) infoMapField.get(terminal);

					for (var entry : infoMap.entrySet()) {
						var info = entry.getValue();
						var mPos = info.getClass().getMethod("pos");
						mPos.setAccessible(true);
						Object infoPos = mPos.invoke(info);

						var mFace = info.getClass().getMethod("face");
						mFace.setAccessible(true);
						Object infoFace = mFace.invoke(info); // 允许为 null：方块形

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
					LOGGER.warn("HighlightButton onHighlight 处理异常", t);
				}
			}
		}
	}
} 