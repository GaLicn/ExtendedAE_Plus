package com.extendedae_plus.ae.menu;

import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import com.extendedae_plus.ae.screen.EntitySpeedTickerScreen;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.util.entitySpeed.ConfigParsingUtils;
import com.extendedae_plus.util.entitySpeed.PowerUtils;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 实体加速器菜单，负责管理客户端与服务端的数据同步，处理加速卡、能量卡和目标方块的状态。
 */
public class EntitySpeedTickerMenu extends UpgradeableMenu<EntitySpeedTickerPart> {
    private final EntitySpeedTickerPart logic;
    @GuiSync(716) public int energyCardCount;                   // 已安装的能量卡数量
    @GuiSync(717) public int effectiveSpeed = 1;                // 当前生效的加速倍率
    @GuiSync(718) public double multiplier = 1.0;               // 目标方块的配置倍率
    @GuiSync(719) public boolean targetBlacklisted = false;     // 目标方块是否在黑名单中
    @GuiSync(720) public YesNo networkEnergySufficient;         // 网络能量是否充足
    @GuiSync(721) private YesNo accelerate;                     // 是否启用加速
    @GuiSync(722) private YesNo redstoneControl;                // 是否启用红石控制

    /**
     * 构造函数，初始化菜单并绑定部件。
     *
     * @param id   菜单ID
     * @param ip   玩家背包
     * @param host 关联的实体加速器部件
     */
    public EntitySpeedTickerMenu(int id, Inventory ip, EntitySpeedTickerPart host) {
        super(ModMenuTypes.ENTITY_TICKER_MENU.get(), id, ip, host);
        this.logic = host;
    }

    // 服务端 → 客户端同步时触发（包括第一次打开 GUI）
    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);

        // 客户端只更新「可能变化」的显示相关字段
        // 顺序必须和 broadcastChanges 里一致！
        this.updateTargetStatus();     // multiplier + targetBlacklisted
        this.updateEffectiveSpeed();   // 依赖上面两个字段

        if (this.isClientSide()) {
            this.refreshClientGui();
        }
    }

    /**
     * 当槽位内容变化时调用，客户端更新卡数量和生效速度。
     *
     * @param slot 发生变化的槽位
     */
    @Override
    public void onSlotChange(Slot slot) {
        super.onSlotChange(slot);
        if (this.isClientSide()) {
            // 升级卡变化时，energyCardCount 已经由 AE2 自动同步
            // 重新计算生效速度并刷新 UI
            this.updateEffectiveSpeed();
            this.refreshClientGui();
        }
    }

    /**
     * 广播数据变化，清理未启用槽位的显示堆栈。
     */
    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            // 1. 先更新配置类（开关、红石、能量充足）
            this.accelerate = this.logic.getConfigManager().getSetting(EAPSettings.ACCELERATE);
            this.redstoneControl = this.logic.getConfigManager().getSetting(EAPSettings.REDSTONE_CONTROL);
            this.networkEnergySufficient = this.logic.isNetworkEnergySufficient() ? YesNo.YES : YesNo.NO;

            // 2. 再更新升级卡数量
            this.energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);

            // 3. 最后更新目标状态 + 生效速度
            this.updateTargetStatus();
            this.updateEffectiveSpeed();
        }
        super.broadcastChanges();
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // 不需要模糊模式
    }

    public YesNo getAccelerate() {
        return this.accelerate;
    }

    public YesNo getRedstoneControl() {
        return this.redstoneControl;
    }

    /**
     * 更新目标方块的黑名单状态和倍率。
     */
    private void updateTargetStatus() {
        BlockEntity target = this.getTargetBlockEntity();
        if (target == null) {
            this.multiplier = 1.0;
            this.targetBlacklisted = false;
            return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()).toString();
        this.multiplier = ConfigParsingUtils.getMultiplierForBlock(blockId, ModConfigs.ENTITY_TICKER_MULTIPLIERS.get());
        this.targetBlacklisted = ConfigParsingUtils.isBlockBlacklisted(blockId, ModConfigs.ENTITY_TICKER_BLACK_LIST.get());
    }

    /**
     * 计算生效速度（考虑黑名单和卡数量）。
     */
    private void updateEffectiveSpeed() {
        this.effectiveSpeed =
                this.targetBlacklisted ? 0 : (int) PowerUtils.computeProductWithCap(this.getUpgrades(), 8);
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
     *
     * @return 目标方块实体或 null
     */
    private BlockEntity getTargetBlockEntity() {
        return this.getHost() != null ?
                this.getHost().getLevel().getBlockEntity(
                        this.getHost().getBlockEntity().getBlockPos().relative(this.getHost().getSide())
                ) : null;
    }
}