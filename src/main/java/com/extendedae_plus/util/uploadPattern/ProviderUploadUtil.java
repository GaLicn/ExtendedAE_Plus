package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.core.definitions.AEItems;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.PlayerSource;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.AEBasePart;
import appeng.util.inv.filter.IAEItemFilter;
import com.extendedae_plus.api.bridge.CompatUpgradeProvider;
import com.extendedae_plus.api.bridge.PatternProviderLogicUpgradeCompatBridge;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.util.PatternProviderDataUtil;
import com.extendedae_plus.util.PatternTerminalUtil;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 与样板供应器（provider）上传相关的工具类：
 * - uploadPatternToProvider (从玩家背包上传)
 * - uploadFromEncodingMenuToProvider (从编码终端上传至指定 providerId)
 * - uploadFromEncodingMenuToProviderByIndex (按网格顺序 index 上传)
 *
 * 其中使用 PatternTerminalUtil 提供的反射/容器访问工具。
 */
public final class ProviderUploadUtil {
    private static final String PENDING_DATA_KEY = "eap_ctrlq_pending_provider_upload_id";
    private static final String PENDING_STACK_KEY = "eap_ctrlq_pending_provider_upload_stack";
    private static final String LAST_UPLOADED_PROVIDER_KEY = "eap_last_uploaded_provider_id";
    private static final String LAST_UPLOAD_KIND_KEY = "eap_last_upload_kind";
    private static final String LAST_UPLOAD_SLOT_KEY = "eap_last_upload_slot";
    private static final String LAST_UPLOAD_POS_KEY = "eap_last_upload_pos";
    private static final String LAST_UPLOAD_SIDE_KEY = "eap_last_upload_side";
    private static final String LAST_UPLOAD_DIM_KEY = "eap_last_upload_dim";
    private static final String LAST_UPLOAD_MATRIX_PLUS_KEY = "eap_last_upload_matrix_plus";
    private static final String LAST_UPLOAD_KIND_PROVIDER = "provider";
    private static final String LAST_UPLOAD_KIND_MATRIX = "matrix";
    private static final String LAST_EXPANSION_NOTIFY_KEY = "eap_last_expansion_notify";
    /** 同一装卡失败原因的提示去重窗口（tick）。 */
    private static final int EXPANSION_NOTIFY_DEDUP_TICKS = 40;

    private ProviderUploadUtil() {}

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

    /**
     * 从网络取扩容卡装入升级槽的结果。
     *
     * <p>{@link #NOT_ELIGIBLE} 表示该供应器根本不支持扩容卡，属于正常情况，
     * 不应当给玩家任何提示；其余失败原因都值得告知玩家，否则表现为「上传没反应」。</p>
     */
    public enum ExpansionInstallResult {
        /** 装入成功。 */
        SUCCESS,
        /** 未走到装卡这一步：还有空位，或样板本身插不进（过滤拒收/重复）。 */
        NOT_ATTEMPTED,
        /** 该供应器不支持扩容卡（非扩展样板供应器，或没有可用的升级槽实现）。 */
        NOT_ELIGIBLE,
        /** 升级槽已满、已被扩容卡占满，或该槽不接受扩容卡。 */
        NO_UPGRADE_SLOT,
        /** 无法访问 AE 网络。 */
        NO_NETWORK,
        /** 网络里取不到扩容卡（没有该物品，或网络能量不足）。 */
        NO_CARD_IN_NETWORK
    }

    /**
     * 复制样板并剔除编码作者标记（{@code encodePlayer}）。
     *
     * <p>「除编码作者以外完全相同」的比对语义统一以此为准：不同玩家编码的同一配方样板
     * 剔除作者后应当视为相同。</p>
     */
    private static ItemStack stripEncodePlayer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        if (copy.getTag() != null) {
            copy.getTag().remove("encodePlayer");
        }
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
            if (ItemStack.isSameItemSameTags(stripEncodePlayer(s), target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把编码槽内的样板回收为等量空白样板并清空编码槽。
     *
     * <p>优先放回空白样板槽，放不下则退回玩家背包。</p>
     */
    public static void recycleEncodedPatternAsBlanks(ServerPlayer player, Object menu, ItemStack pattern) {
        int count = pattern == null ? 0 : pattern.getCount();
        if (count > 0) {
            ItemStack blanks = AEItems.BLANK_PATTERN.stack(count);
            boolean placed = false;
            if (menu instanceof PatternEncodingTermMenu encMenu) {
                var blankSlot = ((PatternEncodingTermMenuAccessor) encMenu).eap$getBlankPatternSlot();
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
        Slot slot = getEncodedPatternSlot(menu);
        if (slot != null) {
            slot.set(ItemStack.EMPTY);
        }
    }

    /**
     * 发送消息给玩家
     *
     * @param player 玩家
     * @param message 消息内容
     */
    private static void sendMessage(ServerPlayer player, String message) {
        // 静默：不再向玩家左下角发送任何提示信息
        // 如需恢复，取消下面注释即可：
        // if (player != null) {
        //     player.sendSystemMessage(Component.literal(message));
        // }
        // 如果玩家为null，静默忽略（用于测试环境）
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
        PatternAccessTermMenu menu = PatternTerminalUtil.getPatternAccessMenu(player);
        if (menu == null) {
            sendMessage(player, "ExtendedAE Plus: 请先打开样板访问终端或扩展样板管理终端");
            return false;
        }

        // 2. 获取玩家背包中的物品
        ItemStack playerItem = player.getInventory().getItem(playerSlotIndex);
        if (playerItem.isEmpty()) {
            sendMessage(player, "ExtendedAE Plus: 背包槽位为空");
            return false;
        }

        // 3. 验证是否是编码样板
        if (!PatternDetailsHelper.isEncodedPattern(playerItem)) {
            sendMessage(player, "ExtendedAE Plus: 该物品不是有效的编码样板");
            return false;
        }

        // 4. 获取目标样板供应器
        PatternContainer patternContainer = PatternTerminalUtil.getPatternContainerById(menu, providerId);
        if (patternContainer == null) {
            sendMessage(player, "ExtendedAE Plus: 找不到指定的样板供应器 (ID: " + providerId + ")");
            return false;
        }

        // 5. 获取样板供应器的库存
        InternalInventory patternInventory = patternContainer.getTerminalPatternInventory();
        if (patternInventory == null) {
            sendMessage(player, "ExtendedAE Plus: 无法访问样板供应器的库存");
            return false;
        }

        // 6. 使用AE2的标准样板过滤器进行插入；槽满时尝试从网络取扩容卡再写入
        var patternFilter = new ExtendedAEPatternFilter();
        ItemStack itemToInsert = playerItem.copy();
        ExpansionInsertResult result = insertWithExpansion(player, resolveGrid(menu),
                patternContainer, itemToInsert, patternFilter);

        if (result.remain().getCount() < itemToInsert.getCount()) {
            // 插入成功（部分或全部）
            int insertedCount = itemToInsert.getCount() - result.remain().getCount();
            playerItem.shrink(insertedCount);

            if (playerItem.isEmpty()) {
                player.getInventory().setItem(playerSlotIndex, ItemStack.EMPTY);
            }

            recordProviderUpload(player, providerId, patternContainer,
                    findLastChangedSlot(patternContainer.getTerminalPatternInventory(),
                            result.before(), result.slotLimit()));

            String terminalType = PatternTerminalUtil.isExtendedAETerminal(player) ? "扩展样板管理终端" : "样板访问终端";
            sendMessage(player, "ExtendedAE Plus: 通过" + terminalType + "成功上传 " + insertedCount + " 个样板");
            return true;
        } else {
            notifyExpansionInstallFailure(player, patternContainer, result);
            sendMessage(player, "ExtendedAE Plus: 上传失败 - 样板供应器已满或样板无效");
            return false;
        }
    }

    /**
     * 将图样编码终端的“已编码图样”上传到指定的样板供应器（通过 providerId 定位）。
     */
    public static UploadOutcome uploadFromEncodingMenuToProvider(ServerPlayer player, PatternEncodingTermMenu menu, long providerId) {
        return uploadFromEncodingMenuToProvider(player, (IPatternUploadMenu) menu, providerId);
    }

    /**
     * 通过通用菜单契约上传，兼容第三方编码终端。
     *
     * <p>若用户选定的目标供应器已含「除编码作者以外完全相同」的样板，直接返回
     * {@link UploadOutcome#DUPLICATE}，不写入任何槽位（样板留在终端，由调用方回收为空白样板）。
     * 检测只针对用户选定的那一个供应器，不扩展到同名回退项。</p>
     */
    public static UploadOutcome uploadFromEncodingMenuToProvider(ServerPlayer player, IPatternUploadMenu menu, long providerId) {
        if (player == null || menu == null) {
            return UploadOutcome.FAILED;
        }
        var encodedSlot = getEncodedPatternSlot(menu);
        if (encodedSlot == null) return UploadOutcome.FAILED;
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return UploadOutcome.FAILED;
        }

        PatternAccessTermMenu accessMenu = PatternTerminalUtil.getPatternAccessMenu(player);
        if (accessMenu == null) {
            return UploadOutcome.FAILED;
        }
        PatternContainer target = PatternTerminalUtil.getPatternContainerById(accessMenu, providerId);
        if (target != null && providerContainsPattern(target, stack)) {
            return UploadOutcome.DUPLICATE;
        }
        // 先确定目标容器名称，用于同名回退
        String targetName = PatternProviderDataUtil.getProviderDisplayName(providerId, accessMenu);
        // 构建尝试顺序：先指定ID，其次同名的其他ID
        List<Long> tryIds = new ArrayList<>();
        tryIds.add(providerId);
        List<Long> all = PatternTerminalUtil.getAllProviderIds(accessMenu);
        for (Long id : all) {
            if (id == null || id == providerId) continue;
            String name = PatternProviderDataUtil.getProviderDisplayName(id, accessMenu);
            if (name != null && name.equals(targetName)) {
                tryIds.add(id);
            }
        }

        IGrid grid = resolveGrid(menu);
        // 整轮都失败时，用最后一个「装卡失败」的供应器向玩家解释原因
        PatternContainer lastFailedContainer = null;
        ExpansionInsertResult lastFailedResult = null;
        // 按顺序逐个尝试插入
        for (Long id : tryIds) {
            PatternContainer c = PatternTerminalUtil.getPatternContainerById(accessMenu, id);
            if (c == null || !c.isVisibleInTerminal()) continue;
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;

            ItemStack toInsert = stack.copy();
            ExpansionInsertResult result = insertWithExpansion(player, grid, c, toInsert, new ExtendedAEPatternFilter());
            if (result.remain().getCount() < toInsert.getCount()) {
                int inserted = toInsert.getCount() - result.remain().getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    encodedSlot.set(ItemStack.EMPTY);
                } else {
                    encodedSlot.set(stack);
                }
                recordProviderUpload(player, id, c,
                        findLastChangedSlot(c.getTerminalPatternInventory(),
                                result.before(), result.slotLimit()));
                return UploadOutcome.SUCCESS;
            }
            if (result.shouldNotifyInstallFailure()) {
                lastFailedContainer = c;
                lastFailedResult = result;
            }
        }
        notifyExpansionInstallFailure(player, lastFailedContainer, lastFailedResult);
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
     * 通过通用菜单契约按网络顺序上传。
     *
     * <p>index 指定的目标供应器已含「除编码作者以外完全相同」的样板时返回
     * {@link UploadOutcome#DUPLICATE}，不写入也不做同名回退（由调用方回收为空白样板）。</p>
     */
    public static UploadOutcome uploadFromEncodingMenuToProviderByIndex(ServerPlayer player, IPatternUploadMenu menu, int index) {
        if (player == null || menu == null || index < 0) return UploadOutcome.FAILED;
        List<PatternContainer> list = listAvailableProvidersFromGrid(menu);
        if (index >= list.size()) return UploadOutcome.FAILED;
        var container = list.get(index);
        if (container == null) return UploadOutcome.FAILED;

        var encodedSlot = getEncodedPatternSlot(menu);
        if (encodedSlot == null) return UploadOutcome.FAILED;
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return UploadOutcome.FAILED;
        }
        if (providerContainsPattern(container, stack)) {
            return UploadOutcome.DUPLICATE;
        }

        // 以名称为键，同名供应器依次尝试：先 index 指定的，再同名的其他
        String targetName = PatternProviderDataUtil.getProviderDisplayName(container);
        List<PatternContainer> tryList = new ArrayList<>();
        tryList.add(container);
        for (PatternContainer c : list) {
            if (c == null || c == container) continue;
            String name = PatternProviderDataUtil.getProviderDisplayName(c);
            if (name != null && name.equals(targetName)) {
                tryList.add(c);
            }
        }

        IGrid grid = resolveGrid(menu);
        // 整轮都失败时，用最后一个「装卡失败」的供应器向玩家解释原因
        PatternContainer lastFailedContainer = null;
        ExpansionInsertResult lastFailedResult = null;
        for (PatternContainer c : tryList) {
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;
            ItemStack toInsert = stack.copy();
            ExpansionInsertResult result = insertWithExpansion(player, grid, c, toInsert, new ExtendedAEPatternFilter());
            if (result.remain().getCount() < toInsert.getCount()) {
                int inserted = toInsert.getCount() - result.remain().getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    encodedSlot.set(ItemStack.EMPTY);
                } else {
                    encodedSlot.set(stack);
                }
                int trueIndex = list.indexOf(c);
                long storedProviderId = trueIndex >= 0 ? -1L - trueIndex : Long.MIN_VALUE;
                recordProviderUpload(player, storedProviderId, c,
                        findLastChangedSlot(c.getTerminalPatternInventory(),
                                result.before(), result.slotLimit()));
                return UploadOutcome.SUCCESS;
            }
            if (result.shouldNotifyInstallFailure()) {
                lastFailedContainer = c;
                lastFailedResult = result;
            }
        }
        notifyExpansionInstallFailure(player, lastFailedContainer, lastFailedResult);
        return UploadOutcome.FAILED;
    }

    /** 从通用菜单契约获取编码槽。 */
    public static Slot getEncodedPatternSlot(Object menu) {
        if (menu instanceof IPatternUploadMenu uploadMenu) {
            return uploadMenu.getEncodedPatternSlot();
        }
        if (menu instanceof PatternEncodingTermMenu encodingMenu) {
            return ((PatternEncodingTermMenuAccessor) (Object) encodingMenu).eap$getEncodedPatternSlot();
        }
        return null;
    }

    /** 从编码菜单目标解析 AE 网络。 */
    public static IGrid resolveGrid(Object menu) {
        if (!(menu instanceof AEBaseMenu aeMenu)) return null;
        Object target = aeMenu.getTarget();
        if (target instanceof IActionHost host && host.getActionableNode() != null) {
            return host.getActionableNode().getGrid();
        }
        return null;
    }

    /** 基于编码终端枚举可用供应器。 */
    public static List<PatternContainer> listAvailableProvidersFromGrid(PatternEncodingTermMenu menu) {
        return listAvailableProvidersFromGrid(resolveGrid(menu));
    }

    /** 基于通用菜单契约枚举可用供应器。 */
    public static List<PatternContainer> listAvailableProvidersFromGrid(IPatternUploadMenu menu) {
        return listAvailableProvidersFromGrid(resolveGrid(menu));
    }

    /**
     * 基于 AE Grid 遍历，列出终端中可见且还能写入的供应器容器。
     *
     * <p>槽满但还能装扩容卡的供应器也列入：上传时会自动从网络取卡扩容。</p>
     */
    public static List<PatternContainer> listAvailableProvidersFromGrid(IGrid grid) {
        List<PatternContainer> list = new ArrayList<>();
        if (grid == null) return list;
        for (var machineClass : grid.getMachineClasses()) {
            if (PatternContainer.class.isAssignableFrom(machineClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
                for (var container : grid.getActiveMachines(containerClass)) {
                    if (container == null || !container.isVisibleInTerminal()) continue;
                    InternalInventory inv = container.getTerminalPatternInventory();
                    if (inv == null || inv.size() <= 0) continue;
                    // 槽满但还能装扩容卡的供应器也列入：上传时会自动取卡扩容
                    if (getAvailableSlots(container) > 0 || canInstallExpansionCard(container)) {
                        list.add(container);
                    }
                }
            }
        }
        return list;
    }

    /**
     * 编码后供应器唯一匹配直传：在编码终端所在网络中按名称定位供应器并写入，成功后发送带名提示。
     *
     * <p>唯一匹配由客户端判定（名称与搜索词同处客户端语言环境），这里只按名称定位。名称是服务端下发的
     * 原始名称（Component JSON，保留翻译键），比较与语言无关；同名供应器按空位从多到少依次尝试。
     * 按当前网络实时定位而不是沿用客户端缓存的下标，供应器被填满导致列表变化时也不会传错目标。</p>
     *
     * <p>同名候选供应器中若已含「除编码作者以外完全相同」的样板，命中即提示并回收为空白样板，
     * 返回 {@link UploadOutcome#DUPLICATE}，不再回退到矩阵。</p>
     *
     * @param providerName 客户端判定唯一匹配的供应器原始名称
     */
    public static UploadOutcome uploadFromEncodingMenuToNamedProvider(ServerPlayer player, IPatternUploadMenu menu, String providerName) {
        if (player == null || menu == null || providerName == null) {
            return UploadOutcome.FAILED;
        }
        Slot encodedSlot = getEncodedPatternSlot(menu);
        if (encodedSlot == null) {
            return UploadOutcome.FAILED;
        }
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return UploadOutcome.FAILED;
        }

        List<PatternContainer> candidates = new ArrayList<>();
        for (PatternContainer c : listAvailableProvidersFromGrid(menu)) {
            if (providerName.equals(PatternProviderDataUtil.getProviderDisplayName(c))) {
                candidates.add(c);
            }
        }
        candidates.sort(Comparator.<PatternContainer>comparingInt(ProviderUploadUtil::getAvailableSlots).reversed());

        IGrid grid = resolveGrid(menu);
        // 整轮都失败时，用最后一个「装卡失败」的供应器向玩家解释原因
        PatternContainer lastFailedContainer = null;
        ExpansionInsertResult lastFailedResult = null;
        for (PatternContainer c : candidates) {
            // 与手动上传一致：目标供应器已含「除编码作者以外完全相同」的样板时不再重复写入。
            // 自动上传没有屏蔽入口，命中即提示并回收为空白样板，不再回退到矩阵/ECO。
            if (providerContainsPattern(c, stack)) {
                player.sendSystemMessage(Component.translatable(
                        "extendedae_plus.message.provider.duplicate", toDisplayComponent(providerName)));
                recycleEncodedPatternAsBlanks(player, menu, stack);
                return UploadOutcome.DUPLICATE;
            }
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;
            ItemStack toInsert = stack.copy();
            ExpansionInsertResult result = insertWithExpansion(player, grid, c, toInsert, new ExtendedAEPatternFilter());
            int inserted = toInsert.getCount() - result.remain().getCount();
            if (inserted > 0) {
                stack.shrink(inserted);
                encodedSlot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                recordProviderUpload(player, Long.MIN_VALUE, c,
                        findLastChangedSlot(c.getTerminalPatternInventory(),
                                result.before(), result.slotLimit()));
                player.sendSystemMessage(Component.translatable("extendedae_plus.upload_to_provider.success",
                        toDisplayComponent(providerName)));
                return UploadOutcome.SUCCESS;
            }
            if (result.shouldNotifyInstallFailure()) {
                lastFailedContainer = c;
                lastFailedResult = result;
            }
        }
        notifyExpansionInstallFailure(player, lastFailedContainer, lastFailedResult);
        return UploadOutcome.FAILED;
    }

    /** 将原始名称还原为 Component，保留翻译键，由玩家客户端按客户端语言渲染。 */
    private static Component toDisplayComponent(String rawName) {
        try {
            if (rawName.startsWith("{") || rawName.startsWith("\"")) {
                Component component = Component.Serializer.fromJson(rawName);
                if (component != null) {
                    return component;
                }
            }
        } catch (Exception ignored) {
        }
        return Component.literal(rawName);
    }

    /**
     * 缓存 Ctrl+Q 生成的待上传样板（不放入玩家背包）。
     */
    public static String beginPendingCtrlQUpload(ServerPlayer player, ItemStack pattern) {
        if (player == null || pattern == null || pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
            return null;
        }
        clearPendingCtrlQUpload(player);
        String id = UUID.randomUUID().toString();
        player.getPersistentData().putString(PENDING_DATA_KEY, id);
        player.getPersistentData().put(PENDING_STACK_KEY, pattern.copy().save(new CompoundTag()));
        return id;
    }

    public static void clearPendingCtrlQUpload(ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().remove(PENDING_DATA_KEY);
        player.getPersistentData().remove(PENDING_STACK_KEY);
    }

    public static boolean hasPendingCtrlQPattern(ServerPlayer player) {
        if (player == null) return false;
        String id = player.getPersistentData().getString(PENDING_DATA_KEY);
        if (id == null || id.isBlank()) return false;
        return !getPendingCtrlQPattern(player).isEmpty();
    }

    /**
     * 将 pending Ctrl+Q 样板上传到玩家网络中的目标 provider（负数索引 ID）。
     *
     * <p>目标 provider 已含「除编码作者以外完全相同」的样板时返回
     * {@link UploadOutcome#DUPLICATE}，不写入也不做同名回退（样板由调用方退回背包）。</p>
     */
    public static UploadOutcome uploadPendingCtrlQPattern(ServerPlayer player, long providerId) {
        if (player == null) return UploadOutcome.FAILED;
        ItemStack pending = getPendingCtrlQPattern(player);
        if (pending.isEmpty()) return UploadOutcome.FAILED;

        if (pendingTargetContainsPattern(player, providerId, pending)) {
            return UploadOutcome.DUPLICATE;
        }

        UploadResult result = insertPatternIntoProviderFromPlayerNetwork(player, pending, providerId);
        ItemStack remain = result.remaining();
        if (remain.getCount() >= pending.getCount()) {
            return UploadOutcome.FAILED;
        }

        if (remain.isEmpty()) {
            clearPendingCtrlQUpload(player);
        } else {
            player.getPersistentData().put(PENDING_STACK_KEY, remain.save(new CompoundTag()));
        }

        if (result.target() != null) {
            recordProviderUpload(player, providerId, result.target(), result.slot());
        }
        return UploadOutcome.SUCCESS;
    }

    /**
     * 判定 providerId 指定的目标 provider 是否已存在同一 pending 样板（剔除编码作者后完全相同）。
     */
    private static boolean pendingTargetContainsPattern(ServerPlayer player, long providerId, ItemStack pattern) {
        int index = decodeProviderIndex(providerId);
        if (index < 0) return false;
        List<PatternContainer> providers = listAvailableProvidersFromPlayerNetwork(player);
        if (index >= providers.size()) return false;
        PatternContainer target = providers.get(index);
        if (target == null) return false;
        return providerContainsPattern(target, pattern);
    }

    /**
     * 将 pending Ctrl+Q 样板回退到玩家背包；若背包已满则掉落在地上。
     */
    public static boolean returnPendingCtrlQPatternToInventory(ServerPlayer player) {
        if (player == null) return false;
        ItemStack pending = getPendingCtrlQPattern(player);
        if (pending.isEmpty()) return false;

        clearPendingCtrlQUpload(player);
        if (!player.getInventory().add(pending.copy())) {
            player.drop(pending.copy(), false);
        }
        return true;
    }

    /**
     * 列出玩家无线终端网络中的可用 provider，顺序与负数索引上传保持一致。
     */
    public static List<PatternContainer> listAvailableProvidersFromPlayerNetwork(ServerPlayer player) {
        return listAvailableProvidersFromGrid(findPlayerGrid(player));
    }

    private static ItemStack getPendingCtrlQPattern(ServerPlayer player) {
        if (player == null) return ItemStack.EMPTY;
        String id = player.getPersistentData().getString(PENDING_DATA_KEY);
        if (id == null || id.isBlank()) return ItemStack.EMPTY;

        CompoundTag data = player.getPersistentData();
        if (!data.contains(PENDING_STACK_KEY)) return ItemStack.EMPTY;
        CompoundTag stackTag = data.getCompound(PENDING_STACK_KEY);
        ItemStack stack = ItemStack.of(stackTag);
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            clearPendingCtrlQUpload(player);
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private record UploadResult(ItemStack remaining, PatternContainer target, int slot) {}

    private static UploadResult insertPatternIntoProviderFromPlayerNetwork(ServerPlayer player, ItemStack pattern, long providerId) {
        if (player == null || pattern == null || pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
            return new UploadResult(pattern == null ? ItemStack.EMPTY : pattern, null, -1);
        }

        int index = decodeProviderIndex(providerId);
        if (index < 0) return new UploadResult(pattern, null, -1);

        List<PatternContainer> providers = listAvailableProvidersFromPlayerNetwork(player);
        if (index >= providers.size()) return new UploadResult(pattern, null, -1);

        PatternContainer target = providers.get(index);
        if (target == null) return new UploadResult(pattern, null, -1);

        IGrid grid = findPlayerGrid(player);
        ItemStack remain = pattern.copy();
        PatternContainer changedContainer = null;
        int changedSlot = -1;
        // 整轮都失败时，用最后一个「装卡失败」的供应器向玩家解释原因
        PatternContainer lastFailedContainer = null;
        ExpansionInsertResult lastFailedResult = null;
        for (PatternContainer container : buildSameNameTryList(providers, target)) {
            InternalInventory inv = container.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;

            // 槽满时自动从网络取扩容卡装入后重试
            ItemStack toInsert = remain.copy();
            ExpansionInsertResult result = insertWithExpansion(player, grid, container, toInsert, new ExtendedAEPatternFilter());
            if (result.remain().getCount() < remain.getCount()) {
                changedContainer = container;
                remain = result.remain();
                changedSlot = findLastChangedSlot(container.getTerminalPatternInventory(),
                        result.before(), result.slotLimit());
                if (remain.isEmpty()) {
                    return new UploadResult(ItemStack.EMPTY, changedContainer, changedSlot);
                }
            } else if (result.shouldNotifyInstallFailure()) {
                lastFailedContainer = container;
                lastFailedResult = result;
            }
        }
        if (!remain.isEmpty()) {
            notifyExpansionInstallFailure(player, lastFailedContainer, lastFailedResult);
        }
        return new UploadResult(remain, changedContainer, changedSlot);
    }

    private static int decodeProviderIndex(long providerId) {
        if (providerId >= 0) return -1;
        long idx = -1L - providerId;
        if (idx > Integer.MAX_VALUE) return -1;
        return (int) idx;
    }

    private static List<PatternContainer> buildSameNameTryList(List<PatternContainer> all, PatternContainer target) {
        String targetName = PatternProviderDataUtil.getProviderDisplayName(target);
        List<PatternContainer> tryList = new ArrayList<>();
        tryList.add(target);
        for (PatternContainer container : all) {
            if (container == null || container == target) continue;
            String name = PatternProviderDataUtil.getProviderDisplayName(container);
            if (name != null && name.equals(targetName)) {
                tryList.add(container);
            }
        }
        return tryList;
    }

    private static IGrid findPlayerGrid(ServerPlayer player) {
        WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
        return WirelessTerminalLocator.getConnectedGrid(player, located);
    }

    public static PatternContainer findProviderContainer(ServerPlayer player, LastUploadRecord record) {
        if (player == null || record == null) {
            return null;
        }

        if (record.pos() == Long.MIN_VALUE) {
            return findProviderContainerByLegacyId(player, record.providerId());
        }

        IGrid grid = null;
        if (player.containerMenu instanceof PatternEncodingTermMenu encMenu) {
            IGridNode node = encMenu.getNetworkNode();
            if (node != null) {
                grid = node.getGrid();
            }
        }
        if (grid == null) {
            grid = findPlayerGrid(player);
        }
        if (grid == null) {
            return null;
        }

        return findProviderContainerByLocation(grid, record.pos(), record.side(), record.dimension());
    }

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

    public static long getLastUploadedProviderId(ServerPlayer player) {
        if (player == null) return Long.MIN_VALUE;
        CompoundTag data = player.getPersistentData();
        if (data.contains(LAST_UPLOADED_PROVIDER_KEY)) {
            return data.getLong(LAST_UPLOADED_PROVIDER_KEY);
        }
        return Long.MIN_VALUE;
    }

    private static PatternContainer findProviderContainerByLegacyId(ServerPlayer player, long providerId) {
        if (providerId >= 0) {
            PatternAccessTermMenu accessMenu = PatternTerminalUtil.getPatternAccessMenu(player);
            return accessMenu != null ? PatternTerminalUtil.getPatternContainerById(accessMenu, providerId) : null;
        }

        int index = decodeProviderIndex(providerId);
        if (index < 0) {
            return null;
        }

        List<PatternContainer> providers;
        if (player.containerMenu instanceof PatternEncodingTermMenu encMenu) {
            providers = listAvailableProvidersFromGrid(encMenu);
        } else {
            providers = listAvailableProvidersFromPlayerNetwork(player);
        }
        return index < providers.size() ? providers.get(index) : null;
    }

    private static PatternContainer findProviderContainerByLocation(IGrid grid, long pos, String side, String dimension) {
        if (grid == null) {
            return null;
        }

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

    private static boolean dimensionMatches(Level level, String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return true;
        }
        return level != null && dimension.equals(level.dimension().location().toString());
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

    /** 返回当前可写入页中的空槽位数。 */
    public static int getAvailableSlots(PatternContainer container) {
        if (container == null) {
            return -1;
        }

        InternalInventory inventory = container.getTerminalPatternInventory();
        if (inventory == null) {
            return -1;
        }

        int available = 0;
        int slotLimit = getAccessiblePatternSlotCount(container, inventory);
        for (int i = 0; i < slotLimit; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                available++;
            }
        }
        return available;
    }

    private static int getAccessiblePatternSlotCount(PatternContainer container, InternalInventory inventory) {
        return inventory == null ? 0 : inventory.size();
    }

    private static ItemStack insertIntoAccessiblePatternSlots(PatternContainer container, ItemStack stack,
            IAEItemFilter filter) {
        if (container == null) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        return insertIntoAccessiblePatternSlots(
                container.getTerminalPatternInventory(),
                getAccessiblePatternSlotCount(container),
                stack,
                filter);
    }

    private static ItemStack insertIntoAccessiblePatternSlots(InternalInventory inventory, int slotLimit, ItemStack stack,
            IAEItemFilter filter) {
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

    // ------------------------------------------------------------------
    // 扩容卡：容量不足时从网络取卡自动装入
    // ------------------------------------------------------------------

    /** 扩展样板供应器最多可装的扩容卡数量，与 UpgradeSlotCompat 的额外页数一致。 */
    private static final int MAX_EXPANSION_CARDS = UpgradeSlotCompat.getExtendedPatternProviderExtraUpgradeSlots();

    /**
     * 该供应器是否还能再装一张扩容卡。
     *
     * <p>三个条件缺一不可：只有扩展样板供应器注册了这张卡；已装数量未到上限；
     * 升级槽里还有空位。样板槽是否已满不在判断范围内——调用方决定何时使用。</p>
     */
    public static boolean canInstallExpansionCard(PatternContainer container) {
        IUpgradeInventory upgrades = getEffectiveUpgrades(container);
        if (upgrades == null) {
            return false;
        }
        int installed = 0;
        boolean hasEmptySlot = false;
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) {
                hasEmptySlot = true;
            } else if (UpgradeSlotCompat.isExtendedPatternProviderExpansionCard(stack)) {
                installed++;
            }
        }
        return hasEmptySlot && installed < MAX_EXPANSION_CARDS;
    }

    /**
     * 取出该供应器实际生效的升级槽。
     *
     * <p>必须走 {@link CompatUpgradeProvider#eap$getEffectiveUpgrades()}：appflux 存在时
     * 生效的是它注入的槽位，往自建槽里装卡机器读不到。</p>
     */
    private static IUpgradeInventory getEffectiveUpgrades(PatternContainer container) {
        if (!(container instanceof PatternProviderLogicHost host)) {
            return null;
        }
        if (!UpgradeSlotCompat.isExtendedPatternProviderHost(host)) {
            return null;
        }
        if (!((Object) host.getLogic() instanceof CompatUpgradeProvider provider)) {
            return null;
        }
        IUpgradeInventory upgrades = provider.eap$getEffectiveUpgrades();
        if (upgrades == null || upgrades == UpgradeInventories.empty()) {
            return null;
        }
        return upgrades;
    }

    /**
     * 该供应器是否还能再装一张扩容卡（按样板访问终端的供应器 ID 查询）。
     *
     * <p>列表筛选时样板槽已满但可扩容的供应器也应列出，上传时会自动取卡。</p>
     */
    public static boolean canInstallExpansionCard(long providerId, PatternAccessTermMenu menu) {
        return canInstallExpansionCard(PatternTerminalUtil.getPatternContainerById(menu, providerId));
    }

    private static int findEmptyUpgradeSlot(IUpgradeInventory upgrades) {
        for (int i = 0; i < upgrades.size(); i++) {
            if (upgrades.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从 ME 网络抽一张扩容卡装入该供应器的升级槽。
     *
     * <p>消耗按玩家计入（{@link PlayerSource}），与手动从网络取卡等价。任何一步失败都会
     * 把已抽出的卡归还网络，不会吞卡。</p>
     *
     * @return 装卡结果；非 {@link ExpansionInstallResult#SUCCESS} 时携带可提示玩家的失败原因
     */
    public static ExpansionInstallResult tryInstallExpansionCard(ServerPlayer player, IGrid grid, PatternContainer container) {
        if (player == null || grid == null) {
            return ExpansionInstallResult.NO_NETWORK;
        }
        IUpgradeInventory upgrades = getEffectiveUpgrades(container);
        if (upgrades == null) {
            return ExpansionInstallResult.NOT_ELIGIBLE;
        }

        int installed = 0;
        for (int i = 0; i < upgrades.size(); i++) {
            if (UpgradeSlotCompat.isExtendedPatternProviderExpansionCard(upgrades.getStackInSlot(i))) {
                installed++;
            }
        }
        // 正常调用路径上 insertWithExpansion 已用 canInstallExpansionCard 挡过卡满的情况，
        // 这里只是 public 方法的防御：卡满即没有能再容纳扩容卡的槽位。
        if (installed >= MAX_EXPANSION_CARDS) {
            return ExpansionInstallResult.NO_UPGRADE_SLOT;
        }

        AEItemKey cardKey = AEItemKey.of(ModItems.EXTENDED_PATTERN_PROVIDER_EXPANSION_CARD_PLUS.get());
        if (cardKey == null) {
            return ExpansionInstallResult.NOT_ELIGIBLE;
        }
        ItemStack card = cardKey.toStack(1);
        // 先确认槽位可用再抽卡，避免白抽一次又要归还
        int emptySlot = findEmptyUpgradeSlot(upgrades);
        if (emptySlot < 0 || !upgrades.isItemValid(emptySlot, card)) {
            return ExpansionInstallResult.NO_UPGRADE_SLOT;
        }

        IEnergyService energy = grid.getEnergyService();
        MEStorage storage = grid.getStorageService().getInventory();
        PlayerSource source = new PlayerSource(player);
        long extracted = StorageHelper.poweredExtraction(energy, storage, cardKey, 1, source);
        if (extracted <= 0) {
            return ExpansionInstallResult.NO_CARD_IN_NETWORK;
        }

        upgrades.insertItem(emptySlot, card, false);

        // 正常情况下恒为 1，剩余部分属防御性归还。
        if (extracted > 1) {
            StorageHelper.poweredInsert(energy, storage, cardKey, extracted - 1, source);
        }

        // 复用 appflux 原有槽位时，其变更回调不会通知到我们，这里显式走一次统一回调，
        // 保证 updatePatterns() 与存档标记一定发生。
        if (container instanceof PatternProviderLogicHost host
                && (Object) host.getLogic() instanceof PatternProviderLogicUpgradeCompatBridge bridge) {
            bridge.eap$onCompatUpgradesChangedHook();
        }
        return ExpansionInstallResult.SUCCESS;
    }

    /** 可访问的样板槽是否已经全部占用；用于区分「槽满」与「过滤/重复导致插不进」。 */
    private static boolean isAccessibleSlotsFull(InternalInventory inventory, int slotLimit) {
        if (inventory == null) {
            return false;
        }
        int limit = Math.max(0, Math.min(inventory.size(), slotLimit));
        if (limit <= 0) {
            return false;
        }
        for (int i = 0; i < limit; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@link #insertWithExpansion} 的结果。
     *
     * @param remain        未能插入的部分，全部插入时为空
     * @param before        本次写入前的库存快照，用于定位最后改动的槽位
     * @param slotLimit     与快照对应的可写槽位上限
     * @param installResult 扩容卡装配结果，用于向玩家解释「为什么没写进去」
     */
    public record ExpansionInsertResult(ItemStack remain, ItemStack[] before, int slotLimit,
                                        ExpansionInstallResult installResult) {

        /**
         * 是否值得把装卡失败原因告诉玩家。
         *
         * <p>「没走到装卡」是正常流程，「该供应器不支持扩容卡」玩家也无从改变，
         * 两者都静默；其余失败都会让上传表现为「没反应」，必须提示。</p>
         */
        public boolean shouldNotifyInstallFailure() {
            return this.installResult != null
                    && this.installResult != ExpansionInstallResult.SUCCESS
                    && this.installResult != ExpansionInstallResult.NOT_ATTEMPTED
                    && this.installResult != ExpansionInstallResult.NOT_ELIGIBLE;
        }
    }

    /**
     * 把「为什么没写进去」告诉玩家。
     *
     * <p>只在整轮上传最终失败时调用；同一原因在短时间内去重，避免一次操作里
     * 同名供应器与回退候选轮流尝试时刷屏。</p>
     */
    public static void notifyExpansionInstallFailure(ServerPlayer player, PatternContainer container,
                                                     ExpansionInsertResult result) {
        if (player == null || result == null || !result.shouldNotifyInstallFailure()) {
            return;
        }
        ExpansionInstallResult reason = result.installResult();
        CompoundTag data = player.getPersistentData();
        CompoundTag last = data.getCompound(LAST_EXPANSION_NOTIFY_KEY);
        long now = player.level().getGameTime();
        if (reason.name().equals(last.getString("reason"))
                && now - last.getLong("time") <= EXPANSION_NOTIFY_DEDUP_TICKS) {
            return;
        }
        CompoundTag record = new CompoundTag();
        record.putString("reason", reason.name());
        record.putLong("time", now);
        data.put(LAST_EXPANSION_NOTIFY_KEY, record);
        player.sendSystemMessage(Component.translatable(
                expansionFailureMessageKey(reason), getProviderDisplayNameComponent(container)));
    }

    private static String expansionFailureMessageKey(ExpansionInstallResult reason) {
        return switch (reason) {
            case NO_UPGRADE_SLOT -> "extendedae_plus.message.provider.expansion.no_upgrade_slot";
            case NO_NETWORK -> "extendedae_plus.message.provider.expansion.no_network";
            case NO_CARD_IN_NETWORK -> "extendedae_plus.message.provider.expansion.no_card";
            default -> "extendedae_plus.message.provider.expansion.failed";
        };
    }

    /** 供应器显示名组件；名称是服务端下发的原始 Component JSON，按客户端语言渲染。 */
    private static Component getProviderDisplayNameComponent(PatternContainer container) {
        String name = container == null ? null : PatternProviderDataUtil.getProviderDisplayName(container);
        return toDisplayComponent(name == null ? "" : name);
    }

    /**
     * 向供应器写入样板，槽满时先从网络取一张扩容卡再重试。
     *
     * <p>只有在「槽位已满 + 该供应器可扩容」时才动网络；样板因过滤或重复写不进去时
     * 不会白白消耗一张卡。</p>
     */
    public static ExpansionInsertResult insertWithExpansion(ServerPlayer player, IGrid grid,
                                                            PatternContainer container, ItemStack stack, IAEItemFilter filter) {
        if (container == null || stack == null || stack.isEmpty()) {
            return new ExpansionInsertResult(stack == null ? ItemStack.EMPTY : stack, new ItemStack[0], 0,
                    ExpansionInstallResult.NOT_ATTEMPTED);
        }

        InternalInventory inv = container.getTerminalPatternInventory();
        int slotLimit = getAccessiblePatternSlotCount(container, inv);
        ItemStack[] before = snapshotInventory(inv, slotLimit);
        ItemStack remain = insertIntoAccessiblePatternSlots(inv, slotLimit, stack, filter);
        if (remain.getCount() < stack.getCount()
                || !isAccessibleSlotsFull(inv, slotLimit)
                || !canInstallExpansionCard(container)) {
            return new ExpansionInsertResult(remain, before, slotLimit, ExpansionInstallResult.NOT_ATTEMPTED);
        }
        ExpansionInstallResult installResult = tryInstallExpansionCard(player, grid, container);
        if (installResult != ExpansionInstallResult.SUCCESS) {
            return new ExpansionInsertResult(remain, before, slotLimit, installResult);
        }

        // 装卡后槽位上限已变，重新取上限与快照再插一次
        inv = container.getTerminalPatternInventory();
        slotLimit = getAccessiblePatternSlotCount(container, inv);
        before = snapshotInventory(inv, slotLimit);
        remain = insertIntoAccessiblePatternSlots(inv, slotLimit, remain, filter);
        return new ExpansionInsertResult(remain, before, slotLimit, ExpansionInstallResult.SUCCESS);
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
            if (!ItemStack.matches(previous, inv.getStackInSlot(i))) {
                changedSlot = i;
            }
        }
        return changedSlot;
    }

    private static ItemStack[] snapshotInventory(InternalInventory inv) {
        ItemStack[] snapshot = new ItemStack[inv.size()];
        for (int i = 0; i < inv.size(); i++) {
            snapshot[i] = inv.getStackInSlot(i).copy();
        }
        return snapshot;
    }

    private static int findLastChangedSlot(InternalInventory inv, ItemStack[] before) {
        int changedSlot = -1;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack previous = i < before.length ? before[i] : ItemStack.EMPTY;
            ItemStack current = inv.getStackInSlot(i);
            if (!ItemStack.matches(previous, current)) {
                changedSlot = i;
            }
        }
        return changedSlot;
    }

    /**
     * ExtendedAE兼容的样板过滤器
     * 使用AE2的PatternDetailsHelper进行样板验证
     */
    private static class ExtendedAEPatternFilter implements IAEItemFilter {
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
