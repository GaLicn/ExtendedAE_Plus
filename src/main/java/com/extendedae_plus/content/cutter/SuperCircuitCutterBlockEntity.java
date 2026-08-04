package com.extendedae_plus.content.cutter;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.util.ConfigManager;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.recipe.SuperCircuitCutterRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 保留原电路切片机数值与处理逻辑的超级版本。 */
public class SuperCircuitCutterBlockEntity extends AENetworkedPoweredBlockEntity
        implements IGridTickable, IUpgradeableObject, IConfigurableObject {
    public static final int POWER_MAXIMUM_AMOUNT = 64_000;
    public static final int MAX_PROGRESS = 200;
    public static final int MAX_PARALLEL_RECIPES = 8;
    private final AppEngInternalInventory input = new AppEngInternalInventory(this, 1, 64);
    private final AppEngInternalInventory output = new AppEngInternalInventory(this, 1, 64);
    private final CombinedInternalInventory inventory = new CombinedInternalInventory(input, output);
    private final CombinedInternalInventory exposedInventory = new CombinedInternalInventory(
            new FilteredInternalInventory(input, AEItemFilters.INSERT_ONLY),
            new FilteredInternalInventory(output, AEItemFilters.EXTRACT_ONLY));
    private final IUpgradeInventory upgrades;
    private final ConfigManager configManager;
    private boolean working;
    private int progress;

    public SuperCircuitCutterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CIRCUIT_CUTTER_PLUS_BE.get(), pos, state);
        getMainNode().setFlags().setIdlePowerUsage(0).addService(IGridTickable.class, this);
        setInternalMaxPower(POWER_MAXIMUM_AMOUNT);
        setPowerSides(getGridConnectableSides(getOrientation()));
        upgrades = UpgradeInventories.forMachine(ModItems.CIRCUIT_CUTTER_PLUS, 4, this::saveChanges);
        configManager = new ConfigManager(this::onConfigChanged);
        configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
    }

    public AppEngInternalInventory getInput() {
        return input;
    }

    public AppEngInternalInventory getOutput() {
        return output;
    }

    public boolean isWorking() {
        return working;
    }

    public int getProgress() {
        return progress;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        return exposedInventory;
    }

    @Override
    public IItemHandler getExposedItemHandler(@Nullable Direction side) {
        return exposedInventory.toItemHandler();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.COVERED;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.FRONT), orientation.getSide(RelativeSide.BACK)));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        setPowerSides(getGridConnectableSides(orientation));
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Inscriber, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (tryAutoExport()) {
            return TickRateModulation.URGENT;
        }
        var recipe = findRecipe();
        if (recipe == null) {
            setWorking(false);
            progress = 0;
            return TickRateModulation.SLOWER;
        }
        setWorking(true);
        int operations = getParallelOperations(recipe.value());
        int speed = speedFor(upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        if (consumePower(10 * speed * operations)) {
            progress += speed;
        }
        if (progress >= MAX_PROGRESS) {
            progress = 0;
            // 完成时重新确认库存和输出空间，阻止并发操作重复消耗或产出。
            operations = getParallelOperations(recipe.value());
            if (operations > 0) {
                consumeInputs(recipe.value(), operations);
                output.insertItem(0, createOutput(recipe.value(), operations), false);
                saveChanges();
            }
        }
        return TickRateModulation.URGENT;
    }

    private @Nullable RecipeHolder<SuperCircuitCutterRecipe> findRecipe() {
        if (level == null) {
            return null;
        }
        for (var recipe : level.getRecipeManager().getAllRecipesFor(SuperCircuitCutterRecipe.TYPE)) {
            if (canRun(recipe.value())) {
                return recipe;
            }
        }
        return null;
    }

    private boolean canRun(SuperCircuitCutterRecipe recipe) {
        return getParallelOperations(recipe) > 0;
    }

    private int getParallelOperations(SuperCircuitCutterRecipe recipe) {
        int operations = Math.min(MAX_PARALLEL_RECIPES, getOutputCapacity(recipe.output()));
        if (operations == 0) {
            return 0;
        }
        var stack = input.getStackInSlot(0).copy();
        for (int operation = 0; operation < operations; operation++) {
            var sample = recipe.input().sample();
            if (!sample.checkType(stack)) {
                return operation;
            }
            sample.consume(stack);
            if (!sample.isEmpty()) {
                return operation;
            }
        }
        return operations;
    }

    private int getOutputCapacity(ItemStack recipeOutput) {
        var currentOutput = output.getStackInSlot(0);
        if (!currentOutput.isEmpty() && !ItemStack.isSameItemSameComponents(currentOutput, recipeOutput)) {
            return 0;
        }
        int remainingSpace = (currentOutput.isEmpty() ? recipeOutput.getMaxStackSize() : currentOutput.getMaxStackSize())
                - currentOutput.getCount();
        return remainingSpace / recipeOutput.getCount();
    }

    private void consumeInputs(SuperCircuitCutterRecipe recipe, int operations) {
        var stack = input.getStackInSlot(0);
        for (int operation = 0; operation < operations; operation++) {
            recipe.input().sample().consume(stack);
        }
        input.setItemDirect(0, stack);
    }

    private ItemStack createOutput(SuperCircuitCutterRecipe recipe, int operations) {
        var outputStack = recipe.output().copy();
        outputStack.setCount(outputStack.getCount() * operations);
        return outputStack;
    }

    private boolean consumePower(double amount) {
        double threshold = amount - 0.01;
        IEnergySource source = this;
        double extracted = source.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (extracted <= threshold && getGridNode() != null && getGridNode().getGrid() != null) {
            source = getGridNode().getGrid().getEnergyService();
            extracted = source.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        }
        if (extracted > threshold) {
            source.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG);
            return true;
        }
        return false;
    }

    private boolean tryAutoExport() {
        if (level == null || configManager.getSetting(Settings.AUTO_EXPORT) != YesNo.YES || output.getStackInSlot(0).isEmpty()) {
            return false;
        }
        for (var direction : Direction.values()) {
            var target = InternalInventory.wrapExternal(level, worldPosition.relative(direction), direction.getOpposite());
            if (target == null) {
                continue;
            }
            int count = output.getStackInSlot(0).getCount();
            output.insertItem(0, target.addItems(output.extractItem(0, 64, false)), false);
            if (count != output.getStackInSlot(0).getCount()) {
                return true;
            }
        }
        return false;
    }

    private static int speedFor(int cards) {
        return switch (cards) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 50;
            default -> 2;
        };
    }

    private void setWorking(boolean working) {
        if (this.working != working) {
            this.working = working;
            markForUpdate();
        }
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return inventory;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory changedInventory, int slot) {
        saveChanges();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean receivedWorking = data.readBoolean();
        if (working != receivedWorking) {
            working = receivedWorking;
            changed = true;
        }
        return changed;
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(working);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putInt("progress", progress);
        upgrades.writeToNBT(data, "upgrades", registries);
        configManager.writeToNBT(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        progress = data.getInt("progress");
        upgrades.readFromNBT(data, "upgrades", registries);
        configManager.readFromNBT(data, registries);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : upgrades) {
            drops.add(upgrade);
        }
    }
}
