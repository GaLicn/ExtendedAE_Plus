package com.extendedae_plus.ae.parts;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
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
import java.util.List;

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

    public EntitySpeedTickerMenu menu;              // 当前打开的菜单实例
    private boolean accelerateEnabled = true;       // 是否启用加速
    private boolean networkEnergySufficient = true; // 网络能量是否充足

    /**
     * 构造函数，初始化部件并设置网络节点属性。
     * @param partItem 部件物品
     */
    public EntitySpeedTickerPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1)
                .addService(IGridTickable.class, this);
    }

    public boolean getAccelerateEnabled() {
        return this.accelerateEnabled;
    }

    public boolean isNetworkEnergySufficient() {
        return this.networkEnergySufficient;
    }

    /**
     * 设置加速开关状态并通知菜单。
     * @param enabled 是否启用加速
     */
    public void setAccelerateEnabled(boolean enabled) {
        this.accelerateEnabled = enabled;
        if (menu != null) {
            menu.setAccelerateEnabled(enabled);
        }
    }

    /**
     * 更新网络能量充足状态并通知菜单。
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
     * @param player 玩家
     * @param hand 手
     * @param pos 点击位置
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
     * @param bch 碰撞辅助器
     */
    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    /**
     * 获取定时请求，指定 tick 频率。
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
        if (menu != null) {
            menu.broadcastChanges();
        }
    }

    /**
     * 网络定时回调，处理目标方块实体的加速。
     * @param iGridNode 网络节点
     * @param ticksSinceLastCall 距离上次调用的 tick 数
     * @return TickRateModulation.IDLE
     */
    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int ticksSinceLastCall) {
        if (!this.getAccelerateEnabled()) {
            return TickRateModulation.IDLE;
        }
        BlockEntity target = getLevel().getBlockEntity(getBlockEntity().getBlockPos().relative(getSide()));
        if (target != null && isActive()) {
            ticker(target);
        }
        return TickRateModulation.IDLE;
    }

    /**
     * 对目标方块实体执行加速 tick 操作。
     * @param blockEntity 目标方块实体
     * @param <T> 方块实体类型
     */
    private <T extends BlockEntity> void ticker(@NotNull T blockEntity) {
        if (!isValidForTicking()) {
            return;
        }

        String blockId = ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock()).toString();
        if (ConfigParsingUtils.isBlockBlacklisted(blockId, List.of(ModConfig.INSTANCE.entityTickerBlackList))) {
            return;
        }

        BlockEntityTicker<T> ticker = getTicker(blockEntity);
        if (ticker == null) {
            return;
        }

        int speed = calculateSpeed();
        if (speed <= 0) {
            return;
        }

        double requiredPower = calculateRequiredPower(speed, blockId);
        if (!extractPower(requiredPower)) {
            return;
        }

        performTicks(blockEntity, ticker, speed);
    }

    /**
     * 检查网络节点是否有效。
     * @return 是否可以执行 tick
     */
    private boolean isValidForTicking() {
        return getGridNode() != null && getMainNode() != null && getMainNode().getGrid() != null;
    }

    /**
     * 获取目标方块实体的 ticker。
     * @param blockEntity 目标方块实体
     * @return ticker 或 null
     */
    private <T extends BlockEntity> BlockEntityTicker<T> getTicker(T blockEntity) {
        return getLevel().getBlockState(blockEntity.getBlockPos())
                .getTicker(getLevel(), (BlockEntityType<T>) blockEntity.getType());
    }

    /**
     * 计算加速倍率。
     * @return 生效的加速倍率
     */
    private int calculateSpeed() {
        int entitySpeedCardCount = getUpgrades().getInstalledUpgrades(ModItems.ENTITY_SPEED_CARD.get());
        if (entitySpeedCardCount <= 0) return 0;
        return (int) PowerUtils.computeProductWithCap(getUpgrades(), 8);
    }

    /**
     * 计算所需能量。
     * @param speed 加速倍率
     * @param blockId 目标方块ID
     * @return 所需能量
     */
    private double calculateRequiredPower(int speed, String blockId) {
        int energyCardCount = getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
        double multiplier = ConfigParsingUtils.getMultiplierForBlock(blockId, List.of(ModConfig.INSTANCE.entityTickerMultipliers));
        return PowerUtils.computeFinalPowerForProduct(speed, energyCardCount) * multiplier;
    }

    /**
     * 提取网络能量并更新状态，优先从 AE2 网络提取 AE 能量，不足时从磁盘提取 FE 能量。
     * @param requiredPower 所需能量（AE 单位）
     * @return 是否成功提取足够能量
     */
    private boolean extractPower(double requiredPower) {
        IEnergyService energyService = getMainNode().getGrid().getEnergyService();
        MEStorage storage = getMainNode().getGrid().getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(this);
        boolean appFluxLoaded = ModList.get().isLoaded("appflux");
        boolean preferDiskEnergy = appFluxLoaded && ModConfig.INSTANCE.prioritizeDiskEnergy;

        // 如果 appflux 存在且优先磁盘能量，尝试提取 FE 能量
        if (appFluxLoaded && preferDiskEnergy) {
            if (tryExtractFE(energyService, storage, requiredPower, source)) {
                return true;
            }
        }

        // 尝试提取 AE 能量（当 appflux 不存在、优先 AE 能量或 FE 提取失败时）
        double simulated = energyService.extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (simulated >= requiredPower) {
            double extracted = energyService.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            boolean sufficient = extracted >= requiredPower;
            updateNetworkEnergySufficient(sufficient);
            return sufficient;
        }
        updateNetworkEnergySufficient(false);

        // 如果 appflux 存在且优先 AE 能量，尝试提取 FE 能量作为备用
        if (appFluxLoaded && !preferDiskEnergy) {
            return tryExtractFE(energyService, storage, requiredPower, source);
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
                updateNetworkEnergySufficient(true);
                return true;
            }
        } catch (Exception e) {
            // 如果反射失败，视为 FE 不可用
        }
        updateNetworkEnergySufficient(false);
        return false;
    }

    /**
     * 执行加速 tick 操作。
     * @param blockEntity 目标方块实体
     * @param ticker 方块实体 ticker
     * @param speed 加速倍率
     */
    private <T extends BlockEntity> void performTicks(T blockEntity,
                                                      BlockEntityTicker<T> ticker,
                                                      int speed) {
        // 执行 speed-1 次额外 tick（原生 tick 已包含 1 次）
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
     * 获取升级卡槽数量。
     * @return 升级卡槽数量
     */
    @Override
    protected int getUpgradeSlots() {
        return 8;
    }
}