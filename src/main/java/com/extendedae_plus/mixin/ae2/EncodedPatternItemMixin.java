package com.extendedae_plus.mixin.ae2;

import appeng.crafting.pattern.EncodedPatternItem;
import com.extendedae_plus.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EncodedPatternItem.class)
public class EncodedPatternItemMixin {
    // 客户端：在 HoverText 显示样板的编码玩家
    @Inject(method = "appendHoverText", at = @At("TAIL"))
    public void epp$appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag advancedTooltips, CallbackInfo ci){
        if (stack.hasTag() && ModConfig.INSTANCE.showEncoderPatternPlayer) {
            CompoundTag tag = stack.getOrCreateTag();
            String name = tag.getString("encodePlayer");
            lines.add(Component.translatable("extendedae_plus.pattern.hovertext.player", name).withStyle(ChatFormatting.GRAY));
        }
    }
}
