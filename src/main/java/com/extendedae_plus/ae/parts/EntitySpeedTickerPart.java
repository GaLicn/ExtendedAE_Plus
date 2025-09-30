package com.extendedae_plus.ae.parts;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.YesNo;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.util.entitySpeed.ConfigParsingUtils;
import com.extendedae_plus.util.entitySpeed.PowerUtils;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * 实体加速器部件，用于加速目标方块实体的 tick 速率，消耗 AE 网络能量，支持加速卡和能量卡升级。
 * 灵感来源于 <a href="https://github.com/GilbertzRivi/crazyae2addons">Crazy AE2 Addons</a>。
 */
public class EntitySpeedTickerPart extends UpgradeablePart implements IGridTickable, MenuProvider, IUpgradeableObject {
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(
            ExtendedAEPlus.MODID, "part/entity_speed_ticker_part");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(ExtendedAEPlus.MODID, "part/entity_speed_ticker_off"));
    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(ExtendedAEPlus.MODID, "part/entity_speed_ticker_on"));
    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(ExtendedAEPlus.MODID, "part/entity_speed_ticker_has_channel"));

    private static volatile boolean[] cachedAttempts;

    private static volatile Method cachedFEExtractMethod;
    private static volatile boolean FE_UNAVAILABLE;

    // 静态块：初始化缓存
    static {
        // 优先磁盘 -> FE 然后 AE；否则 AE 然后 FE
        cachedAttempts = ModConfig.INSTANCE.prioritizeDiskEnergy ?
                new boolean[]{ true, true, false } :
                new boolean[]{ false, true, true };
        if (ModList.get().isLoaded("appflux")) {
            try {
                Class<?> helperClass = Class.forName("com.extendedae_plus.util.FluxEnergyHelper");
                cachedFEExtractMethod = helperClass.getMethod(
                        "extractFE",
                        IEnergyService.class,
                        MEStorage.class,
                        long.class,
                        IActionSource.class
                );
                FE_UNAVAILABLE = false;
            } catch (Exception e) {
                FE_UNAVAILABLE = true;
            }
        }
    }

    public EntitySpeedTickerMenu menu;              // 当前打开的菜单实例
    private boolean networkEnergySufficient = true; // 网络能量是否充足
    private int cachedSpeed = -1;                    // 缓存的加速倍率
    private int cachedEnergyCardCount = -1;          // 缓存的能量卡数量
    private BlockEntity cachedTarget = null;
    private BlockPos cachedTargetPos = null;

    /**
     * 构造函数，初始化部件并设置网络节点属性。
     *
     * @param partItem 部件物品
     */
    public EntitySpeedTickerPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1)
                .addService(IGridTickable.class, this);

        // 注册可记忆的配置（YES/NO）
        this.getConfigManager().registerSetting(
                com.extendedae_plus.ae.api.config.Settings.ACCELERATE,
                YesNo.YES
        );
    }

    /**
     * 更新缓存的 attempts 数组，由 ModConfig 调用。
     */
    public static void updateCachedAttempts(boolean prioritizeDiskEnergy) {
        synchronized (EntitySpeedTickerPart.class) {
            // 优先磁盘 -> FE 然后 AE；否则 AE 然后 FE
            cachedAttempts = prioritizeDiskEnergy ?
                    new boolean[]{ true, true, false } :
                    new boolean[]{ false, true, true };
        }
    }


    public boolean getAccelerateEnabled() {
        return this.getConfigManager().getSetting(com.extendedae_plus.ae.api.config.Settings.ACCELERATE) == YesNo.YES;
    }

    /**
     * 设置加速开关状态并通知菜单。
     *
     * @param enabled 是否启用加速
     */
    public void setAccelerateEnabled(boolean enabled) {
        this.getConfigManager().putSetting(com.extendedae_plus.ae.api.config.Settings.ACCELERATE, enabled ? YesNo.YES : YesNo.NO);
        if (menu != null) {
            menu.setAccelerateEnabled(enabled);
        }
    }

    public boolean isNetworkEnergySufficient() {
        return this.networkEnergySufficient;
    }

    /**
     * 更新网络能量充足状态并通知菜单。
     *
     * @param sufficient 是否能量充足
     */
    private void updateNetworkEnergySufficient(boolean sufficient) {
        this.networkEnergySufficient = sufficient;
        if (menu != null) {
            menu.setNetworkEnergySufficient(sufficient);
        }
    }

    /**
     * 获取当前状态的渲染模型。
     *
     * @return 当前状态的模型
     */
    @Override
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
     * 处理玩家右键激活部件，打开菜单。
     *
     * @param player 玩家
     * @param hand   手
     * @param pos    点击位置
     * @return 总是返回 true
     */
    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (!player.getCommandSenderWorld().isClientSide()) {
            MenuOpener.open(ModMenuTypes.ENTITY_TICKER_MENU.get(), player, MenuLocators.forPart(this));
        }
        return true;
    }

    /**
     * 定义部件的碰撞箱。
     *
     * @param bch 碰撞辅助器
     */
    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    /**
     * 获取定时请求，指定 tick 频率。
     *
     * @param iGridNode 网络节点
     * @return TickingRequest 对象
     */
    @Override
    public TickingRequest getTickingRequest(IGridNode iGridNode) {
        return new TickingRequest(1, 1, false, true);
    }

    /**
     * 当升级卡变化时通知菜单更新。
     */
    @Override
    public void upgradesChanged() {
        // 更新缓存的升级卡数量和加速倍率
        this.cachedEnergyCardCount = getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
        this.cachedSpeed = calculateSpeed();

        if (menu != null) {
            menu.broadcastChanges();
        }
    }

    /**
     * 网络定时回调，处理目标方块实体的加速。
     *
     * @param iGridNode          网络节点
     * @param ticksSinceLastCall 距离上次调用的 tick 数
     * @return TickRateModulation.IDLE
     */
    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int ticksSinceLastCall) {
        if (!getAccelerateEnabled()) {
            return TickRateModulation.IDLE;
        }
        updateCachedTarget();
        if (cachedTarget != null && isActive()) {
            ticker(cachedTarget);
        }
        return TickRateModulation.IDLE;
    }

    /**
     * 对目标方块实体执行加速 tick 操作。
     *
     * @param blockEntity 目标方块实体
     * @param <T>         方块实体类型
     */
    private <T extends BlockEntity> void ticker(@NotNull T blockEntity) {
        if (!isValidForTicking()) {
            return;
        }

        String blockId = ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock()).toString();
        if (ConfigParsingUtils.isBlockBlacklisted(blockId)) {
            return;
        }

        BlockEntityTicker<T> ticker = getTicker(blockEntity);
        if (ticker == null) {
            return;
        }

        if (cachedEnergyCardCount == -1 || cachedSpeed == -1) {
            this.cachedEnergyCardCount = getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
            this.cachedSpeed = calculateSpeed();
        }

        if (cachedSpeed <= 0) {
            return;
        }

        double requiredPower = PowerUtils.getCachedPower(cachedSpeed, cachedEnergyCardCount)
                * ConfigParsingUtils.getMultiplierForBlock(blockId);
        if (!extractPower(requiredPower)) {
            return;
        }

        performTicks(blockEntity, ticker, cachedSpeed);
    }

    /**
     * 检查网络节点是否有效。
     *
     * @return 是否可以执行 tick
     */
    private boolean isValidForTicking() {
        return getGridNode() != null && getMainNode() != null && getMainNode().getGrid() != null;
    }

    /**
     * 更新缓存的目标方块实体引用。
     */
    private void updateCachedTarget() {
        BlockPos targetPos = getBlockEntity().getBlockPos().relative(getSide());
        if (!targetPos.equals(cachedTargetPos) || cachedTarget == null || cachedTarget.isRemoved() ||
                cachedTarget.getType() != getLevel().getBlockEntity(targetPos).getType()) {
            cachedTargetPos = targetPos;
            cachedTarget = getLevel().getBlockEntity(targetPos);
        }
    }

    /**
     * 获取目标方块实体的 ticker。
     *
     * @param blockEntity 目标方块实体
     * @return ticker 或 null
     */
    private <T extends BlockEntity> BlockEntityTicker<T> getTicker(T blockEntity) {
        return getLevel().getBlockState(blockEntity.getBlockPos())
                .getTicker(getLevel(), (BlockEntityType<T>) blockEntity.getType());
    }

    /**
     * 计算加速倍率。
     *
     * @return 生效的加速倍率
     */
    private int calculateSpeed() {
        int entitySpeedCardCount = getUpgrades().getInstalledUpgrades(ModItems.ENTITY_SPEED_CARD.get());
        if (entitySpeedCardCount <= 0) return 0;
        return PowerUtils.computeProductWithCap(getUpgrades(), 8);
    }

    /**
     * 提取网络能量并更新状态，优先从 AE2 网络提取 AE 能量，不足时从磁盘提取 FE 能量。
     *
     * @param requiredPower 所需能量（AE 单位）
     * @return 是否成功提取足够能量
     */
    private boolean extractPower(double requiredPower) {
        IEnergyService energyService = getMainNode().getGrid().getEnergyService();
        MEStorage storage = getMainNode().getGrid().getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(this);

        for (int i = 0; i < cachedAttempts.length; i++) {
            if (!cachedAttempts[i]) continue;

            // FE 提取
            if ((i == 0 || i == 2) && !FE_UNAVAILABLE && cachedFEExtractMethod != null) {
                try {
                    long feRequired = (long) requiredPower << 1;
                    long feExtracted = (long) cachedFEExtractMethod.invoke(null, energyService, storage, feRequired, source);
                    if (feExtracted >= feRequired) {
                        updateNetworkEnergySufficient(true);
                        return true;
                    }
                } catch (Exception e) {
                    FE_UNAVAILABLE = true;
                    continue;
                }
            }
            // AE 提取
            else {
                double simulated = energyService.extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                if (simulated >= requiredPower) { // 模拟足够
                    double extracted = energyService.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    boolean sufficient = extracted >= requiredPower;
                    updateNetworkEnergySufficient(sufficient);
                    if (sufficient) return true;
                }
            }
        }

        // 所有尝试都不够
        updateNetworkEnergySufficient(false);
        return false;
    }


    /**
     * 执行加速 tick 操作。
     *
     * @param blockEntity 目标方块实体
     * @param ticker      方块实体 ticker
     * @param speed       加速倍率
     */
    private <T extends BlockEntity> void performTicks(T blockEntity,
                                                      BlockEntityTicker<T> ticker,
                                                      int speed) {
        for (int i = 0; i < speed - 1; i++) {
            ticker.tick(
                    blockEntity.getLevel(),
                    blockEntity.getBlockPos(),
                    blockEntity.getBlockState(),
                    blockEntity
            );
        }
    }

    @Override
    public boolean hasCustomName() {
        return super.hasCustomName();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return super.getDisplayName();
    }

    /**
     * 创建菜单实例。
     *
     * @param containerId     容器ID
     * @param playerInventory 玩家背包
     * @param player          玩家
     * @return 菜单实例
     */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId,
                                                      @NotNull Inventory playerInventory,
                                                      @NotNull Player player) {
        return new EntitySpeedTickerMenu(containerId, playerInventory, this);
    }

    /**
     * 获取升级卡槽数量。
     *
     * @return 升级卡槽数量
     */
    @Override
    protected int getUpgradeSlots() {
        return 8;
    }

    @Override
    public void removeFromWorld() {
        super.removeFromWorld();
        cachedTarget = null;
        cachedTargetPos = null;
    }
}