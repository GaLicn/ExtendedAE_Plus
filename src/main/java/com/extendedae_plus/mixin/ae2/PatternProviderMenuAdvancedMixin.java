package com.extendedae_plus.mixin.ae2;

import appeng.menu.implementations.PatternProviderMenu;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.guisync.GuiSync;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extendedae_plus.api.AdvancedBlockingHolder;
import com.extendedae_plus.api.PatternProviderMenuAdvancedSync;

@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuAdvancedMixin implements PatternProviderMenuAdvancedSync {
    static {
        System.out.println("[EPP][MIXIN] Loaded PatternProviderMenuAdvancedMixin");
    }
    @Shadow
    protected abstract boolean isServerSide();

    @Shadow
    protected PatternProviderLogic logic;

    // 选择一个未占用的 GUI 同步 id（AE2 已用到 7），这里使用 20 以避冲突
    @GuiSync(20)
    public boolean eppAdvancedBlocking = false;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void epp$syncAdvancedBlocking(CallbackInfo ci) {
        if (this.isServerSide()) {
            var l = this.logic;
            if (l instanceof AdvancedBlockingHolder holder) {
                this.eppAdvancedBlocking = holder.ext$getAdvancedBlocking();
                System.out.println("[EPP][MENU][S->C] broadcastChanges: eppAdvancedBlocking=" + this.eppAdvancedBlocking);
            }
        } else {
            System.out.println("[EPP][MENU][CLIENT] broadcastChanges called on client side");
        }
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void epp$syncAdvancedBlockingTail(CallbackInfo ci) {
        System.out.println("[EPP][MENU] broadcastChanges tail");
    }

    // 构造器尾注入（public ctor）
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"))
    private void epp$initAdvancedSync_Public(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof AdvancedBlockingHolder holder) {
                this.eppAdvancedBlocking = holder.ext$getAdvancedBlocking();
                System.out.println("[EPP][MENU] <init>-public set initial eppAdvancedBlocking=" + this.eppAdvancedBlocking);
            }
        } catch (Throwable t) {
            System.out.println("[EPP][MENU] <init>-public init error: " + t);
        }
    }

    // 构造器尾注入（protected ctor with MenuType）
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"))
    private void epp$initAdvancedSync_Protected(MenuType<? extends PatternProviderMenu> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof AdvancedBlockingHolder holder) {
                this.eppAdvancedBlocking = holder.ext$getAdvancedBlocking();
                System.out.println("[EPP][MENU] <init>-protected set initial eppAdvancedBlocking=" + this.eppAdvancedBlocking);
            }
        } catch (Throwable t) {
            System.out.println("[EPP][MENU] <init>-protected init error: " + t);
        }
    }

    @Override
    public boolean ext$getAdvancedBlockingSynced() {
        return this.eppAdvancedBlocking;
    }

    // 调试：当 Screen 每帧读取这些 getter 时打印，验证 Mixin 是否生效
    @Inject(method = "getBlockingMode", at = @At("HEAD"), remap = false)
    private void epp$debug_getBlockingMode(CallbackInfoReturnable<?> cir) {
        System.out.println("[EPP][MENU][DEBUG] getBlockingMode() called; eppAdvancedBlocking=" + this.eppAdvancedBlocking);
    }

    @Inject(method = "getShowInAccessTerminal", at = @At("HEAD"), remap = false)
    private void epp$debug_getShowInAccessTerminal(CallbackInfoReturnable<?> cir) {
        System.out.println("[EPP][MENU][DEBUG] getShowInAccessTerminal() called; eppAdvancedBlocking=" + this.eppAdvancedBlocking);
    }
}
