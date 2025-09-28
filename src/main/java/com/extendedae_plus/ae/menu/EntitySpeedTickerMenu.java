package com.extendedae_plus.ae.menu;

import appeng.core.definitions.AEItems;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.OptionalFakeSlot;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import com.extendedae_plus.ae.screen.EntitySpeedTickerScreen;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.util.entitySpeed.ConfigParsingUtils;
import com.extendedae_plus.util.entitySpeed.PowerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 实体加速器菜单，负责管理客户端与服务端的数据同步，处理加速卡、能量卡和目标方块的状态。
 */
public class EntitySpeedTickerMenu extends UpgradeableMenu<EntitySpeedTickerPart> {
    @GuiSync(716) public boolean accelerateEnabled = true;       // 是否启用加速
    @GuiSync(717) public int entitySpeedCardCount;               // 已安装的实体加速卡数量
    @GuiSync(718) public int energyCardCount;                    // 已安装的能量卡数量
    @GuiSync(719) public int effectiveSpeed = 1;                 // 当前生效的加速倍率
    @GuiSync(720) public double multiplier = 1.0;                // 目标方块的配置倍率
    @GuiSync(721) public boolean targetBlacklisted = false;      // 目标方块是否在黑名单中
    @GuiSync(722) public boolean networkEnergySufficient = true; // 网络能量是否充足

    /**
     * 构造函数，初始化菜单并绑定部件。
     * @param id 菜单ID
     * @param ip 玩家背包
     * @param host 关联的实体加速器部件
     */
    public EntitySpeedTickerMenu(int id, Inventory ip, EntitySpeedTickerPart host) {
        super(ModMenuTypes.ENTITY_TICKER_MENU.get(), id, ip, host);
        if (host != null) {
            host.menu = this; // 绑定菜单到部件
            this.accelerateEnabled = host.getAccelerateEnabled(); // 同步初始开关状态
        }
    }

    /**
     * 获取加速开关状态。
     * @return 是否启用加速
     */
    public boolean getAccelerateEnabled() {
        return this.accelerateEnabled;
    }

    /**
     * 设置加速开关状态，并同步到部件。
     * @param enabled 是否启用加速
     */
    public void setAccelerateEnabled(boolean enabled) {
        this.accelerateEnabled = enabled;
        if (getHost() != null) {
            getHost().setAccelerateEnabled(enabled); // 同步到部件
        }
        broadcastChanges(); // 广播状态变化
    }

    /**
     * 更新网络能量充足状态并广播到客户端。
     * @param sufficient 是否能量充足
     */
    public void setNetworkEnergySufficient(boolean sufficient) {
        this.networkEnergySufficient = sufficient;
        broadcastChanges();
    }

    /**
     * 服务端数据同步到客户端时调用，更新卡数量、目标状态和生效速度。
     */
    @Override
    public void onServerDataSync() {
        super.onServerDataSync();
        updateCardCounts();          // 更新卡数量
        updateTargetStatus();        // 更新目标方块的黑名单和倍率
        updateEffectiveSpeed();      // 计算生效速度
        updateNetworkEnergyStatus(); // 同步能量状态
        if (isClientSide()) {
            refreshClientGui();      // 客户端刷新界面
        }
    }

    /**
     * 当槽位内容变化时调用，客户端更新卡数量和生效速度。
     * @param slot 发生变化的槽位
     */
    @Override
    public void onSlotChange(Slot slot) {
        super.onSlotChange(slot);
        if (isClientSide()) {
            updateCardCounts();
            updateEffectiveSpeed();
            refreshClientGui();
        }
    }

    /**
     * 广播数据变化，清理未启用槽位的显示堆栈。
     */
    @Override
    public void broadcastChanges() {
        for (Object o : this.slots) {
            if (o instanceof OptionalFakeSlot fs && !fs.isSlotEnabled() && !fs.getDisplayStack().isEmpty()) {
                fs.clearStack(); // 清理未启用槽位的显示
            }
        }
        standardDetectAndSendChanges();
    }

    /**
     * 更新加速卡和能量卡的数量。
     */
    private void updateCardCounts() {
        this.entitySpeedCardCount = this.getUpgrades().getInstalledUpgrades(ModItems.ENTITY_SPEED_CARD.get());
        this.energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
    }

    /**
     * 更新目标方块的黑名单状态和倍率。
     */
    private void updateTargetStatus() {
        BlockEntity target = getTargetBlockEntity();
        if (target == null) {
            this.multiplier = 1.0;
            this.targetBlacklisted = false;
            return;
        }
        String blockId = ForgeRegistries.BLOCKS.getKey(target.getBlockState().getBlock()).toString();
        this.multiplier = ConfigParsingUtils.getMultiplierForBlock(blockId, List.of(ModConfig.INSTANCE.entityTickerMultipliers));
        this.targetBlacklisted = ConfigParsingUtils.isBlockBlacklisted(blockId, List.of(ModConfig.INSTANCE.entityTickerBlackList));
    }

    /**
     * 计算生效速度（考虑黑名单和卡数量）。
     */
    private void updateEffectiveSpeed() {
        this.effectiveSpeed = targetBlacklisted ? 0 : (int) PowerUtils.computeProductWithCap(getUpgrades(), 8);
    }

    /**
     * 同步网络能量状态（仅服务端）。
     */
    private void updateNetworkEnergyStatus() {
        if (!isClientSide() && getHost() != null) {
            this.networkEnergySufficient = getHost().isNetworkEnergySufficient();
        }
    }

    /**
     * 客户端刷新界面。
     */
    private void refreshClientGui() {
        if (Minecraft.getInstance().screen instanceof EntitySpeedTickerScreen screen) {
            screen.refreshGui();
        }
    }

    /**
     * 获取目标方块实体。
     * @return 目标方块实体或 null
     */
    private BlockEntity getTargetBlockEntity() {
        return getHost() != null ?
                getHost().getLevel().getBlockEntity(
                        getHost().getBlockEntity().getBlockPos().relative(getHost().getSide())
                ) : null;
    }
}