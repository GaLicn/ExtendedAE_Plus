package com.extendedae_plus.ae.parts;

import appeng.api.components.ExportedUpgrades;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
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
import appeng.items.tools.NetworkToolItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import appeng.util.SettingsFrom;
import appeng.util.inv.PlayerInternalInventory;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.ae.wireless.WirelessSlaveLink;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.items.materials.ChannelCardItem;
import com.extendedae_plus.util.ExtendedAELogger;
import com.extendedae_plus.util.entitySpeed.ConfigParsingUtils;
import com.extendedae_plus.util.entitySpeed.PowerUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

/**
 * EntitySpeedTickerPart 是一个可升级的 AE2 部件<p>
 * 该部件可以加速目标方块实体的 tick 速率，消耗 AE 网络能量，并支持加速卡升级<p>
 * 功能受<a href="https://github.com/GilbertzRivi/crazyae2addons">Crazy AE2 Addons</a>启发
 */
public class EntitySpeedTickerPart extends UpgradeablePart implements IGridTickable, MenuProvider, IUpgradeableObject, InterfaceWirelessLinkBridge {
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
    private WirelessSlaveLink wirelessLink;
    private long lastChannel = -1;
    private UUID lastOwner;
    private boolean clientWirelessConnected = false;
    private boolean hasInitializedWireless = false;
    private int delayedInitTicks = 0;

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
        if (!this.isClientSide()) {
            if (!this.hasInitializedWireless) {
                this.initializeWirelessLink();
            } else if (this.wirelessLink != null && this.lastChannel != 0L) {
                this.wirelessLink.updateStatus();
            }
        }
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

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        if (mode != SettingsFrom.MEMORY_CARD) {
            super.importSettings(mode, input, player);
            return;
        }

        var desiredUpgrades = input.get(AEComponents.EXPORTED_UPGRADES);
        super.importSettings(mode, this.filterOutExportedUpgrades(input), player);

        if (desiredUpgrades == null) {
            return;
        }

        if (player == null || player.getAbilities().instabuild) {
            this.setUpgradesDirect(desiredUpgrades);
            return;
        }

        this.restoreExactUpgrades(player, desiredUpgrades);
    }

    @Override
    public void upgradesChanged() {
        super.upgradesChanged();
        if (this.isClientSide()) {
            return;
        }
        this.hasInitializedWireless = false;
        this.lastChannel = -1;
        this.lastOwner = null;
        this.initializeWirelessLink();
    }

    @Override
    public void eap$updateWirelessLink() {
        if (this.isClientSide()) {
            return;
        }
        if (this.wirelessLink != null) {
            this.wirelessLink.updateStatus();
        }
    }

    @Override
    public boolean eap$isWirelessConnected() {
        if (this.isClientSide()) {
            return this.clientWirelessConnected;
        }
        return this.wirelessLink != null && this.wirelessLink.isConnected();
    }

    @Override
    public void eap$setClientWirelessState(boolean connected) {
        this.clientWirelessConnected = connected;
    }

    @Override
    public boolean eap$hasTickInitialized() {
        return this.hasInitializedWireless;
    }

    @Override
    public void eap$setTickInitialized(boolean initialized) {
        this.hasInitializedWireless = initialized;
    }

    @Override
    public void eap$initializeChannelLink() {
        this.initializeWirelessLink();
    }

    @Override
    public void eap$handleDelayedInit() {
        if (this.isClientSide()) {
            return;
        }
        if (!this.hasInitializedWireless) {
            this.initializeWirelessLink();
            return;
        }
        if (this.delayedInitTicks > 0) {
            this.delayedInitTicks--;
            if (this.delayedInitTicks == 0 && this.wirelessLink != null && !this.wirelessLink.isConnected()) {
                this.hasInitializedWireless = false;
                this.initializeWirelessLink();
            }
        }
    }

    @Override
    public boolean eap$shouldKeepTicking() {
        if (this.isClientSide()) {
            return false;
        }
        if (!this.hasInitializedWireless) {
            return true;
        }
        if (this.lastChannel == 0L) {
            return false;
        }
        return this.wirelessLink == null || !this.wirelessLink.isConnected();
    }

    private boolean extractPower(double requiredPower) {
        if (requiredPower <= 0) {
            return true;
        }
        try {
            var node = this.getMainNode();
            if (node == null || node.getGrid() == null) {
                this.setNetworkEnergySufficient(false);
                return false;
            }
            IEnergyService energyService = node.getGrid().getEnergyService();
            double simulated = energyService.extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (simulated + 1e-6 < requiredPower) {
                this.setNetworkEnergySufficient(false);
                return false;
            }
            energyService.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            this.setNetworkEnergySufficient(true);
            return true;
        } catch (Throwable ignored) {
        }
        this.setNetworkEnergySufficient(false);
        return false;
    }

    private void initializeWirelessLink() {
        if (this.isClientSide()) {
            return;
        }
        var node = this.getMainNode();
        if (node == null || node.getNode() == null) {
            this.hasInitializedWireless = false;
            return;
        }

        long channel = 0L;
        UUID owner = null;
        boolean found = false;
        for (ItemStack stack : this.getUpgrades()) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                channel = ChannelCardItem.getChannel(stack);
                owner = ChannelCardItem.getOwnerUUID(stack);
                if (owner == null) {
                    owner = this.getFallbackOwner();
                }
                found = true;
                break;
            }
        }

        if (!found) {
            this.disconnectWirelessLink();
            this.lastChannel = 0L;
            this.lastOwner = null;
            this.hasInitializedWireless = true;
            this.delayedInitTicks = 0;
            try {
                this.getHost().markForUpdate();
            } catch (Throwable ignored) {
            }
            return;
        }

        boolean sameOwner = (this.lastOwner == null && owner == null)
                || (this.lastOwner != null && this.lastOwner.equals(owner));
        if (this.wirelessLink != null && this.lastChannel == channel && sameOwner) {
            if (this.wirelessLink.isConnected()) {
                this.hasInitializedWireless = true;
            } else {
                this.delayedInitTicks = Math.max(this.delayedInitTicks, 5);
            }
            return;
        }

        if (this.wirelessLink == null) {
            var endpoint = new GenericNodeEndpointImpl(
                    () -> this.getHost().getBlockEntity(),
                    () -> this.getMainNode().getNode()
            );
            this.wirelessLink = new WirelessSlaveLink(endpoint);
        }

        this.wirelessLink.setPlacerId(owner);
        this.wirelessLink.setFrequency(channel);
        this.wirelessLink.updateStatus();
        this.lastChannel = channel;
        this.lastOwner = owner;
        this.hasInitializedWireless = this.wirelessLink.isConnected();
        if (!this.hasInitializedWireless) {
            this.delayedInitTicks = 5;
        } else {
            this.delayedInitTicks = 0;
        }
        try {
            this.getHost().markForUpdate();
        } catch (Throwable ignored) {
        }
    }

    private void disconnectWirelessLink() {
        if (this.wirelessLink != null) {
            this.wirelessLink.setPlacerId(null);
            this.wirelessLink.setFrequency(0L);
            this.wirelessLink.updateStatus();
        }
    }

    private UUID getFallbackOwner() {
        if (this.getMainNode() != null && this.getMainNode().getNode() != null) {
            return this.getMainNode().getNode().getOwningPlayerProfileId();
        }
        return null;
    }

    private DataComponentMap filterOutExportedUpgrades(DataComponentMap input) {
        if (input.get(AEComponents.EXPORTED_UPGRADES) == null) {
            return input;
        }

        var builder = DataComponentMap.builder();
        for (var type : input.keySet()) {
            if (type == AEComponents.EXPORTED_UPGRADES) {
                continue;
            }
            copyDataComponent(input, builder, type);
        }
        return builder.build();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void copyDataComponent(DataComponentMap input, DataComponentMap.Builder builder,
                                          DataComponentType<?> type) {
        var rawType = (DataComponentType) type;
        var value = input.get(rawType);
        if (value != null) {
            builder.set(rawType, value);
        }
    }

    private void setUpgradesDirect(ExportedUpgrades desiredUpgrades) {
        var upgrades = this.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            upgrades.setItemDirect(i, ItemStack.EMPTY);
        }
        for (var desired : desiredUpgrades.upgrades()) {
            if (!desired.isEmpty()) {
                upgrades.addItems(desired.copy());
            }
        }
        this.upgradesChanged();
        this.saveChanges();
    }

    private void restoreExactUpgrades(Player player, ExportedUpgrades desiredUpgrades) {
        var upgrades = this.getUpgrades();
        var desiredCounts = collectUpgradeCounts(desiredUpgrades.upgrades());
        var currentCounts = collectUpgradeCounts(upgrades);

        Object2IntMap<ItemStack> excessCounts = new Object2IntOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        excessCounts.defaultReturnValue(0);
        for (var entry : currentCounts.object2IntEntrySet()) {
            int excess = entry.getIntValue() - desiredCounts.getInt(entry.getKey());
            if (excess > 0) {
                excessCounts.put(entry.getKey(), excess);
            }
        }

        var upgradeSources = new ArrayList<InternalInventory>();
        upgradeSources.add(new PlayerInternalInventory(player.getInventory()));

        var networkTool = NetworkToolItem.findNetworkToolInv(player);
        if (networkTool != null) {
            upgradeSources.add(networkTool.getInventory());
        }

        for (int i = 0; i < upgrades.size(); i++) {
            var current = upgrades.getStackInSlot(i);
            if (current.isEmpty()) {
                continue;
            }

            var key = normalizeUpgradeKey(current);
            int excess = excessCounts.getInt(key);
            if (excess <= 0) {
                continue;
            }

            var removed = upgrades.extractItem(i, Math.min(excess, current.getCount()), false);
            if (removed.isEmpty()) {
                continue;
            }

            excessCounts.put(key, excess - removed.getCount());
            for (var upgradeSource : upgradeSources) {
                if (removed.isEmpty()) {
                    break;
                }
                removed = upgradeSource.addItems(removed);
            }
            if (!removed.isEmpty()) {
                player.drop(removed, false);
            }
        }

        var afterRemovalCounts = collectUpgradeCounts(upgrades);
        for (var entry : desiredCounts.object2IntEntrySet()) {
            var desiredStack = entry.getKey();
            int missing = entry.getIntValue() - afterRemovalCounts.getInt(desiredStack);
            if (missing <= 0) {
                continue;
            }

            var simulatedOverflow = upgrades.addItems(desiredStack.copyWithCount(missing), true);
            int insertable = missing - simulatedOverflow.getCount();
            if (insertable <= 0) {
                continue;
            }

            int remaining = insertable;
            for (var upgradeSource : upgradeSources) {
                var extracted = upgradeSource.removeItems(remaining, desiredStack, null);
                if (extracted.isEmpty()) {
                    continue;
                }

                var overflow = upgrades.addItems(extracted);
                if (!overflow.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(overflow);
                }

                remaining -= extracted.getCount();
                if (remaining <= 0) {
                    break;
                }
            }
        }

        this.saveChanges();
    }

    private static Object2IntMap<ItemStack> collectUpgradeCounts(Iterable<ItemStack> stacks) {
        Object2IntMap<ItemStack> counts = new Object2IntOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        counts.defaultReturnValue(0);
        for (var stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            counts.mergeInt(normalizeUpgradeKey(stack), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static ItemStack normalizeUpgradeKey(ItemStack stack) {
        return stack.copyWithCount(1);
    }
}
