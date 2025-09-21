package com.extendedae_plus.mixin.ae2;

import appeng.crafting.pattern.EncodedPatternItem;
import com.extendedae_plus.config.ModConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EncodedPatternItem.class)
public class EncodedPatternItemMixin {
    // 客户端：在 HoverText 显示样板的编码玩家
    @Inject(method = "appendHoverText", at = @At("TAIL"))
    public void epp$appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag advancedTooltips, CallbackInfo ci){
        if (ModConfigs.SHOW_ENCODER_PATTERN_PLAYER.get()) {
            var customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            var tag = customData.copyTag();
            if (tag.contains("encodePlayer")) {
                String name = tag.getString("encodePlayer");
                lines.add(Component.translatable("extendedae_plus.pattern.hovertext.player", name).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
