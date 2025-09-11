package com.extendedae_plus.ae.parts;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.util.ConfigParsingUtils;
import com.extendedae_plus.util.PowerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * EntitySpeedTickerPart 是一个可升级的 AE2 部件<p>
 * 该部件可以加速目标方块实体的 tick 速率，消耗 AE 网络能量，并支持加速卡升级<p>
 * 功能受<a href="https://github.com/GilbertzRivi/crazyae2addons">Crazy AE2 Addons</a>启发
 */
public class EntitySpeedTickerPart extends UpgradeablePart implements IGridTickable, MenuProvider, IUpgradeableObject {
    // 当前打开的菜单实例（如果有）
    public EntitySpeedTickerMenu menu;

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(
            ExtendedAEPlus.MODID, "part/entity_speed_ticker_part");

    @PartModels
    public static final PartModel MODELS_OFF;
    @PartModels
    public static final PartModel MODELS_ON;
    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL;
    
    static {
        MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(ExtendedAEPlus.MODID, "part/entity_speed_ticker_off"));
        MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(ExtendedAEPlus.MODID, "part/entity_speed_ticker_on"));
        MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(ExtendedAEPlus.MODID, "part/entity_speed_ticker_has_channel"));
    }
    
    /**
     * 构造函数，初始化部件并设置网络节点属性
     * @param partItem 部件物品
     */
    public EntitySpeedTickerPart(IPartItem<?> partItem) {
        super(partItem);
        // 设置网络节点属性：需要通道、空闲功耗为1，并注册为 IGridTickable 服务
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1)
                .addService(IGridTickable.class, this);
    }

    /**
     * 获取当前状态下的静态模型（用于渲染）
     * @return 当前状态的模型
     */
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    /**
     * 当玩家激活部件（右键）时调用，打开自定义菜单
     * @param player 玩家
     * @param hand 手
     * @param pos 点击位置
     * @return 总是返回 true，表示激活成功
     */
    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        // 仅在服务端打开菜单
        if (!player.getCommandSenderWorld().isClientSide()) {
            MenuOpener.open(ModMenuTypes.ENTITY_TICKER_MENU.get(), player, MenuLocators.forPart(this));
        }
        return true;
    }

    /**
     * 定义部件的碰撞箱（用于物理碰撞和渲染）
     * @param bch 碰撞辅助器
     */
    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    /**
     * 获取定时请求，决定本部件多久 tick 一次
     * @param iGridNode 网络节点
     * @return TickingRequest 对象
     */
    @Override
    public TickingRequest getTickingRequest(IGridNode iGridNode) {
        // 每 1 tick 执行一次
        return new TickingRequest(1, 1, false, true);
    }

    /**
     * 当升级卡数量发生变化时调用，通知菜单更新
     */
    @Override
    public void upgradesChanged() {
        if (this.menu != null) {
            // 使用 AE2 风格：当升级发生变化时让菜单广播变化（槽/数据会被同步），客户端会基于槽内容重新计算并刷新界面
            this.menu.broadcastChanges();
        }
    }

    /**
     * 网络定时回调，每次 tick 时调用
     * @param iGridNode 网络节点
     * @param ticksSinceLastCall 距离上次调用经过的 tick 数
     * @return TickRateModulation.IDLE 表示继续保持当前 tick 速率
     */
    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int ticksSinceLastCall) {
        // 获取目标方块实体（本部件朝向的方块）
        BlockEntity target = getLevel().getBlockEntity(getBlockEntity().getBlockPos().relative(getSide()));
        // 仅在目标存在且部件处于激活状态时执行加速
        if (target != null && isActive()) {
            ticker(target);
        }
        return TickRateModulation.IDLE;
    }

    /**
     * 以指定速度对目标方块实体进行 tick 操作
     * @param blockEntity 需要被 tick 的方块实体
     * @param <T> 方块实体类型
     */
    private <T extends BlockEntity> void ticker(@NotNull T blockEntity) {
        if (this.getGridNode() == null
                || this.getMainNode() == null
                || this.getMainNode().getGrid() == null) {
            return;
        }

        // 获取方块实体的位置
        BlockPos pos = blockEntity.getBlockPos();
        if (blockEntity.getLevel() == null) return;

        // 检查黑名单（支持通配符/正则）
        String blockId = Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock())).toString();

        // 使用工具类的缓存接口（工具类内部负责懒加载/线程安全）
        List<Pattern> compiledBlacklist = ConfigParsingUtils.getCachedBlacklist(ModConfigs.EntitySpeedTickerBlackList.get());
        for (Pattern p : compiledBlacklist) {
            if (p.matcher(blockId).matches()) return;
        }

        // 获取该方块实体的 Ticker
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> blockEntityTicker = this.getLevel()
                .getBlockState(pos)
                .getTicker(this.getLevel(), (BlockEntityType<T>) blockEntity.getType());
        if (blockEntityTicker == null) return;

        int speedCardCount = getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD);
        int energyCardCount = getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);

        // 计算本次 tick 所需能量
        // - 基础消耗为 512
        // - 加速卡的数量对能耗有分段增长：
        //     0: 无增长
        //     1: 翻倍
        //     2-6: 指数增长（较快）
        //     7-8: 幂级数增长（极高）
        // 使用工具类统一计算增长因子与原始功耗，并从网络中抽取对应能量
        double requiredPower = PowerUtils.getFinalPower(speedCardCount, energyCardCount);

        double multiplier = 1.0;
        for (ConfigParsingUtils.MultiplierEntry me : ConfigParsingUtils.getCachedMultiplierEntries(ModConfigs.EntitySpeedTickerMultipliers.get())) {
            if (me.pattern.matcher(blockId).matches()) {
                multiplier = Math.max(multiplier, me.multiplier);
            }
        }

        requiredPower *= multiplier;

        // 先模拟提取以检查网络中是否有足够能量，再真正抽取
        double simulated = getMainNode().getGrid().getEnergyService()
                .extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (simulated < requiredPower) return;

        double extractedPower = getMainNode().getGrid().getEnergyService()
                .extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (extractedPower < requiredPower) return;

        // 计算加速倍数：基于 2 的次方，并把 8 张映射到最大 1024x（2^10）
        int speed = PowerUtils.getSpeedMultiplier(speedCardCount);

        // 执行 tick 操作
        for (int i = 0; i < speed - 1; i++) {
            blockEntityTicker.tick(
                    blockEntity.getLevel(),
                    blockEntity.getBlockPos(),
                    blockEntity.getBlockState(),
                    blockEntity
            );
        }
    }

    /**
     * 判断部件是否有自定义名称
     * @return 是否有自定义名称
     */
    @Override
    public boolean hasCustomName() {
        return super.hasCustomName();
    }

    /**
     * 获取部件的显示名称
     * @return 显示名称
     */
    @Override
    public @NotNull Component getDisplayName() {
        return super.getDisplayName();
    }

    /**
     * 创建自定义菜单（GUI）
     * @param containerId 容器ID
     * @param playerInventory 玩家背包
     * @param player 玩家
     * @return 菜单实例
     */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId,
                                                      @NotNull Inventory playerInventory,
                                                      @NotNull Player player) {
        return new EntitySpeedTickerMenu(containerId, playerInventory, this);
    }

    /**
     * 获取可用的升级卡槽数量
     * @return 升级卡槽数量
     */
    @Override
    protected int getUpgradeSlots() {
        return 8;
    }
}