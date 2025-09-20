package com.extendedae_plus.ae.menu;

import appeng.core.definitions.AEItems;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.OptionalFakeSlot;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import com.extendedae_plus.ae.screen.EntitySpeedTickerScreen;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.util.ConfigParsingUtils;
import com.extendedae_plus.util.PowerUtils;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

// 实体加速器菜单，负责与客户端界面同步数据
public class EntitySpeedTickerMenu extends UpgradeableMenu<EntitySpeedTickerPart> {
    @GuiSync(716)
    public boolean accelerateEnabled = true;
    // 已安装的实体加速卡数量（用于能耗计算）
    @GuiSync(717)
    public int entitySpeedCardCount;
    // 已安装的能量卡数量
    @GuiSync(718)
    public int energyCardCount;
    // 当前生效的配置倍率（从配置中读取并同步）
    // 当前计算出的生效速度（product of multipliers），同步给客户端用于显示
    @GuiSync(719)
    public int effectiveSpeed = 1;
    @GuiSync(720)
    public double multiplier = 1.0;
    @GuiSync(721)
    public boolean targetBlacklisted = false;

    // 构造方法，初始化菜单并与部件绑定
    public EntitySpeedTickerMenu(int id, Inventory ip, EntitySpeedTickerPart host) {
        super(ModMenuTypes.ENTITY_TICKER_MENU.get(), id, ip, host);
        // 让部件持有当前菜单实例，便于通信
        getHost().menu = this;
        // 初始同步部件上的开关状态到菜单（服务器端构造时保证一致）
        try {
            this.accelerateEnabled = getHost().getAccelerateEnabled();
        } catch (Exception ignored) {
        }
    }

    public boolean getAccelerateEnabled() {
        return this.accelerateEnabled;
    }

    public void setAccelerateEnabled(boolean enabled) {
        this.accelerateEnabled = enabled;
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        // 重新统计实体加速卡和能量卡数量
        this.entitySpeedCardCount = this.getUpgrades().getInstalledUpgrades(ModItems.ENTITY_SPEED_CARD.get());
        this.energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);

        // 计算当前面向方块的倍率（服务器端），并同步给客户端
        double mult = 1.0;
        try {
            BlockEntity target = getHost().getLevel().getBlockEntity(
                    getHost().getBlockEntity().getBlockPos().relative(getHost().getSide())
            );
            if (target != null) {
                String blockId = BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()).toString();
                for (ConfigParsingUtils.MultiplierEntry me :
                        ConfigParsingUtils.getCachedMultiplierEntries(ModConfigs.ENTITY_TICKER_MULTIPLIERS.get())) {
                    if (me.pattern.matcher(blockId).matches()) {
                        mult = Math.max(mult, me.multiplier);
                    }
                }
            }
        } catch (Exception ignored) {}
        this.multiplier = mult;

        // 检查目标是否在黑名单中，如果是则标记并将生效速度设为 0（服务器端计算）
        boolean blacklisted = false;
        try {
            BlockEntity target = getHost().getLevel().getBlockEntity(
                    getHost().getBlockEntity().getBlockPos().relative(getHost().getSide())
            );
            if (target != null) {
                Block block = target.getBlockState().getBlock();
                String blockId = BuiltInRegistries.BLOCK.getKey(block).toString(); // 直接拿到 "minecraft:stone"

                for (java.util.regex.Pattern p : ConfigParsingUtils.getCachedBlacklist(
                        ModConfigs.ENTITY_TICKER_BLACK_LIST.get())) {
                    if (p.matcher(blockId).matches()) {
                        blacklisted = true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        this.targetBlacklisted = blacklisted;

        // 计算生效速度：如果被黑名单则为 0，否则进行正常计算（使用工具类从菜单直接计算 product with cap，最多 8 张）
        if (this.targetBlacklisted) {
            this.effectiveSpeed = 0;
        } else {
            this.effectiveSpeed = (int) PowerUtils.computeProductWithCapFromMenu(this, 8);
        }

        // 如果在客户端，刷新界面
        if (isClientSide()) {
            if (Minecraft.getInstance().screen instanceof EntitySpeedTickerScreen screen) {
                screen.refreshGui();
            }
        }
    }

    // 当任意槽位发生变化时调用
    @Override
    public void onSlotChange(net.minecraft.world.inventory.Slot slot) {
        super.onSlotChange(slot);
        // 客户端重新统计卡数量并刷新界面
        if (isClientSide()) {
            this.entitySpeedCardCount = this.getUpgrades().getInstalledUpgrades(ModItems.ENTITY_SPEED_CARD.get());
            this.energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
            // 立即在客户端计算生效速度以便界面即时反馈（使用与服务端相同的工具方法，最多 8 张卡）
            this.effectiveSpeed = (int) PowerUtils.computeProductWithCapFromMenu(this, 8);
            if (Minecraft.getInstance().screen instanceof EntitySpeedTickerScreen screen) {
                screen.refreshGui();
            }
        }
    }

    @Override
    public void broadcastChanges() {
        // 遍历所有槽位，清理未启用但有物品显示的 OptionalFakeSlot
        for (Object o : this.slots) {
            if (o instanceof OptionalFakeSlot fs) {
                if (!fs.isSlotEnabled() && !fs.getDisplayStack().isEmpty()) {
                    fs.clearStack();
                }
            }
        }
        // 调用标准的同步方法，通知监听者数据已更新
        this.standardDetectAndSendChanges();
    }
}