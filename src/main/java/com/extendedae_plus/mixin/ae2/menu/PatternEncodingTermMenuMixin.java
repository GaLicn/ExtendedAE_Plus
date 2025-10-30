package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.extendedae_plus.mixin.ae2.accessor.MEStorageMenuAccessor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 对 AE2 的 PatternEncodingTermMenu 添加自动补充空白样板功能。
 * 当玩家打开样板终端时，如果槽位为空且网络中存在空白样板，则会自动从网络提取若干放入槽中。
 * 若初始化时网络尚未激活，则会在第一次同步（broadcastChanges）时重试一次。
 */
@Mixin(PatternEncodingTermMenu.class)
public abstract class PatternEncodingTermMenuMixin {

    /** 空白样板槽位（AE2原字段） */
    @Final
    @Shadow(remap = false)
    private RestrictedInputSlot blankPatternSlot;

    /** 防止重复自动填充的标记位 */
    @Unique
    private boolean eap$blankAutoFilled = false;

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V",
            at = @At("TAIL"), remap = false)
    private void eap$onCtor1(MenuType<?> menuType, int id, Inventory ip, IPatternTerminalMenuHost host,
                             boolean bindInventory, CallbackInfo ci) {
        eap$tryAutoFill(ip, host, false);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;)V",
            at = @At("TAIL"), remap = false)
    private void eap$onCtor2(int id, Inventory ip, IPatternTerminalMenuHost host, CallbackInfo ci) {
        eap$tryAutoFill(ip, host, false);
    }

    /**
     * 当容器同步（broadcastChanges）时再尝试一次自动填充。
     * 某些情况下，构造时网络尚未激活（storage/power 为空），
     * 因此需要延迟到第一次同步后执行补偿填充。
     */
    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void eap$onBroadcastChanges(CallbackInfo ci) {
        if (!eap$blankAutoFilled) {
            var self = (PatternEncodingTermMenu) (Object) this;
            var player = self.getPlayerInventory().player;

            // 仅在服务端执行逻辑
            if (!player.level().isClientSide()) {
                eap$tryAutoFill(self.getPlayerInventory(), (IPatternTerminalMenuHost) self.getTarget(), true);
            }
        }
    }

    /**
     * 尝试自动从网络提取空白样板并填入终端槽位。
     *
     * @param ip          玩家背包对象（用于获取 Player 实例）
     * @param host        菜单宿主（提供 blankPatternInv 等逻辑访问）
     * @param markFilled  是否在执行后标记为“已填充过”（用于 broadcastChanges 重试逻辑）
     */
    @Unique
    private void eap$tryAutoFill(Inventory ip, IPatternTerminalMenuHost host, boolean markFilled) {
        try {
            var self = (PatternEncodingTermMenu) (Object) this;
            var player = ip.player;

            // 跳过客户端执行（避免无效提取）
            if (player.level().isClientSide()) return;

            // 获取 AE2 存储访问接口
            var accessor = (MEStorageMenuAccessor) self;
            MEStorage storage = accessor.getStorage();
            IEnergySource power = accessor.getPowerSource();

            // 无法交互（例如网络未激活或无电力）则跳过
            if (storage == null || power == null || !accessor.getHasPower()) return;

            // 获取空白样板的专用内部槽
            InternalInventory blankInv = host.getLogic().getBlankPatternInv();
            var current = blankInv.getStackInSlot(0);
            int limit = blankInv.getSlotLimit(0);

            // 计算当前剩余可放入数量（不超过物品最大堆叠）
            int space = Math.min(Math.max(0, limit - current.getCount()),
                    AEItems.BLANK_PATTERN.asItem().getMaxStackSize());

            // 槽位已满时直接跳过
            if (space <= 0) {
                if (markFilled) eap$blankAutoFilled = true;
                return;
            }

            // 从网络中提取空白样板
            AEKey blankKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
            long extracted = StorageHelper.poweredExtraction(power, storage, blankKey, space, self.getActionSource());
            if (extracted <= 0) return;

            // 插入到空白样板槽中
            int toInsert = (int) Math.min(extracted, space);
            var slotStack = blankPatternSlot.getItem();
            if (slotStack.isEmpty()) {
                blankPatternSlot.set(AEItems.BLANK_PATTERN.stack(toInsert));
            } else {
                slotStack.grow(toInsert);
                blankPatternSlot.set(slotStack);
            }

            // 若存在提取但未放入完的剩余物品，退还到网络中
            long leftover = extracted - toInsert;
            if (leftover > 0) {
                StorageHelper.poweredInsert(power, storage, blankKey, leftover, self.getActionSource());
            }

            // 成功或确定无剩余后标记为已填充，防止重复执行
            if (markFilled) eap$blankAutoFilled = true;

        } catch (Exception ignored) {
            // 安全兜底，防止 AE2 网络状态异常或空指针导致的崩溃
        }
    }
}
