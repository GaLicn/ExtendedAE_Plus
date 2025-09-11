package com.extendedae_plus.ae.menu;

import appeng.core.definitions.AEItems;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.OptionalFakeSlot;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import com.extendedae_plus.ae.screen.EntitySpeedTickerScreen;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.util.ConfigParsingUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

// 实体加速器菜单，负责与客户端界面同步数据
public class EntitySpeedTickerMenu extends UpgradeableMenu<EntitySpeedTickerPart> {
    // 已安装的速度卡数量
    public int speedCardCount;
    // 已安装的能量卡数量
    public int energyCardCount;
    // 当前生效的倍率（从配置中读取并同步）
    public double multiplier = 1.0;

    // 构造方法，初始化菜单并与部件绑定
    public EntitySpeedTickerMenu(int id, Inventory ip, EntitySpeedTickerPart host) {
        super(ModMenuTypes.ENTITY_TICKER_MENU.get(), id, ip, host);
        // 让部件持有当前菜单实例，便于通信
        getHost().menu = this;
    }

    // 当服务器数据同步到客户端时调用
    @Override
    public void onServerDataSync() {
        super.onServerDataSync();
        // 重新统计速度卡和能量卡数量
        this.speedCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD);
        this.energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);

        // 计算当前面向方块的倍率（服务器端），并同步给客户端
        double mult = 1.0;
        try {
            BlockEntity target = getHost().getLevel().getBlockEntity(getHost().getBlockEntity().getBlockPos().relative(getHost().getSide()));
            if (target != null) {
                String blockId = ForgeRegistries.BLOCKS.getKey(target.getBlockState().getBlock()).toString();
                for (ConfigParsingUtils.MultiplierEntry me : ConfigParsingUtils.getCachedMultiplierEntries(ModConfigs.EntitySpeedTickerMultipliers.get())) {
                    if (me.pattern.matcher(blockId).matches()) {
                        mult = Math.max(mult, me.multiplier);
                    }
                }
            }
        } catch (Exception ignored) {}
        this.multiplier = mult;

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
            this.speedCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD);
            this.energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
            if (Minecraft.getInstance().screen instanceof EntitySpeedTickerScreen screen) {
                screen.refreshGui();
            }
        }
    }

    @Override
    public void broadcastChanges(){
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