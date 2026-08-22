package com.extendedae_plus.content.crystal;

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
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigManager;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.recipe.SuperCrystalAssemblerRecipe;
import com.extendedae_plus.recipe.SuperCrystalAssemblerRecipeManager;
import com.glodblock.github.extendedae.api.caps.IGenericInvHost;
import com.glodblock.github.glodium.recipe.stack.IngredientStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 单方块超级水晶装配器；数值、槽位和加速卡曲线均与 ExtendedAE 原机一致。
 */
public class SuperCrystalAssemblerBlockEntity extends AENetworkedPoweredBlockEntity
        implements IGridTickable, IUpgradeableObject, IConfigurableObject, IGenericInvHost {
    public static final int SLOTS = 9;
    public static final int TANK_CAP = 16_000;
    public static final int POWER_MAXIMUM_AMOUNT = 64_000;
    public static final int MAX_PROGRESS = 200;
    public static final int MAX_PARALLEL_RECIPES = 8;

    private final AppEngInternalInventory input = new AppEngInternalInventory(this, SLOTS, 64);
    private final AppEngInternalInventory output = new AppEngInternalInventory(this, 1, 64);
    private final CombinedInternalInventory inventory = new CombinedInternalInventory(input, output);
    private final FilteredInternalInventory exposedOutput = new FilteredInternalInventory(output, AEItemFilters.EXTRACT_ONLY);
    private final FilteredInternalInventory exposedInput = new FilteredInternalInventory(input, AEItemFilters.INSERT_ONLY);
    private final CombinedInternalInventory exposedInventory = new CombinedInternalInventory(exposedInput, exposedOutput);
    private final GenericStackInv tank = new GenericStackInv(Set.of(AEKeyType.fluids()), this::onChangeTank,
            GenericStackInv.Mode.STORAGE, 1) {
        @Override
        public boolean canExtract() {
            return false;
        }
    };
    private final IUpgradeInventory upgrades;
    private final ConfigManager configManager;
    private boolean working;
    private int progress;

    public SuperCrystalAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL_ASSEMBLER_PLUS_BE.get(), pos, state);
        this.getMainNode().setFlags().setIdlePowerUsage(0).addService(IGridTickable.class, this);
        this.setInternalMaxPower(POWER_MAXIMUM_AMOUNT);
        this.setPowerSides(this.getGridConnectableSides(this.getOrientation()));
        this.upgrades = UpgradeInventories.forMachine(ModItems.CRYSTAL_ASSEMBLER_PLUS, 4, this::saveChanges);
        this.configManager = new ConfigManager(this::onConfigChanged);
        this.configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
        this.tank.setCapacity(AEKeyType.fluids(), TANK_CAP);
    }

    public AppEngInternalInventory getInput() {
        return input;
    }

    public AppEngInternalInventory getOutput() {
        return output;
    }

    public GenericStackInv getTank() {
        return tank;
    }

    @Override
    public GenericStackInv getGenericInv() {
        // 对齐 EAE 原机，让 AE2 外部存储能力访问流体罐。
        return tank;
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
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.TOP)));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        this.setPowerSides(this.getGridConnectableSides(orientation));
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Inscriber, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.tryAutoExport()) {
            return TickRateModulation.URGENT;
        }
        var recipe = this.findRecipe();
        if (recipe == null) {
            this.setWorking(false);
            this.progress = 0;
            return TickRateModulation.SLOWER;
        }

        this.setWorking(true);
        int operations = this.getParallelOperations(recipe.value());
        int speed = speedFor(this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        if (this.consumePower(10 * speed * operations)) {
            this.progress += speed;
        }
        if (this.progress >= MAX_PROGRESS) {
            this.progress = 0;
            // 完成时再次验证并消耗输入，避免库存变化造成物品复制。
            operations = this.getParallelOperations(recipe.value());
            if (operations > 0) {
                this.consumeInputs(recipe.value(), operations);
                this.output.insertItem(0, this.createOutput(recipe.value(), operations), false);
                this.saveChanges();
            }
        }
        return TickRateModulation.URGENT;
    }

    private @Nullable RecipeHolder<SuperCrystalAssemblerRecipe> findRecipe() {
        if (this.level == null) {
            return null;
        }
        for (var recipe : SuperCrystalAssemblerRecipeManager.getAllRecipes(this.level)) {
            if (this.canRun(recipe.value())) {
                return recipe;
            }
        }
        return null;
    }

    private boolean canRun(SuperCrystalAssemblerRecipe recipe) {
        return this.getParallelOperations(recipe) > 0;
    }

    private int getParallelOperations(SuperCrystalAssemblerRecipe recipe) {
        int operations = Math.min(MAX_PARALLEL_RECIPES, this.getOutputCapacity(recipe.output()));
        if (operations == 0) {
            return 0;
        }
        var available = this.copyInputs();
        for (int operation = 0; operation < operations; operation++) {
            for (var requirement : this.requirements(recipe)) {
                for (var stack : available) {
                    if (requirement.checkType(stack)) {
                        requirement.consume(stack);
                    }
                    if (requirement.isEmpty()) {
                        break;
                    }
                }
                if (requirement.isEmpty()) {
                    continue;
                }
                return operation;
            }
        }
        return operations;
    }

    private int getOutputCapacity(ItemStack recipeOutput) {
        var currentOutput = this.output.getStackInSlot(0);
        if (!currentOutput.isEmpty() && !ItemStack.isSameItemSameComponents(currentOutput, recipeOutput)) {
            return 0;
        }
        int remainingSpace = (currentOutput.isEmpty() ? recipeOutput.getMaxStackSize() : currentOutput.getMaxStackSize())
                - currentOutput.getCount();
        return remainingSpace / recipeOutput.getCount();
    }

    private ItemStack createOutput(SuperCrystalAssemblerRecipe recipe, int operations) {
        var outputStack = recipe.output().copy();
        outputStack.setCount(outputStack.getCount() * operations);
        return outputStack;
    }

    private List<IngredientStack<?, ?>> requirements(SuperCrystalAssemblerRecipe recipe) {
        var requirements = new ArrayList<IngredientStack<?, ?>>();
        for (var inputStack : recipe.inputItems()) {
            if (!inputStack.isEmpty()) {
                requirements.add(inputStack.sample());
            }
        }
        recipe.inputFluid().ifPresent(fluid -> requirements.add(fluid.sample()));
        return requirements;
    }

    private List<Object> copyInputs() {
        var copied = new ArrayList<Object>();
        for (var item : input) {
            if (!item.isEmpty()) {
                copied.add(item.copy());
            }
        }
        var storedFluid = tank.getStack(0);
        if (storedFluid != null && storedFluid.what() instanceof AEFluidKey fluidKey) {
            copied.add(fluidKey.toStack((int) storedFluid.amount()));
        }
        return copied;
    }

    private void consumeInputs(SuperCrystalAssemblerRecipe recipe, int operations) {
        FluidStack fluidStack = null;
        var storedFluid = tank.getStack(0);
        if (storedFluid != null && storedFluid.what() instanceof AEFluidKey fluidKey) {
            fluidStack = fluidKey.toStack((int) storedFluid.amount());
        }
        // 每一批独立构建需求，确保物品与流体均按并行次数扣除。
        for (int operation = 0; operation < operations; operation++) {
            for (var requirement : this.requirements(recipe)) {
                for (int slot = 0; slot < input.size(); slot++) {
                    var item = input.getStackInSlot(slot);
                    if (requirement.checkType(item)) {
                        requirement.consume(item);
                        input.setItemDirect(slot, item);
                    }
                    if (requirement.isEmpty()) {
                        break;
                    }
                }
                if (fluidStack != null && !requirement.isEmpty() && requirement.checkType(fluidStack)) {
                    requirement.consume(fluidStack);
                }
            }
        }
        if (fluidStack != null) {
            tank.setStack(0, fluidStack.isEmpty() ? null
                    : new GenericStack(AEFluidKey.of(fluidStack), fluidStack.getAmount()));
        }
    }

    private boolean consumePower(double amount) {
        double threshold = amount - 0.01;
        IEnergySource source = this;
        double extracted = source.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (extracted <= threshold) {
            var gridNode = this.getGridNode();
            if (gridNode != null && gridNode.getGrid() != null) {
                source = gridNode.getGrid().getEnergyService();
                extracted = source.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            }
        }
        if (extracted > threshold) {
            source.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG);
            return true;
        }
        return false;
    }

    private boolean tryAutoExport() {
        if (this.configManager.getSetting(Settings.AUTO_EXPORT) != YesNo.YES || output.getStackInSlot(0).isEmpty()
                || this.level == null) {
            return false;
        }
        for (var direction : Direction.values()) {
            var target = InternalInventory.wrapExternal(level, this.worldPosition.relative(direction), direction.getOpposite());
            if (target == null) {
                continue;
            }
            var before = output.getStackInSlot(0).getCount();
            output.insertItem(0, target.addItems(output.extractItem(0, 64, false)), false);
            if (before != output.getStackInSlot(0).getCount()) {
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
            this.markForUpdate();
        }
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
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
        this.saveChanges();
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private void onChangeTank() {
        this.saveChanges();
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean receivedWorking = data.readBoolean();
        if (receivedWorking != working) {
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
        tank.writeToChildTag(data, "tank", registries);
        upgrades.writeToNBT(data, "upgrades", registries);
        configManager.writeToNBT(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        progress = data.getInt("progress");
        tank.readFromChildTag(data, "tank", registries);
        upgrades.readFromNBT(data, "upgrades", registries);
        configManager.readFromNBT(data, registries);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        tank.clear();
        upgrades.clear();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : upgrades) {
            drops.add(upgrade);
        }
        var fluid = tank.getStack(0);
        if (fluid != null) {
            fluid.what().addDrops(fluid.amount(), drops, level, pos);
        }
    }
}
