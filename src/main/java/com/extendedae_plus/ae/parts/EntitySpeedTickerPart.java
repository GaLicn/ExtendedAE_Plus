package com.extendedae_plus.ae.parts;


import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Setting;
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
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigManagerBuilder;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.util.ExtendedAELogger;
import com.extendedae_plus.util.entitySpeed.ConfigParsingUtils;
import com.extendedae_plus.util.entitySpeed.PowerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * EntitySpeedTickerPart 是一个可升级的 AE2 部件<p>
 * 该部件可以加速目标方块实体的 tick 速率，消耗 AE 网络能量，并支持加速卡升级<p>
 * 功能受<a href="https://github.com/GilbertzRivi/crazyae2addons">Crazy AE2 Addons</a>启发
 */
public class EntitySpeedTickerPart extends UpgradeablePart implements IGridTickable, MenuProvider, IUpgradeableObject {
    private static final ResourceLocation MODEL_BASE = ResourceLocation.fromNamespaceAndPath(
            ExtendedAEPlus.MODID, "part/entity_speed_ticker_part");
    @PartModels
    private static final PartModel MODELS_OFF;
    @PartModels
    private static final PartModel MODELS_ON;
    @PartModels
    private static final PartModel MODELS_HAS_CHANNEL;

    static {
        MODELS_OFF = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "part/entity_speed_ticker_off"));
        MODELS_ON = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "part/entity_speed_ticker_on"));
        MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "part/entity_speed_ticker_has_channel"));
    }

    public EntitySpeedTickerMenu menu;              // 当前打开的菜单实例
    private YesNo networkEnergySufficient = YesNo.YES; // 网络能量是否充足

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
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(EAPSettings.ACCELERATE, YesNo.YES);         // 默认开启加速
        builder.registerSetting(EAPSettings.REDSTONE_CONTROL, YesNo.NO);    // 默认忽略红石信号
    }

    /**
     * 获取可用的升级卡槽数量
     *
     * @return 升级卡槽数量
     */
    @Override
    protected int getUpgradeSlots() {
        return 8;
    }

    // 判断当前是否应该休眠
    @Override
    protected boolean isSleeping() {
        // 主开关没开 → 休眠
        if (this.getConfigManager().getSetting(EAPSettings.ACCELERATE) != YesNo.YES) {
            return true;
        }

        // 没开红石控制 → 一直工作
        if (this.getConfigManager().getSetting(EAPSettings.REDSTONE_CONTROL) != YesNo.YES) {
            return false;
        }

        // 开启了红石控制 → 必须有红石信号才工作
        return !this.getHost().hasRedstone(); // 没信号 → 休眠
    }

    @Override
    protected void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        // 每次玩家在 GUI 里点任何按钮（包括加速开关、红石控制开关）都会进来这里
        this.getMainNode().ifPresent((grid, node) -> {
            if (this.isSleeping()) {
                grid.getTickManager().sleepDevice(node);
            } else {
                grid.getTickManager().wakeDevice(node);
            }
        });
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        // 只关心红石控制开启的情况下
        if (this.getConfigManager().getSetting(EAPSettings.REDSTONE_CONTROL) == YesNo.YES) {
            this.getMainNode().ifPresent((grid, node) -> {
                if (this.getHost().hasRedstone()) {
                    grid.getTickManager().wakeDevice(node);   // 有信号 → 立刻唤醒
                } else {
                    grid.getTickManager().sleepDevice(node);  // 没信号 → 立刻休眠
                }
            });
        }
    }

    /**
     * 当玩家激活部件（右键）时调用，打开自定义菜单
     *
     * @param player 玩家
     * @param pos    点击位置
     * @return 总是返回 true，表示激活成功
     */
    @Override
    public final boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!this.isClientSide()) {
            MenuOpener.open(ModMenuTypes.ENTITY_TICKER_MENU.get(), player, MenuLocators.forPart(this));
        }
        return true;
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
     * 定义部件的碰撞箱（用于物理碰撞和渲染）
     *
     * @param bch 碰撞辅助器
     */
    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(3, 3, 14, 13, 13, 16);
        bch.addBox(5, 5, 11, 11, 11, 14);
    }

    public void saveChanges() {
        this.getHost().markForSave();
    }

    private boolean isAccelerate() {
        return this.getConfigManager().getSetting(EAPSettings.ACCELERATE) == YesNo.YES;
    }

    public boolean isNetworkEnergySufficient() {
        return this.networkEnergySufficient == YesNo.YES;
    }

    /**
     * 更新网络能量充足状态并通知菜单。
     *
     * @param sufficient 是否能量充足
     */
    private void setNetworkEnergySufficient(boolean sufficient) {
        this.networkEnergySufficient = sufficient ? YesNo.YES : YesNo.NO;
        this.saveChanges();
    }

    /**
     * 获取定时请求，决定本部件多久 tick 一次
     *
     * @param iGridNode 网络节点
     * @return TickingRequest 对象
     */
    @Override
    public TickingRequest getTickingRequest(IGridNode iGridNode) {
        // 每 1 tick 执行一次
        return new TickingRequest(1, 1, this.isSleeping());
    }

    /**
     * 网络定时回调，每次 tick 时调用
     *
     * @param iGridNode          网络节点
     * @param ticksSinceLastCall 距离上次调用经过的 tick 数
     * @return TickRateModulation.IDLE 表示继续保持当前 tick 速率
     */
    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int ticksSinceLastCall) {
        if (!this.isAccelerate()) {
            return TickRateModulation.IDLE;
        }

        if (this.isSleeping()) {
            return TickRateModulation.SLEEP;
        }

        // 获取目标方块实体（本部件朝向的方块）
        BlockEntity target = this.getLevel().getBlockEntity(
                this.getBlockEntity().getBlockPos().relative(this.getSide())
        );
        // 仅在目标存在且部件处于激活状态时执行加速
        if (target == null || !this.isActive()) {
            return TickRateModulation.IDLE;
        }

        this.ticker(target);
        return TickRateModulation.IDLE;
    }

    /**
     * 对目标方块实体执行加速 tick 操作。
     *
     * @param blockEntity 目标方块实体
     * @param <T>         方块实体类型
     */
    private <T extends BlockEntity> void ticker(@NotNull T blockEntity) {
        if (!this.isValidForTicking()) {
            return;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString();
        if (ConfigParsingUtils.isBlockBlacklisted(blockId, ModConfigs.ENTITY_TICKER_BLACK_LIST.get())) {
            return;
        }

        BlockEntityTicker<T> ticker = this.getTicker(blockEntity);
        if (ticker == null) {
            return;
        }

        int speed = this.calculateSpeed();
        if (speed <= 0) {
            return;
        }

        double requiredPower = this.calculateRequiredPower(speed, blockId);
        if (!this.extractPower(requiredPower)) {
            return;
        }

        this.performTicks(blockEntity, ticker, speed);
    }

    /**
     * 检查网络节点是否有效。
     *
     * @return 是否可以执行 tick
     */
    private boolean isValidForTicking() {
        return this.getGridNode() != null && this.getMainNode() != null && this.getMainNode().getGrid() != null;
    }

    /**
     * 获取目标方块实体的 ticker。
     *
     * @param blockEntity 目标方块实体
     * @return ticker 或 null
     */
    private <T extends BlockEntity> BlockEntityTicker<T> getTicker(T blockEntity) {
        return this.getLevel().getBlockState(blockEntity.getBlockPos())
                .getTicker(this.getLevel(), (BlockEntityType<T>) blockEntity.getType());
    }

    /**
     * 计算加速倍率。
     *
     * @return 生效的加速倍率
     */
    private int calculateSpeed() {
        int entitySpeedCardCount = this.getUpgrades().getInstalledUpgrades(ModItems.ENTITY_SPEED_CARD.get());
        if (entitySpeedCardCount <= 0) return 0;
        return (int) PowerUtils.computeProductWithCap(this.getUpgrades(), 8);
    }

    /**
     * 计算所需能量。
     *
     * @param speed   加速倍率
     * @param blockId 目标方块ID
     * @return 所需能量
     */
    private double calculateRequiredPower(int speed, String blockId) {
        int energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
        double multiplier = ConfigParsingUtils.getMultiplierForBlock(blockId, ModConfigs.ENTITY_TICKER_MULTIPLIERS.get());
        return PowerUtils.computeFinalPowerForProduct(speed, energyCardCount) * multiplier;
    }

    /**
     * 提取网络能量并更新状态，优先从 AE2 网络提取 AE 能量，不足时从磁盘提取 FE 能量。
     *
     * @param requiredPower 所需能量（AE 单位）
     * @return 是否成功提取足够能量
     */
    private boolean extractPower(double requiredPower) {
        IEnergyService energyService = this.getMainNode().getGrid().getEnergyService();
        MEStorage storage = this.getMainNode().getGrid().getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(this);
        boolean appFluxLoaded = ModList.get().isLoaded("appflux");
        boolean preferDiskEnergy = appFluxLoaded && ModConfigs.PRIORITIZE_DISK_ENERGY.get();

        // 如果 appflux 存在且优先磁盘能量，尝试提取 FE 能量
        if (appFluxLoaded && preferDiskEnergy) {
            if (this.tryExtractFE(energyService, storage, requiredPower, source)) {
                return true;
            }
        }

        // 尝试提取 AE 能量（当 appflux 不存在、优先 AE 能量或 FE 提取失败时）
        double simulated = energyService.extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (simulated >= requiredPower) {
            double extracted = energyService.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            boolean sufficient = extracted >= requiredPower;
            this.setNetworkEnergySufficient(sufficient);
            return sufficient;
        }
        this.setNetworkEnergySufficient(false);

        // 如果 appflux 存在且优先 AE 能量，尝试提取 FE 能量作为备用
        if (appFluxLoaded && !preferDiskEnergy) {
            return this.tryExtractFE(energyService, storage, requiredPower, source);
        }
        return false;
    }

    private boolean tryExtractFE(IEnergyService energyService, MEStorage storage, double requiredPower, IActionSource source) {
        try {
            Class<?> helperClass = Class.forName("com.extendedae_plus.util.FluxEnergyHelper");
            Method extractMethod = helperClass.getMethod(
                    "extractFE",
                    IEnergyService.class,
                    MEStorage.class,
                    long.class,
                    IActionSource.class
            );
            long feRequired = (long) requiredPower << 1; // 1 AE = 2 FE
            long feExtracted = (long) extractMethod.invoke(null, energyService, storage, feRequired, source);
            if (feExtracted >= feRequired) {
                this.setNetworkEnergySufficient(true);
                return true;
            }
        } catch (Exception e) {
            // 如果反射失败，视为 FE 不可用
        }
        this.setNetworkEnergySufficient(false);
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
        // 执行 speed-1 次额外 tick（原生 tick 已包含 1 次）
        for (int i = 0; i < speed - 1; i++) {
            try {
                ticker.tick(
                        blockEntity.getLevel(),
                        blockEntity.getBlockPos(),
                        blockEntity.getBlockState(),
                        blockEntity
                );
            } catch (IllegalStateException e) {
                // 捕获随机数生成器的多线程访问异常
                // 这通常发生在某些模组（如 Thermal）的机器使用随机数时
                // 由于加速导致在同一tick内多次访问随机数生成器而触发 ThreadingDetector
                if (e.getMessage() != null && e.getMessage().contains("LegacyRandomSource")) {
                    // 记录警告并停止当前加速循环，避免崩溃
                    ExtendedAELogger.LOGGER.warn(
                            "检测到方块实体 {} 在位置 {} 的随机数访问冲突，已停止本次加速以避免崩溃。" +
                                    "建议将此方块类型添加到配置黑名单中。",
                            blockEntity.getType().toString(),
                            blockEntity.getBlockPos()
                    );
                    break; // 停止后续的加速 tick
                } else {
                    // 如果是其他类型的 IllegalStateException，继续抛出
                    throw e;
                }
            } catch (Exception e) {
                // 捕获其他可能的异常，防止崩溃
                ExtendedAELogger.LOGGER.error(
                        "在加速方块实体 {} 位置 {} 时发生错误: {}",
                        blockEntity.getType().toString(),
                        blockEntity.getBlockPos(),
                        e.getMessage(),
                        e
                );
                break; // 停止后续的加速 tick
            }
        }
    }

    /**
     * 判断部件是否有自定义名称
     *
     * @return 是否有自定义名称
     */
    @Override
    public boolean hasCustomName() {
        return super.hasCustomName();
    }

    /**
     * 获取部件的显示名称
     *
     * @return 显示名称
     */
    @Override
    public @NotNull Component getDisplayName() {
        return super.getDisplayName();
    }

    /**
     * 创建自定义菜单（GUI）
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
}