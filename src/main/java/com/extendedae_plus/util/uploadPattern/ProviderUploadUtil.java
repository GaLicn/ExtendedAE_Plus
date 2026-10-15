package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.AEBasePart;
import appeng.util.inv.filter.IAEItemFilter;
import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.api.upload.UploadPreferences;
import com.extendedae_plus.compat.NeoECOAECompat;
import com.extendedae_plus.config.PatternUploadPriority;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 样板供应器侧的上传工具，并承载「最近一次上传目标」的记录与回查。
 *
 * <p>职责包括：供应器的定位与查询（按 ID、按位置、按网络遍历）、可写入槽位工具、
 * 各类上传入口（背包上传、编码终端指定供应器/按索引/唯一名称/任意）、重复检测与
 * 空白样板回收。上传记录的持久化字段也由本类统一管理，矩阵侧通过
 * {@link #recordMatrixUpload} 写入，二者共用同一套 key，避免两处漂移。</p>
 */
public final class ProviderUploadUtil {

    private static final String LAST_UPLOADED_PROVIDER_KEY = "eap_last_uploaded_provider_id";
    private static final String LAST_UPLOAD_KIND_KEY = "eap_last_upload_kind";
    private static final String LAST_UPLOAD_SLOT_KEY = "eap_last_upload_slot";
    private static final String LAST_UPLOAD_POS_KEY = "eap_last_upload_pos";
    private static final String LAST_UPLOAD_SIDE_KEY = "eap_last_upload_side";
    private static final String LAST_UPLOAD_DIM_KEY = "eap_last_upload_dim";
    private static final String LAST_UPLOAD_MATRIX_PLUS_KEY = "eap_last_upload_matrix_plus";
    private static final String LAST_UPLOAD_KIND_PROVIDER = "provider";
    private static final String LAST_UPLOAD_KIND_MATRIX = "matrix";

    private ProviderUploadUtil() {
    }

    public record LastUploadRecord(String kind, long providerId, int slot, long pos, String side, String dimension, boolean matrixPlus) {
        public boolean isMatrix() {
            return LAST_UPLOAD_KIND_MATRIX.equals(this.kind);
        }
    }

    private record HostLocator(long pos, String side, String dimension) {}

    /**
     * 上传结果三态。
     *
     * <p>「重复」必须与「失败」区分：前者是用户应当知晓的正常结果（样板已在目标处，
     * 本次不上传），后者才是异常；混为一谈会让玩家误以为链路出错。</p>
     */
    public enum UploadOutcome {
        SUCCESS,
        DUPLICATE,
        FAILED
    }

    // ------------------------------------------------------------------
    // 上传记录
    // ------------------------------------------------------------------

    public static void recordProviderUpload(ServerPlayer player, long providerId, PatternContainer container, int slot) {
        if (player == null || container == null || slot < 0) {
            return;
        }

        HostLocator locator = getHostLocator(container);
        CompoundTag data = player.getPersistentData();
        data.putString(LAST_UPLOAD_KIND_KEY, LAST_UPLOAD_KIND_PROVIDER);
        data.putLong(LAST_UPLOADED_PROVIDER_KEY, providerId);
        data.putInt(LAST_UPLOAD_SLOT_KEY, slot);
        data.putLong(LAST_UPLOAD_POS_KEY, locator != null ? locator.pos() : Long.MIN_VALUE);
        data.putString(LAST_UPLOAD_SIDE_KEY, locator != null ? locator.side() : "");
        data.putString(LAST_UPLOAD_DIM_KEY, locator != null ? locator.dimension() : "");
        data.remove(LAST_UPLOAD_MATRIX_PLUS_KEY);
    }

    public static void recordMatrixUpload(ServerPlayer player, BlockPos pos, String dimension, boolean isPlus, int slot) {
        if (player == null || pos == null || slot < 0) {
            return;
        }

        CompoundTag data = player.getPersistentData();
        data.putString(LAST_UPLOAD_KIND_KEY, LAST_UPLOAD_KIND_MATRIX);
        data.putLong(LAST_UPLOADED_PROVIDER_KEY, Long.MIN_VALUE);
        data.putInt(LAST_UPLOAD_SLOT_KEY, slot);
        data.putLong(LAST_UPLOAD_POS_KEY, pos.asLong());
        data.putString(LAST_UPLOAD_SIDE_KEY, "");
        data.putString(LAST_UPLOAD_DIM_KEY, dimension == null ? "" : dimension);
        data.putBoolean(LAST_UPLOAD_MATRIX_PLUS_KEY, isPlus);
    }

    public static LastUploadRecord getLastUploadRecord(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        CompoundTag data = player.getPersistentData();
        if (!data.contains(LAST_UPLOAD_KIND_KEY) || !data.contains(LAST_UPLOAD_SLOT_KEY) || !data.contains(LAST_UPLOAD_POS_KEY)) {
            return null;
        }

        return new LastUploadRecord(
                data.getString(LAST_UPLOAD_KIND_KEY),
                data.contains(LAST_UPLOADED_PROVIDER_KEY) ? data.getLong(LAST_UPLOADED_PROVIDER_KEY) : Long.MIN_VALUE,
                data.getInt(LAST_UPLOAD_SLOT_KEY),
                data.getLong(LAST_UPLOAD_POS_KEY),
                data.getString(LAST_UPLOAD_SIDE_KEY),
                data.getString(LAST_UPLOAD_DIM_KEY),
                data.getBoolean(LAST_UPLOAD_MATRIX_PLUS_KEY)
        );
    }

    public static PatternContainer findProviderContainer(ServerPlayer player, LastUploadRecord record) {
        if (player == null || record == null || record.isMatrix()) {
            return null;
        }

        Level level = findLevel(player, record.dimension());
        PatternContainer located = null;
        IGrid grid = findPlayerGrid(player);
        if (grid != null && record.pos() != Long.MIN_VALUE && dimensionMatches(level, record.dimension())) {
            located = findProviderContainerByLocation(grid, record.pos(), record.side(), record.dimension());
        }
        return located != null ? located : findProviderContainerByLegacyId(player, record.providerId());
    }

    public static Level findLevel(ServerPlayer player, String dimension) {
        if (player == null) {
            return null;
        }
        if (dimension == null || dimension.isEmpty()) {
            return player.level();
        }
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null || player.server == null) {
            return null;
        }
        return player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    // ------------------------------------------------------------------
    // 菜单 / 编码槽辅助
    // ------------------------------------------------------------------

    /**
     * 获取玩家当前的样板访问终端菜单（支持ExtendedAE和原版AE2）
     *
     * @param player 玩家
     * @return PatternAccessTermMenu实例，如果玩家没有打开则返回null
     */
    public static PatternAccessTermMenu getPatternAccessMenu(ServerPlayer player) {
        if (player == null || player.containerMenu == null) {
            return null;
        }
        // 优先检查ExtendedAE的扩展样板管理终端（使用类名检查避免直接导入）
        String containerClassName = player.containerMenu.getClass().getName();
        if (containerClassName.equals("com.glodblock.github.extendedae.container.ContainerExPatternTerminal")) {
            // ExtendedAE的容器继承自PatternAccessTermMenu，可以安全转换
            return (PatternAccessTermMenu) player.containerMenu;
        }
        // 兼容原版AE2的样板访问终端
        if (player.containerMenu instanceof PatternAccessTermMenu) {
            return (PatternAccessTermMenu) player.containerMenu;
        }
        return null;
    }

    /**
     * 从菜单目标解析 AE 网络。
     * 统一走 target -> IActionHost -> getGrid()。
     */
    public static IGrid resolveGrid(Object menu) {
        if (!(menu instanceof AEBaseMenu abm)) {
            return null;
        }
        try {
            Object target = abm.getTarget();
            if (target instanceof IActionHost host && host.getActionableNode() != null) {
                return host.getActionableNode().getGrid();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 通过上传菜单接口获取编码槽。
     */
    public static Slot getEncodedPatternSlot(Object menu) {
        if (menu instanceof IPatternUploadMenu uploadMenu) {
            return uploadMenu.getEncodedPatternSlot();
        }
        if (menu instanceof PatternEncodingTermMenu encMenu) {
            return ((PatternEncodingTermMenuAccessor) (Object) encMenu).eap$getEncodedPatternSlot();
        }
        return null;
    }

    /**
     * 写回编码槽内容（清空或保留剩余）。
     */
    public static void setEncodedPatternSlot(Object menu, ItemStack stack) {
        var slot = getEncodedPatternSlot(menu);
        if (slot != null) {
            slot.set(stack == null ? ItemStack.EMPTY : stack);
        }
    }

    /**
     * 通过服务器ID获取PatternContainer
     * 兼容ExtendedAE的ContainerExPatternTerminal和原版PatternAccessTermMenu
     *
     * @param menu 样板访问终端菜单
     * @param providerId 供应器服务器ID
     * @return PatternContainer实例，如果不存在则返回null
     */
    private static PatternContainer getPatternContainerById(PatternAccessTermMenu menu, long providerId) {
        try {
            // 通过反射访问byId字段（ExtendedAE继承了这个字段）
            Field byIdField = findByIdField(menu.getClass());
            if (byIdField == null) {
                System.err.println("ExtendedAE Plus: Unable to find byId field");
                return null;
            }

            byIdField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<Long, Object> byId = (Map<Long, Object>) byIdField.get(menu);

            Object containerTracker = byId.get(providerId);
            if (containerTracker == null) {
                return null;
            }

            // 从ContainerTracker中获取PatternContainer
            Field containerField = findContainerField(containerTracker.getClass());
            if (containerField == null) {
                System.err.println("ExtendedAE Plus: Unable to find container field");
                return null;
            }

            containerField.setAccessible(true);
            return (PatternContainer) containerField.get(containerTracker);

        } catch (Exception e) {
            System.err.println("ExtendedAE Plus: Failed to get PatternContainer, error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 在类层次结构中查找byId字段
     */
    private static Field findByIdField(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField("byId");
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 在类层次结构中查找container字段
     */
    private static Field findContainerField(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField("container");
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 列出当前菜单中所有供应器的服务器ID（原样返回 byId 的 key 集合）。
     */
    public static List<Long> getAllProviderIds(PatternAccessTermMenu menu) {
        List<Long> result = new ArrayList<>();
        if (menu == null) return result;
        try {
            Field byIdField = findByIdField(menu.getClass());
            if (byIdField == null) return result;
            byIdField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, Object> byId = (Map<Long, Object>) byIdField.get(menu);
            if (byId != null) {
                result.addAll(byId.keySet());
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 供应器定位
    // ------------------------------------------------------------------

    private static PatternContainer findProviderContainerByLegacyId(ServerPlayer player, long providerId) {
        if (providerId >= 0) {
            PatternAccessTermMenu accessMenu = getPatternAccessMenu(player);
            return accessMenu != null ? getPatternContainerById(accessMenu, providerId) : null;
        }

        int index = decodeProviderIndex(providerId);
        if (index < 0) {
            return null;
        }

        List<PatternContainer> providers;
        if (player.containerMenu instanceof PatternEncodingTermMenu encMenu) {
            providers = listAvailableProvidersFromGrid(encMenu);
        } else {
            IGrid grid = findPlayerGrid(player);
            providers = grid != null ? listAvailableProvidersFromGrid(grid) : Collections.emptyList();
        }
        return index < providers.size() ? providers.get(index) : null;
    }

    private static int decodeProviderIndex(long providerId) {
        if (providerId >= 0) return -1;
        long idx = -1L - providerId;
        if (idx > Integer.MAX_VALUE) return -1;
        return (int) idx;
    }

    private static PatternContainer findProviderContainerByLocation(IGrid grid, long pos, String side, String dimension) {
        if (grid == null) {
            return null;
        }

        try {
            for (var machineClass : grid.getMachineClasses()) {
                if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
                for (PatternContainer container : grid.getMachines(containerClass)) {
                    if (container != null && matchesContainer(container, pos, side, dimension)) {
                        return container;
                    }
                }
                for (PatternContainer container : grid.getActiveMachines(containerClass)) {
                    if (container != null && matchesContainer(container, pos, side, dimension)) {
                        return container;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean matchesContainer(PatternContainer container, long pos, String side, String dimension) {
        HostLocator locator = getHostLocator(container);
        return locator != null
                && locator.pos() == pos
                && locator.side().equals(side == null ? "" : side)
                && (dimension == null || dimension.isEmpty() || dimension.equals(locator.dimension()));
    }

    private static HostLocator getHostLocator(PatternContainer container) {
        if (!(container instanceof PatternProviderLogicAccessor accessor)) {
            return null;
        }

        PatternProviderLogicHost host = accessor.eap$host();
        if (host == null) {
            return null;
        }

        BlockEntity blockEntity = host.getBlockEntity();
        if (blockEntity == null) {
            return null;
        }

        String side = "";
        if (host instanceof AEBasePart part) {
            side = part.getSide().getSerializedName();
        }
        String dimension = blockEntity.getLevel() != null
                ? blockEntity.getLevel().dimension().location().toString()
                : "";
        return new HostLocator(blockEntity.getBlockPos().asLong(), side, dimension);
    }

    private static IGrid findPlayerGrid(ServerPlayer player) {
        if (player == null || player.containerMenu == null) {
            return null;
        }

        try {
            if (player.containerMenu instanceof AEBaseMenu abm) {
                Object target = abm.getTarget();
                if (target instanceof IActionHost host && host.getActionableNode() != null) {
                    return host.getActionableNode().getGrid();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean dimensionMatches(Level level, String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return true;
        }
        return level != null && dimension.equals(level.dimension().location().toString());
    }

    // ------------------------------------------------------------------
    // 供应器查询 / 槽位工具
    // ------------------------------------------------------------------

    /**
     * 基于编码终端菜单的 AE Grid 遍历，列出“可在终端中可见且有空位”的供应器容器。
     * 返回顺序稳定：按 grid 的 machineClasses 顺序，再按 activeMachines 迭代顺序。
     */
    public static List<PatternContainer> listAvailableProvidersFromGrid(PatternEncodingTermMenu menu) {
        // 原生菜单：统一走 resolveGrid 从 target 解析网络
        return listAvailableProvidersFromGrid(resolveGrid(menu));
    }

    /**
     * 基于上传菜单契约的 AE Grid 遍历，供第三方终端复用。
     */
    public static List<PatternContainer> listAvailableProvidersFromGrid(IPatternUploadMenu menu) {
        return listAvailableProvidersFromGrid(resolveGrid(menu));
    }

    public static List<PatternContainer> listAvailableProvidersFromGrid(IGrid grid) {
        List<PatternContainer> list = new ArrayList<>();
        if (grid == null) return list;
        try {
            for (var machineClass : grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
                    for (var container : grid.getActiveMachines(containerClass)) {
                        if (container == null || !container.isVisibleInTerminal()) continue;
                        InternalInventory inv = container.getTerminalPatternInventory();
                        if (inv == null || inv.size() <= 0) continue;
                        int slotLimit = getAccessiblePatternSlotCount(container, inv);
                        for (int i = 0; i < slotLimit; i++) {
                            if (inv.getStackInSlot(i).isEmpty()) {
                                list.add(container);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** 获取供应器显示名（优先组名）- 返回 Component 以支持客户端翻译 */
    public static Component getProviderDisplayNameComponent(PatternContainer container) {
        if (container == null) {
            return Component.translatable("extendedae_plus.provider.unknown");
        }
        try {
            var group = container.getTerminalGroup();
            if (group != null) return group.name(); // 直接返回 Component
        } catch (Throwable ignored) {
        }
        return Component.translatable("extendedae_plus.provider.default");
    }

    /**
     * 获取供应器显示名（优先组名）- 已弃用
     * 注意：此方法在服务端调用时会使用服务端语言，不推荐用于客户端显示
     */
    @Deprecated
    public static String getProviderDisplayName(PatternContainer container) {
        return getProviderDisplayNameComponent(container).getString();
    }

    /** 计算供应器空槽位数量 */
    public static int getAvailableSlots(PatternContainer container) {
        if (container == null) return -1;
        InternalInventory inv = container.getTerminalPatternInventory();
        if (inv == null) return -1;
        int slotLimit = getAccessiblePatternSlotCount(container, inv);
        int available = 0;
        for (int i = 0; i < slotLimit; i++) {
            if (inv.getStackInSlot(i).isEmpty()) available++;
        }
        return available;
    }

    public static int getAccessiblePatternSlotCount(PatternContainer container) {
        if (container == null) {
            return 0;
        }
        return getAccessiblePatternSlotCount(container, container.getTerminalPatternInventory());
    }

    public static boolean isAccessiblePatternSlot(PatternContainer container, int slot) {
        return slot >= 0 && slot < getAccessiblePatternSlotCount(container);
    }

    public static ItemStack insertIntoAccessiblePatternSlots(PatternContainer container, ItemStack stack, IAEItemFilter filter) {
        if (container == null) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        return insertIntoAccessiblePatternSlots(container.getTerminalPatternInventory(),
                getAccessiblePatternSlotCount(container), stack, filter);
    }

    private static int getAccessiblePatternSlotCount(PatternContainer container, InternalInventory inventory) {
        return inventory == null ? 0 : inventory.size();
    }

    private static ItemStack insertIntoAccessiblePatternSlots(InternalInventory inventory, int slotLimit, ItemStack stack, IAEItemFilter filter) {
        if (inventory == null || stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }

        ItemStack remain = stack.copy();
        int limit = Math.max(0, Math.min(inventory.size(), slotLimit));
        for (int i = 0; i < limit && !remain.isEmpty(); i++) {
            if (filter != null && !filter.allowInsert(inventory, i, remain)) {
                continue;
            }
            remain = inventory.insertItem(i, remain, false);
        }
        return remain;
    }

    public static ItemStack[] snapshotInventory(InternalInventory inv, int slotLimit) {
        int limit = Math.max(0, Math.min(inv.size(), slotLimit));
        ItemStack[] snapshot = new ItemStack[limit];
        for (int i = 0; i < limit; i++) {
            snapshot[i] = inv.getStackInSlot(i).copy();
        }
        return snapshot;
    }

    public static int findLastChangedSlot(InternalInventory inv, ItemStack[] before, int slotLimit) {
        int changedSlot = -1;
        int limit = Math.max(0, Math.min(inv.size(), slotLimit));
        for (int i = 0; i < limit; i++) {
            ItemStack previous = i < before.length ? before[i] : ItemStack.EMPTY;
            ItemStack current = inv.getStackInSlot(i);
            if (!ItemStack.matches(previous, current)) {
                changedSlot = i;
            }
        }
        return changedSlot;
    }

    // ------------------------------------------------------------------
    // 重复检测 / 回收
    // ------------------------------------------------------------------

    /**
     * 复制样板并剔除编码作者标记（{@code encodePlayer}）。
     *
     * <p>「除编码作者以外完全相同」的比对语义统一以此为准：不同玩家编码的同一配方样板
     * 剔除作者后应当视为相同。矩阵与供应器两处重复检测共用，避免逻辑漂移。</p>
     */
    public static ItemStack stripEncodePlayer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        CustomData defaultData = CustomData.of(new CompoundTag());
        ItemStack copy = stack.copy();
        CompoundTag tag = copy.getOrDefault(DataComponents.CUSTOM_DATA, defaultData).copyTag();
        tag.remove("encodePlayer");
        copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return copy;
    }

    /**
     * 目标供应器的样板库存中是否已存在与给定样板「除编码作者以外完全相同」的样板。
     *
     * <p>仅检查传入容器本身，不扩展到同名组或整个网络（issue #140 的检测范围约定）。</p>
     */
    public static boolean providerContainsPattern(PatternContainer container, ItemStack pattern) {
        if (container == null || pattern == null || pattern.isEmpty()) {
            return false;
        }
        InternalInventory inv = container.getTerminalPatternInventory();
        if (inv == null) {
            return false;
        }
        ItemStack target = stripEncodePlayer(pattern);
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameComponents(stripEncodePlayer(s), target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把编码槽内的样板回收为等量空白样板并清空编码槽。
     *
     * <p>优先放回空白样板槽，放不下则退回玩家背包——与矩阵侧重复命中的处理保持一致。</p>
     */
    public static void recycleEncodedPatternAsBlanks(ServerPlayer player, Object menu, ItemStack pattern) {
        int count = pattern == null ? 0 : pattern.getCount();
        if (count > 0) {
            ItemStack blanks = AEItems.BLANK_PATTERN.stack(count);
            boolean placed = false;
            if (menu instanceof PatternEncodingTermMenu encMenu) {
                var blankSlot = ((PatternEncodingTermMenuAccessor) (Object) encMenu).eap$getBlankPatternSlot();
                if (blankSlot != null && blankSlot.mayPlace(blanks)) {
                    ItemStack remain = blankSlot.safeInsert(blanks);
                    if (!remain.isEmpty() && player != null) {
                        player.getInventory().placeItemBackInInventory(remain, false);
                    }
                    placed = true;
                }
            }
            if (!placed && player != null) {
                player.getInventory().placeItemBackInInventory(blanks, false);
            }
        }
        setEncodedPatternSlot(menu, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------
    // 上传入口
    // ------------------------------------------------------------------

    /**
     * 编码后自动上传的编排入口：按配置的优先级尝试装配矩阵与 ECO 合成系统。
     *
     * <p>每次调用只会向一个目标写入样板：优先目标接受后立即返回，仅在优先目标拒绝时
     * 才回退到另一个目标，避免同一样板被重复写入两处。</p>
     *
     * <p>ECO 合成系统仅在模组已加载且配置启用时参与；任一侧的「已存在」判定同样视为
     * 已满足，不会继续写入另一侧。</p>
     *
     * <p>成功写入后按实际目标发送提示：矩阵与 ECO 各自使用对应的成功文案，ECO 侧「已存在」
     * 单独提示，避免玩家误以为写入失败。提示仅在编排层发送，优先目标被拒时不产生中间提示，
     * 因此回退流程不会出现「先失败后成功」的连续消息。</p>
     *
     * <p>ECO 开关与优先级均为<b>客户端配置</b>，专用服务器不加载，只能由客户端在编码动作时随包上报
     * （见 {@code EncodeWithShiftFlagC2SPacket}）。供应器唯一直传不在此处理，由编码菜单先行尝试。</p>
     *
     * @param player 服务器玩家
     * @param menu   图样编码终端菜单
     * @param prefs  客户端上报的自动上传偏好，可为 null（缺失时回退默认值）
     * @return 是否有目标接受了该样板
     */
    public static boolean uploadFromEncodingMenuByPriority(ServerPlayer player, PatternEncodingTermMenu menu, UploadPreferences prefs) {
        if (player == null || menu == null) {
            return false;
        }
        UploadPreferences p = prefs != null ? prefs : UploadPreferences.defaults();

        boolean ecoEnabled = p.ecoAutoUploadEnabled() && NeoECOAECompat.isAvailable();
        if (p.uploadPriority() == PatternUploadPriority.ECO && ecoEnabled) {
            NeoECOAECompat.UploadOutcome ecoOutcome = NeoECOAECompat.tryUploadFromEncodingMenu(player, menu);
            if (NeoECOAECompat.isAccepted(ecoOutcome)) {
                notifyEcoUpload(player, ecoOutcome);
                return true;
            }
            if (MatrixUploadUtil.uploadFromEncodingMenuToMatrix(player, menu)) {
                notifyMatrixUpload(player);
                return true;
            }
            return false;
        }

        if (MatrixUploadUtil.uploadFromEncodingMenuToMatrix(player, menu)) {
            notifyMatrixUpload(player);
            return true;
        }
        if (ecoEnabled) {
            NeoECOAECompat.UploadOutcome ecoOutcome = NeoECOAECompat.tryUploadFromEncodingMenu(player, menu);
            if (NeoECOAECompat.isAccepted(ecoOutcome)) {
                notifyEcoUpload(player, ecoOutcome);
                return true;
            }
        }
        return false;
    }

    /**
     * 发送装配矩阵上传成功提示。
     */
    private static void notifyMatrixUpload(ServerPlayer player) {
        if (player != null) {
            player.sendSystemMessage(Component.translatable("extendedae_plus.upload_to_matrix.success"));
        }
    }

    /**
     * 按 ECO 侧插入结果发送提示。
     *
     * <p>{@link NeoECOAECompat.UploadOutcome#ALREADY_PRESENT} 使用独立文案：样板已存在于 ECO
     * 合成系统，与本次写入成功需要区分。</p>
     */
    private static void notifyEcoUpload(ServerPlayer player, NeoECOAECompat.UploadOutcome outcome) {
        if (player == null) {
            return;
        }
        String key = outcome == NeoECOAECompat.UploadOutcome.ALREADY_PRESENT
                ? "extendedae_plus.upload_to_eco.duplicate"
                : "extendedae_plus.upload_to_eco.success";
        player.sendSystemMessage(Component.translatable(key));
    }

    /**
     * 编码后供应器唯一匹配直传：在编码终端所在网络中按名称定位供应器并写入，成功后发送带名提示。
     *
     * <p>唯一匹配由客户端判定（名称与搜索词同处客户端语言环境），这里只按名称定位。名称按
     * {@link Component} 结构比较（翻译键或字面量），与语言无关；同名供应器按空位从多到少依次尝试。
     * 按当前网络实时定位而不是沿用客户端缓存的下标，供应器被填满导致列表变化时也不会传错目标。</p>
     *
     * <p>与手动上传共用同一套重复检测：目标供应器已含「除编码作者以外完全相同」的样板时
     * 不再写入，改为提示并回收样板，返回 {@link UploadOutcome#DUPLICATE}。</p>
     *
     * @param providerName 客户端判定唯一匹配的供应器名称
     * @return 写入成功为 {@link UploadOutcome#SUCCESS}；命中重复为 {@link UploadOutcome#DUPLICATE}；
     *         名称未匹配到供应器或全部拒收为 {@link UploadOutcome#FAILED}
     */
    public static UploadOutcome uploadFromEncodingMenuToNamedProvider(ServerPlayer player, IPatternUploadMenu menu, Component providerName) {
        if (player == null || menu == null || providerName == null) {
            return UploadOutcome.FAILED;
        }
        Slot encodedSlot = menu.getEncodedPatternSlot();
        if (encodedSlot == null) {
            return UploadOutcome.FAILED;
        }
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return UploadOutcome.FAILED;
        }

        List<PatternContainer> candidates = new ArrayList<>();
        for (PatternContainer c : listAvailableProvidersFromGrid(menu)) {
            if (providerName.equals(getProviderDisplayNameComponent(c))) {
                candidates.add(c);
            }
        }
        candidates.sort(Comparator.<PatternContainer>comparingInt(ProviderUploadUtil::getAvailableSlots).reversed());

        for (PatternContainer c : candidates) {
            // 与手动上传一致：目标供应器已含「除编码作者以外完全相同」的样板时不再重复写入。
            // 自动上传没有 Ctrl 之类的屏蔽入口，命中即回收样板并终止，不再回退到矩阵/ECO。
            if (providerContainsPattern(c, stack)) {
                player.sendSystemMessage(Component.translatable(
                        "extendedae_plus.message.provider.duplicate", providerName));
                recycleEncodedPatternAsBlanks(player, menu, stack);
                return UploadOutcome.DUPLICATE;
            }
            InternalInventory inv = c.getTerminalPatternInventory();
            int slotLimit = getAccessiblePatternSlotCount(c, inv);
            ItemStack[] before = snapshotInventory(inv, slotLimit);
            ItemStack toInsert = stack.copy();
            ItemStack remain = insertIntoAccessiblePatternSlots(inv, slotLimit, toInsert, new ExtendedAEPatternFilter());
            int inserted = toInsert.getCount() - remain.getCount();
            if (inserted > 0) {
                stack.shrink(inserted);
                encodedSlot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                recordProviderUpload(player, Long.MIN_VALUE, c, findLastChangedSlot(inv, before, slotLimit));
                player.sendSystemMessage(Component.translatable("extendedae_plus.upload_to_provider.success", providerName));
                return UploadOutcome.SUCCESS;
            }
        }
        return UploadOutcome.FAILED;
    }

    /**
     * 从 AE2 的图样编码终端菜单上传当前“已编码图样”至当前网络中任意可用的样板供应器。
     * 策略：
     * 1) 仅当 encoded 槽位存在有效编码样板时执行；
     * 2) 通过 menu.getNetworkNode() 获取 IGrid，遍历在线的 PatternContainer；
     * 3) 仅选择在终端中可见（isVisibleInTerminal）且库存存在空位的供应器；
     * 4) 只向当前已解锁槽位按 Pattern 过滤规则尝试插入；
     * 5) 成功后清空 encoded 槽位，返回 true；否则返回 false。
     */
    public static boolean uploadFromEncodingMenuToAnyProvider(ServerPlayer player, PatternEncodingTermMenu menu) {
        return uploadFromEncodingMenuToAnyProvider(player, (Object) menu);
    }

    /**
     * 接口重载：兼容第三方编码终端菜单（实现 {@link IPatternUploadMenu}）。
     */
    public static boolean uploadFromEncodingMenuToAnyProvider(ServerPlayer player, IPatternUploadMenu menu) {
        return uploadFromEncodingMenuToAnyProvider(player, (Object) menu);
    }

    private static boolean uploadFromEncodingMenuToAnyProvider(ServerPlayer player, Object menu) {
        if (player == null || menu == null) {
            return false;
        }
        // 读取已编码槽位的物品（优先走上传菜单契约）
        var encodedSlot = getEncodedPatternSlot(menu);
        ItemStack stack = encodedSlot != null ? encodedSlot.getItem() : ItemStack.EMPTY;
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        // 获取 AE 网络（1.21 经由 AEBaseMenu target + IActionHost）
        IGrid grid = resolveGrid(menu);
        if (grid == null) {
            return false;
        }

        // 遍历在线的 PatternContainer，寻找第一个可见且有空位的供应器
        try {
            for (var machineClass : grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
                    for (var container : grid.getActiveMachines(containerClass)) {
                        if (container == null || !container.isVisibleInTerminal()) {
                            continue;
                        }
                        InternalInventory inv = container.getTerminalPatternInventory();
                        if (inv == null || inv.size() <= 0) {
                            continue;
                        }
                        boolean hasEmpty = false;
                        int slotLimit = getAccessiblePatternSlotCount(container, inv);
                        for (int i = 0; i < slotLimit; i++) {
                            if (inv.getStackInSlot(i).isEmpty()) {
                                hasEmpty = true;
                                break;
                            }
                        }
                        if (!hasEmpty) {
                            continue;
                        }

                        // 按 AE2 样板过滤规则尝试插入
                        var patternFilter = new ExtendedAEPatternFilter();
                        ItemStack[] before = snapshotInventory(inv, slotLimit);
                        ItemStack toInsert = stack.copy();
                        ItemStack remain = insertIntoAccessiblePatternSlots(inv, slotLimit, toInsert, patternFilter);
                        if (remain.getCount() < toInsert.getCount()) {
                            int inserted = toInsert.getCount() - remain.getCount();
                            stack.shrink(inserted);
                            setEncodedPatternSlot(menu, stack.isEmpty() ? ItemStack.EMPTY : stack);
                            recordProviderUpload(player, Long.MIN_VALUE, container, findLastChangedSlot(inv, before, slotLimit));
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // 忽略异常以避免噪声
        }
        return false;
    }

    /**
     * 将图样编码终端的“已编码图样”上传到指定的样板供应器（通过 providerId 定位）。
     */
    public static UploadOutcome uploadFromEncodingMenuToProvider(ServerPlayer player, PatternEncodingTermMenu menu, long providerId) {
        return uploadFromEncodingMenuToProvider(player, (IPatternUploadMenu) menu, providerId);
    }

    /**
     * 通过上传菜单契约将已编码样板上传到指定供应器（byId 模式）。
     *
     * <p>若用户选定的目标供应器已含「除编码作者以外完全相同」的样板，
     * 直接返回 {@link UploadOutcome#DUPLICATE}，不写入任何槽位（样板留在终端，由调用方回收为空白样板）。
     * 检测只针对用户选定的那一个供应器，不扩展到同名回退项。</p>
     */
    public static UploadOutcome uploadFromEncodingMenuToProvider(ServerPlayer player, IPatternUploadMenu menu, long providerId) {
        if (player == null || menu == null) {
            return UploadOutcome.FAILED;
        }
        var encodedSlot = menu.getEncodedPatternSlot();
        if (encodedSlot == null) {
            return UploadOutcome.FAILED;
        }
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return UploadOutcome.FAILED;
        }

        PatternAccessTermMenu accessMenu = getPatternAccessMenu(player);
        if (accessMenu == null) {
            return UploadOutcome.FAILED;
        }
        PatternContainer target = getPatternContainerById(accessMenu, providerId);
        if (target != null && providerContainsPattern(target, stack)) {
            return UploadOutcome.DUPLICATE;
        }
        // 先确定目标容器名称，用于同名回退
        String targetName = getProviderDisplayName(providerId, accessMenu);
        // 构建尝试顺序：先指定ID，其次同名的其他ID
        List<Long> tryIds = new ArrayList<>();
        tryIds.add(providerId);
        for (Long id : getAllProviderIds(accessMenu)) {
            if (id == null || id.longValue() == providerId) continue;
            String name = getProviderDisplayName(id, accessMenu);
            if (name != null && name.equals(targetName)) {
                tryIds.add(id);
            }
        }

        // 按顺序逐个尝试插入
        for (Long id : tryIds) {
            PatternContainer c = getPatternContainerById(accessMenu, id);
            if (c == null || !c.isVisibleInTerminal()) continue;
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;

            var patternFilter = new ExtendedAEPatternFilter();
            int slotLimit = getAccessiblePatternSlotCount(c, inv);
            ItemStack[] before = snapshotInventory(inv, slotLimit);
            ItemStack toInsert = stack.copy();
            ItemStack remain = insertIntoAccessiblePatternSlots(inv, slotLimit, toInsert, patternFilter);
            if (remain.getCount() < toInsert.getCount()) {
                int inserted = toInsert.getCount() - remain.getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    encodedSlot.set(ItemStack.EMPTY);
                } else {
                    encodedSlot.set(stack);
                }
                recordProviderUpload(player, id, c, findLastChangedSlot(inv, before, slotLimit));
                return UploadOutcome.SUCCESS;
            }
        }
        return UploadOutcome.FAILED;
    }

    /**
     * 基于“索引”的定向上传：使用 listAvailableProvidersFromGrid(menu) 的顺序，
     * 将编码槽样板插入到第 index 个供应器。
     */
    public static UploadOutcome uploadFromEncodingMenuToProviderByIndex(ServerPlayer player, PatternEncodingTermMenu menu, int index) {
        return uploadFromEncodingMenuToProviderByIndex(player, (IPatternUploadMenu) menu, index);
    }

    /**
     * 索引模式上传：兼容第三方编码终端菜单（实现 {@link IPatternUploadMenu}）。
     *
     * <p>若 index 指定的目标供应器已含「除编码作者以外完全相同」的样板，
     * 直接返回 {@link UploadOutcome#DUPLICATE}，不写入任何槽位（含同名回退项）。
     * 检测只针对 index 指定的那一个供应器。</p>
     */
    public static UploadOutcome uploadFromEncodingMenuToProviderByIndex(ServerPlayer player, IPatternUploadMenu menu, int index) {
        if (player == null || menu == null || index < 0) return UploadOutcome.FAILED;
        List<PatternContainer> list = listAvailableProvidersFromGrid(menu);
        if (index >= list.size()) return UploadOutcome.FAILED;
        var container = list.get(index);
        if (container == null) return UploadOutcome.FAILED;

        var encodedSlot = menu.getEncodedPatternSlot();
        if (encodedSlot == null) return UploadOutcome.FAILED;
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return UploadOutcome.FAILED;
        }
        if (providerContainsPattern(container, stack)) {
            return UploadOutcome.DUPLICATE;
        }

        // 以名称为键，同名供应器依次尝试：先 index 指定的，再同名的其他
        String targetName = getProviderDisplayName(container);
        List<PatternContainer> tryList = new ArrayList<>();
        tryList.add(container);
        for (PatternContainer c : list) {
            if (c == null || c == container) continue;
            String name = getProviderDisplayName(c);
            if (name != null && name.equals(targetName)) {
                tryList.add(c);
            }
        }

        for (PatternContainer c : tryList) {
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;
            var patternFilter = new ExtendedAEPatternFilter();
            int slotLimit = getAccessiblePatternSlotCount(c, inv);
            ItemStack[] before = snapshotInventory(inv, slotLimit);
            ItemStack toInsert = stack.copy();
            ItemStack remain = insertIntoAccessiblePatternSlots(inv, slotLimit, toInsert, patternFilter);
            if (remain.getCount() < toInsert.getCount()) {
                int inserted = toInsert.getCount() - remain.getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    encodedSlot.set(ItemStack.EMPTY);
                } else {
                    encodedSlot.set(stack);
                }
                recordProviderUpload(player, -1L - index, c, findLastChangedSlot(inv, before, slotLimit));
                return UploadOutcome.SUCCESS;
            }
        }
        return UploadOutcome.FAILED;
    }

    /**
     * 将玩家背包中的样板上传到指定的样板供应器
     * 兼容ExtendedAE和原版AE2
     *
     * @param player 玩家
     * @param playerSlotIndex 玩家背包槽位索引
     * @param providerId 目标样板供应器的服务器ID
     * @return 是否上传成功
     */
    public static boolean uploadPatternToProvider(ServerPlayer player, int playerSlotIndex, long providerId) {
        // 1. 验证玩家是否打开了样板访问终端
        PatternAccessTermMenu menu = getPatternAccessMenu(player);
        if (menu == null) {
            return false;
        }

        // 2. 获取玩家背包中的物品
        ItemStack playerItem = player.getInventory().getItem(playerSlotIndex);
        if (playerItem.isEmpty()) {
            return false;
        }

        // 3. 验证是否是编码样板
        if (!PatternDetailsHelper.isEncodedPattern(playerItem)) {
            return false;
        }

        // 4. 获取目标样板供应器
        PatternContainer patternContainer = getPatternContainerById(menu, providerId);
        if (patternContainer == null) {
            return false;
        }

        // 5. 获取样板供应器的库存
        InternalInventory patternInventory = patternContainer.getTerminalPatternInventory();
        if (patternInventory == null) {
            return false;
        }

        // 6. 仅允许向当前已解锁的槽位插入样板，避免锁页被旁路写入
        var patternFilter = new ExtendedAEPatternFilter();
        int slotLimit = getAccessiblePatternSlotCount(patternContainer, patternInventory);

        // 7. 尝试插入样板
        ItemStack[] before = snapshotInventory(patternInventory, slotLimit);
        ItemStack itemToInsert = playerItem.copy();
        ItemStack remain = insertIntoAccessiblePatternSlots(patternInventory, slotLimit, itemToInsert, patternFilter);
        int insertedCount = itemToInsert.getCount() - remain.getCount();
        if (insertedCount > 0) {
            playerItem.shrink(insertedCount);
            if (playerItem.isEmpty()) {
                player.getInventory().setItem(playerSlotIndex, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(playerSlotIndex, playerItem);
            }
            recordProviderUpload(player, providerId, patternContainer, findLastChangedSlot(patternInventory, before, slotLimit));
            return true;
        } else {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 兼容旧 ID 入口
    // ------------------------------------------------------------------

    /**
     * 获取样板供应器中的空槽位数量
     *
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单（支持ExtendedAE）
     * @return 空槽位数量，如果无法访问则返回-1
     */
    public static int getAvailableSlots(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return -1;
        }

        InternalInventory inventory = container.getTerminalPatternInventory();
        if (inventory == null) {
            return -1;
        }

        int availableSlots = 0;
        int slotLimit = getAccessiblePatternSlotCount(container, inventory);
        for (int i = 0; i < slotLimit; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                availableSlots++;
            }
        }

        return availableSlots;
    }

    /**
     * 获取样板供应器的显示名称
     *
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单
     * @return 显示名称，如果无法获取则返回"未知供应器"
     */
    public static Component getProviderDisplayNameComponent(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return Component.translatable("extendedae_plus.provider.unknown");
        }

        try {
            // 尝试获取供应器的组信息来构建显示名称
            var group = container.getTerminalGroup();
            if (group != null) {
                return group.name(); // 直接返回 Component，不转换为 String
            }
        } catch (Exception e) {
            // 忽略异常，使用默认名称
        }

        return Component.translatable("extendedae_plus.provider.named_id", providerId);
    }

    /**
     * 获取供应器显示名称（已弃用，请使用 getProviderDisplayNameComponent）
     * 注意：此方法在服务端调用时会使用服务端语言，不推荐用于客户端显示
     */
    @Deprecated
    public static String getProviderDisplayName(long providerId, PatternAccessTermMenu menu) {
        return getProviderDisplayNameComponent(providerId, menu).getString();
    }

    /**
     * 验证样板供应器是否可用
     *
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单
     * @return 是否可用
     */
    public static boolean isProviderAvailable(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return false;
        }

        // 检查是否在终端中可见
        if (!container.isVisibleInTerminal()) {
            return false;
        }

        // 检查是否连接到网络
        return container.getGrid() != null;
    }

    /**
     * ExtendedAE兼容的样板过滤器
     * 使用AE2的PatternDetailsHelper进行样板验证
     */
    public static class ExtendedAEPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }
    }
}