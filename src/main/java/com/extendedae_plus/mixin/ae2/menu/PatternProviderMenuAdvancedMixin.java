package com.extendedae_plus.mixin.ae2.menu;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.AdvancedBlockingHolder;
import com.extendedae_plus.api.PatternProviderMenuAdvancedSync;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuAdvancedMixin implements PatternProviderMenuAdvancedSync {
    @Shadow
    protected PatternProviderLogic logic;

    // 选择一个未占用的 GUI 同步 id（AE2 已用到 7），这里使用 20 以避冲突
    @Unique
    @GuiSync(20)
    public boolean eap$AdvancedBlocking = false;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncAdvancedBlocking(CallbackInfo ci) {
        // 避免@Shadow父类方法，改用公共API：AEBaseMenu#isClientSide()
        if (!((AEBaseMenu) (Object) this).isClientSide()) {
            var l = this.logic;
            if (l instanceof AdvancedBlockingHolder holder) {
                this.eap$AdvancedBlocking = holder.eap$getAdvancedBlocking();
                // debug removed
            }
        }
    }

    @Override
    public boolean eap$getAdvancedBlockingSynced() {
        return this.eap$AdvancedBlocking;
    }

    // 调试：当 Screen 每帧读取这些 getter 时打印，验证 Mixin 是否生效
    @Inject(method = "getBlockingMode", at = @At("HEAD"), remap = false)
    private void eap$debug_getBlockingMode(CallbackInfoReturnable<?> cir) {
    }

    @Inject(method = "getShowInAccessTerminal", at = @At("HEAD"), remap = false)
    private void eap$debug_getShowInAccessTerminal(CallbackInfoReturnable<?> cir) {
    }
}