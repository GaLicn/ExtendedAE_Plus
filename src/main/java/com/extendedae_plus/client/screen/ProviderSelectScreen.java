package com.extendedae_plus.client.screen;

import com.extendedae_plus.network.BatchPendingActionC2SPacket;
import com.extendedae_plus.network.CancelPendingPatternC2SPacket;
import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;
import com.extendedae_plus.client.emi.BoMMappingStatus;
import com.extendedae_plus.client.widget.ResizableAETextField;
import com.extendedae_plus.util.uploadPattern.BatchPatternUploadUtil;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import com.google.gson.*;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单的供应器选择弹窗。
 * 展示若干个可点击的供应器条目，点击后发送带 providerId 的上传请求。
 */
public class ProviderSelectScreen extends Screen {
    private static final int MIN_PAGE_SIZE = 2;
    // AE2 文本框纹理的可视宽度上限，超过后背景中段不会完整平铺。
    private static final int AE_TEXT_FIELD_WIDTH = 128;
    private static final int AE_SEARCH_FIELD_HEIGHT = 20;
    private int pageSize = 6;

    // 置顶的供应器名称集合（静态变量，持久化到配置文件）
    private static final Set<String> pinnedProviders = new HashSet<>();
    private static final String PINNED_CONFIG_PATH = "extendedae_plus/pinned_providers.json";
    private static final String AUTO_UPLOAD_UNIQUE_MATCH_KEY = "auto_upload_unique_match";
    private static final String SHOW_PROCESSING_BUTTONS_KEY = "show_processing_buttons";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static boolean autoUploadUniqueMatchEnabled = true;
    private static boolean showProcessingButtonsEnabled = true;

    // 静态初始化块：加载置顶配置
    static {
        try {
            loadPinnedProviders();
        } catch (Throwable t) {
            // 加载失败时静默处理，不影响界面使用
        }
    }

    private final Screen parent;
    private final ScreenStyle aeStyle;
    // 原始数据
    private final List<Long> ids;
    private final List<Component> names; // 改为 Component
    private final List<Integer> emptySlots;
    // 分组后的数据（同名合并）
    private final List<Long> gIds = new ArrayList<>();           // 代表条目使用的 providerId：选择空位数最多的那个
    private final List<String> gNames = new ArrayList<>();        // 分组名（供应器名称）- 转换为 String 用于显示和匹配
    private final List<Integer> gTotalSlots = new ArrayList<>();  // 该名称下供应器空位总和
    private final List<Integer> gCount = new ArrayList<>();       // 该名称下供应器数量
    // 过滤后的数据（由查询生成）
    private final List<Long> fIds = new ArrayList<>();
    private final List<String> fNames = new ArrayList<>();
    private final List<Integer> fTotalSlots = new ArrayList<>();
    private final List<Integer> fCount = new ArrayList<>();
    private final List<Button> entryButtons = new ArrayList<>();
    // 搜索框
    private ResizableAETextField searchBox;
    // 快捷映射值输入框，映射键使用当前供应器搜索词。
    private ResizableAETextField cnInput;
    private Button processingButtonsToggleButton;
    private Button autoUploadToggleButton;
    private String query = "";
    private boolean needsRefresh = false;
    private int page = 0;
    private boolean autoUploadRequestedFromPresetSearch = false;
    private boolean autoUploadAttempted = false;
    private int lastExactMatchCount = 0;
    /**
     * 非空表示「为配方类型挑选机器」模式：入口是合成树上的感叹号，
     * 此时没有待上传的样板，点选条目改为写入「配方类型 → 机器名」映射。
     */
    private final String mappingKey;
    /** 挑映射机器模式下「配方类型：XXX」的绘制 Y，随 init 的布局一起算。 */
    private int mappingKeyTextY;

    /**
     * 非空表示「批量编码待选」模式：一键编码里映射没有唯一命中的样板由服务端排队，
     * 逐个弹出本界面让玩家指定目标机器，选完/跳过才轮到下一项。
     */
    private final ProvidersListS2CPacket.PendingSelection pending;
    /** 已经把选择发给服务端，onClose 不能再补一条取消，否则会把队列的下一项一起取消掉。 */
    private boolean choiceSent = false;

    public ProviderSelectScreen(Screen parent, List<Long> ids, List<Component> names, List<Integer> emptySlots) {
        this(parent, null, ids, names, emptySlots);
    }

    public ProviderSelectScreen(Screen parent, String mappingKey,
                                List<Long> ids, List<Component> names, List<Integer> emptySlots) {
        this(parent, mappingKey, ids, names, emptySlots, null);
    }

    public ProviderSelectScreen(Screen parent, List<Long> ids, List<Component> names, List<Integer> emptySlots,
                                ProvidersListS2CPacket.PendingSelection pending) {
        this(parent, null, ids, names, emptySlots, pending);
    }

    private ProviderSelectScreen(Screen parent, String mappingKey,
                                 List<Long> ids, List<Component> names, List<Integer> emptySlots,
                                 ProvidersListS2CPacket.PendingSelection pending) {
        super(Component.translatable("extendedae_plus.screen.choose_provider.title"));
        this.parent = parent;
        this.mappingKey = mappingKey == null || mappingKey.isBlank() ? null : mappingKey.trim();
        this.pending = pending;
        this.aeStyle = StyleManager.loadStyleDoc("/screens/common/common.json");
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
        // 如果有来自最近一次写样板流程的预设搜索词，则作为初始查询
        try {
            String recent = ExtendedAEPatternUploadUtil.consumeLastProviderSearchKey();
            if (recent != null && !recent.isBlank()) {
                // 兼容旧流程中已经缓存的原始关键字，打开界面时再次应用映射。
                this.query = ExtendedAEPatternUploadUtil.resolveProviderSearchKey(recent);
                this.autoUploadRequestedFromPresetSearch = true;
            }
        } catch (Throwable ignored) {}
        if (this.mappingKey != null) {
            // 挑映射机器时不该自动上传，也不该沿用上一次编码流程留下的搜索词。
            this.query = "";
            this.autoUploadRequestedFromPresetSearch = false;
        }
        if (this.pending != null) {
            // 搜索词来自这一项自己的映射，与客户端缓存的“上一次”无关；
            // 命中不唯一是服务端已经判定过的结论，这里再自动上传就等于替玩家瞎猜。
            this.query = this.pending.presetQuery();
            this.autoUploadRequestedFromPresetSearch = false;
        }
        this.buildGroups();
        this.applyFilter();
    }

    public boolean isMappingMode() {
        return this.mappingKey != null;
    }

    public boolean isBatchPendingMode() {
        return this.pending != null;
    }

    public Screen getParentScreen() {
        return this.parent;
    }

    public static boolean isAutoUploadUniqueMatchEnabled() {
        return autoUploadUniqueMatchEnabled;
    }

    private static boolean nameMatches(String name, String key) {
        // 匹配语义与服务端定向上传共用，见 ExtendedAEPatternUploadUtil#providerNameMatches
        return BatchPatternUploadUtil.providerNameMatches(name, key);
    }

    private void changePage(int delta) {
        int newPage = this.page + delta;
        if (newPage < 0) return;
        if (newPage * this.pageSize >= this.fIds.size()) return;
        this.page = newPage;
        // 避免在回调中直接重建 UI，改为下帧刷新
        this.needsRefresh = true;
    }

    private Component buildAutoUploadToggleLabel() {
        String stateKey = autoUploadUniqueMatchEnabled
                ? "extendedae_plus.configuration.state_on"
                : "extendedae_plus.configuration.state_off";
        return Component.translatable("extendedae_plus.screen.auto_upload_unique_match",
                Component.translatable(stateKey));
    }

    private Component buildProcessingButtonsToggleLabel() {
        String stateKey = showProcessingButtonsEnabled
                ? "extendedae_plus.configuration.state_on"
                : "extendedae_plus.configuration.state_off";
        return Component.translatable("extendedae_plus.screen.show_processing_buttons",
                Component.translatable(stateKey));
    }

    private Tooltip buildProcessingButtonsTooltip() {
        String stateKey = showProcessingButtonsEnabled
                ? "extendedae_plus.configuration.state_on"
                : "extendedae_plus.configuration.state_off";
        return Tooltip.create(Component.translatable("extendedae_plus.screen.show_processing_buttons.tooltip",
                Component.translatable(stateKey)));
    }

    private Tooltip buildAutoUploadTooltip() {
        String stateKey = autoUploadUniqueMatchEnabled
                ? "extendedae_plus.configuration.state_on"
                : "extendedae_plus.configuration.state_off";
        return Tooltip.create(Component.translatable("extendedae_plus.screen.auto_upload_unique_match.tooltip",
                Component.translatable(stateKey)));
    }

    public static boolean isProcessingButtonsEnabled() {
        return showProcessingButtonsEnabled;
    }

    private void toggleProcessingButtons() {
        showProcessingButtonsEnabled = !showProcessingButtonsEnabled;
        savePinnedProviders();
        if (this.processingButtonsToggleButton != null) {
            this.processingButtonsToggleButton.setMessage(this.buildProcessingButtonsToggleLabel());
            this.processingButtonsToggleButton.setTooltip(this.buildProcessingButtonsTooltip());
        }
    }

    private void toggleAutoUploadUniqueMatch() {
        autoUploadUniqueMatchEnabled = !autoUploadUniqueMatchEnabled;
        savePinnedProviders();
        if (this.autoUploadToggleButton != null) {
            this.autoUploadToggleButton.setMessage(this.buildAutoUploadToggleLabel());
            this.autoUploadToggleButton.setTooltip(this.buildAutoUploadTooltip());
        }
    }

    private String buildLabel(int idx) {
        String name = this.fNames.get(idx);
        int totalSlots = this.fTotalSlots.get(idx);
        int count = this.fCount.get(idx);

        // 如果是置顶条目，在最左侧添加星星标志
        String prefix = pinnedProviders.contains(name) ? "★ " : "";

        // 不显示具体 id，显示合并统计：名称（总空位）x数量
        return prefix + name + "  (" + totalSlots + ")  x" + count;
    }

    private void onChoose(int idx) {
        this.onChoose(idx, false);
    }

    private void onChoose(int idx, boolean showStatusMessage) {
        if (idx < 0 || idx >= this.fIds.size()) return;
        String providerName = this.fNames.get(idx);
        if (this.mappingKey != null) {
            // 挑映射机器模式：点选即建立「配方类型 → 机器名」映射，没有样板要上传。
            this.saveMappingTo(providerName);
            return;
        }
        long providerId = this.fIds.get(idx);
        this.choiceSent = true;
        PacketDistributor.sendToServer(new UploadEncodedPatternToProviderC2SPacket(providerId, showStatusMessage, providerName));
        this.onClose();
    }

    /** 把当前配方类型映射到给定的机器名/搜索词，成功后返回上一层界面。 */
    private void saveMappingTo(String value) {
        var player = Minecraft.getInstance().player;
        if (value == null || value.isBlank()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.cn_required"));
            }
            return;
        }
        if (!ExtendedAEPatternUploadUtil.addOrUpdateRecipeTypeMapping(this.mappingKey, value)) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.add_fail"));
            }
            return;
        }
        // 映射表版本已递增，合成树上的感叹号会随之消失。
        BoMMappingStatus.invalidate();
        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    "extendedae_plus.message.mapping.add_success", this.mappingKey, value));
        }
        this.onClose();
    }

    private void buildGroups() {
        // 使用 LinkedHashMap 保持首次出现顺序
        Map<String, Group> map = new LinkedHashMap<>();
        for (int i = 0; i < this.names.size(); i++) {
            String name = this.names.get(i).getString(); // 将 Component 转换为 String
            long id = this.ids.get(i);
            int slots = this.emptySlots.get(i);
            Group g = map.computeIfAbsent(name, k -> new Group());
            g.count++;
            g.totalSlots += Math.max(0, slots);
            // 挑选空位最多的作为代表 id；若并列，保留先到者
            if (slots > g.bestSlots) {
                g.bestSlots = slots;
                g.bestId = id;
            }
        }
        for (Map.Entry<String, Group> e : map.entrySet()) {
            String name = e.getKey();
            Group g = e.getValue();
            this.gNames.add(name);
            this.gIds.add(g.bestId);
            this.gTotalSlots.add(g.totalSlots);
            this.gCount.add(g.count);
        }
    }

    private void applyFilter() {
        this.fIds.clear();
        this.fNames.clear();
        this.fTotalSlots.clear();
        this.fCount.clear();
        this.lastExactMatchCount = 0;
        String q = this.query == null ? "" : this.query.trim();
        // 输入内容命中配方类型映射时，供应器名称按映射值进行过滤。
        String searchKey = ExtendedAEPatternUploadUtil.resolveProviderSearchKey(q);
        for (int i = 0; i < this.gIds.size(); i++) {
            String name = this.gNames.get(i);
            if (searchKey.isEmpty() || nameMatches(name, searchKey)) {
                this.fIds.add(this.gIds.get(i));
                this.fNames.add(name);
                this.fTotalSlots.add(this.gTotalSlots.get(i));
                this.fCount.add(this.gCount.get(i));
                this.lastExactMatchCount++;
            }
        }

        // 对 fNames 进行排序，置顶的条目排在前面，然后按自然排序
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < this.fNames.size(); i++) indices.add(i);
        indices.sort((i1, i2) -> {
            String name1 = this.fNames.get(i1);
            String name2 = this.fNames.get(i2);
            boolean pinned1 = pinnedProviders.contains(name1);
            boolean pinned2 = pinnedProviders.contains(name2);

            // 置顶的排在前面
            if (pinned1 && !pinned2) return -1;
            if (!pinned1 && pinned2) return 1;

            // 都置顶或都不置顶，按自然排序
            return compareNatural(name1, name2);
        });

        List<Long> sortedIds = new ArrayList<>();
        List<String> sortedNames = new ArrayList<>();
        List<Integer> sortedSlots = new ArrayList<>();
        List<Integer> sortedCount = new ArrayList<>();

        for (int idx : indices) {
            sortedIds.add(this.fIds.get(idx));
            sortedNames.add(this.fNames.get(idx));
            sortedSlots.add(this.fTotalSlots.get(idx));
            sortedCount.add(this.fCount.get(idx));
        }

        this.fIds.clear();
        this.fIds.addAll(sortedIds);
        this.fNames.clear();
        this.fNames.addAll(sortedNames);
        this.fTotalSlots.clear();
        this.fTotalSlots.addAll(sortedSlots);
        this.fCount.clear();
        this.fCount.addAll(sortedCount);
    }

    private void tryAutoUploadIfUniqueMatch() {
        if (!autoUploadUniqueMatchEnabled || !this.autoUploadRequestedFromPresetSearch || this.autoUploadAttempted) {
            return;
        }
        this.autoUploadAttempted = true;
        if (this.query == null || this.query.isBlank() || this.lastExactMatchCount != 1 || this.fIds.size() != 1) {
            return;
        }
        this.onChoose(0, true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.choiceSent) {
            // 已经选好机器：pending 样板由上传封包接手，再发取消会误伤队列的下一项。
            Minecraft.getInstance().setScreen(this.parent);
            return;
        }
        if (this.pending != null) {
            // 批量待选模式下关窗视为放弃整条队列：剩余样板一份都不生成，
            // 服务端会把编码时扣掉的空白样板还回去。只想放过当前一项的话有单独的「跳过」按钮。
            PacketDistributor.sendToServer(new BatchPendingActionC2SPacket(BatchPendingActionC2SPacket.Action.ABORT));
        } else if (this.mappingKey == null) {
            // 挑映射机器模式下没有 pending 样板，不能误发取消请求。
            PacketDistributor.sendToServer(CancelPendingPatternC2SPacket.INSTANCE);
        }
        Minecraft.getInstance().setScreen(this.parent);
    }

    /** 批量待选模式：放过当前这一项（不生成样板，空白样板还回），继续处理队列里的下一项。 */
    private void skipPendingItem() {
        if (this.pending == null) {
            return;
        }
        this.choiceSent = true;
        PacketDistributor.sendToServer(new BatchPendingActionC2SPacket(BatchPendingActionC2SPacket.Action.SKIP));
        this.onClose();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.entryButtons.clear();

        int centerX = this.width / 2;
        
        // 动态计算页面大小，确保所有元素都能显示；底部控件压缩为两排。
        int buttonHeight = 20;
        int gap = 5;
        int entryUnitHeight = buttonHeight + gap;
        int reservedHeight = 30 + 30 + 30 + 20 + 40;
        int availableHeight = this.height - reservedHeight;
        this.pageSize = Math.max(MIN_PAGE_SIZE, availableHeight / entryUnitHeight);
        
        // 动态计算起始高度，使内容垂直居中
        int totalEntriesHeight = this.pageSize * entryUnitHeight;
        int contentHeight = 30 + totalEntriesHeight + 30 + 30 + 20;
        int startY = (this.height - contentHeight) / 2 + 30;

        // 搜索框（置于条目上方）
        int searchX = centerX - AE_TEXT_FIELD_WIDTH / 2;
        this.searchBox = new ResizableAETextField(this.aeStyle, this.font, searchX, startY - 25,
                AE_TEXT_FIELD_WIDTH, AE_SEARCH_FIELD_HEIGHT);
        // 配方类型提示紧贴搜索框上方，跟着布局走，不另算一套居中。
        this.mappingKeyTextY = startY - 25 - 11;
        // AE2 终端关闭 EditBox 默认填充，只绘制 AE2 文本框纹理。
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(256);
        this.searchBox.setPlaceholder(Component.translatable("extendedae_plus.screen.search"));
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(text -> {
            // 只有当输入真正发生变化时，才重置页码与过滤
            if (Objects.equals(text, this.query)) return;
            this.query = text;
            this.page = 0;
            this.applyFilter();
            // 避免在回调中直接重建 UI，延迟到下一次 tick
            this.needsRefresh = true;
        });
        this.addRenderableWidget(this.searchBox);

        int start = this.page * this.pageSize;
        int end = Math.min(start + this.pageSize, this.fIds.size());

        int buttonWidth = 240;

        for (int i = start; i < end; i++) {
            int idx = i;
            String label = this.buildLabel(idx);
            Button btn = new AE2Button(
                    centerX - buttonWidth / 2, startY + (i - start) * (buttonHeight + gap), buttonWidth, buttonHeight,
                    Component.literal(label), b -> this.onChoose(idx));
            this.entryButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        // 分页按钮
        int navY = startY + this.pageSize * (buttonHeight + gap) + 10;
        Button prev = new AE2Button(centerX - 60, navY, 20, 20, Component.literal("<"), b -> this.changePage(-1));
        Button next = new AE2Button(centerX + 40, navY, 20, 20, Component.literal(">"), b -> this.changePage(1));
        prev.active = this.page > 0;
        next.active = (this.page + 1) * this.pageSize < this.fIds.size();
        this.addRenderableWidget(prev);
        this.addRenderableWidget(next);

        if (this.pending != null) {
            // 跳过放在翻页行右侧：底部那排已经排满，且「跳过当前项」与翻页一样是逐项操作。
            Button skip = new AE2Button(centerX + 70, navY, 70, 20,
                    Component.translatable("extendedae_plus.screen.pending_skip"), b -> this.skipPendingItem());
            skip.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.screen.pending_skip.tooltip")));
            this.addRenderableWidget(skip);
        }

        // 底部第二排依次放置中文名、增加映射、映射管理和取消按钮。
        int controlsWidth = Math.min(480, Math.max(240, this.width - 20));
        int controlsX = centerX - controlsWidth / 2;
        int toggleGap = 5;
        int toggleWidth = (controlsWidth - toggleGap) / 2;
        int toggleY = navY + 30;

        this.processingButtonsToggleButton = new AE2Button(
                controlsX, toggleY, toggleWidth, 20,
                this.buildProcessingButtonsToggleLabel(), b -> this.toggleProcessingButtons());
        this.processingButtonsToggleButton.setTooltip(this.buildProcessingButtonsTooltip());
        this.addRenderableWidget(this.processingButtonsToggleButton);

        this.autoUploadToggleButton = new AE2Button(
                controlsX + toggleWidth + toggleGap, toggleY, toggleWidth, 20,
                this.buildAutoUploadToggleLabel(), b -> this.toggleAutoUploadUniqueMatch());
        this.autoUploadToggleButton.setTooltip(this.buildAutoUploadTooltip());
        this.addRenderableWidget(this.autoUploadToggleButton);

        int quickMappingY = navY + 55;
        int quickInputWidth = AE_TEXT_FIELD_WIDTH;
        String cnValue = this.cnInput == null ? "" : this.cnInput.getValue();
        this.cnInput = new ResizableAETextField(this.aeStyle, this.font, controlsX, quickMappingY,
                quickInputWidth, AE_SEARCH_FIELD_HEIGHT);
        this.cnInput.setBordered(false);
        this.cnInput.setMaxLength(256);
        this.cnInput.setPlaceholder(Component.translatable("extendedae_plus.screen.cn_name"));
        this.cnInput.setValue(cnValue);
        this.addRenderableWidget(this.cnInput);
        this.addRenderableWidget(new AE2Button(
                controlsX + quickInputWidth + 5, quickMappingY, 85, 20,
                Component.translatable("extendedae_plus.screen.add_mapping"), b -> this.addMappingFromUI()));

        // 完整编辑和删除操作集中到独立的可视化管理页面。
        Button mappingManagement = new AE2Button(
                controlsX + quickInputWidth + 5 + 85 + 5, quickMappingY, 155, 20,
                Component.translatable("extendedae_plus.screen.mapping_management.button"),
                b -> Minecraft.getInstance().setScreen(this.mappingKey == null
                        ? new RecipeTypeMappingScreen(this)
                        : new RecipeTypeMappingScreen(this, this.mappingKey)));
        this.addRenderableWidget(mappingManagement);

        Button close = new AE2Button(
                controlsX + quickInputWidth + 5 + 85 + 5 + 155 + 5, quickMappingY, 80, 20,
                Component.translatable("gui.cancel"), b -> this.onClose());
        this.addRenderableWidget(close);

        this.tryAutoUploadIfUniqueMatch();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.needsRefresh) {
            this.needsRefresh = false;
            // 重新构建当前屏幕内容
            this.init();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.mappingKey != null) {
            // 挑映射机器时必须看得见正在给哪个配方类型建映射，否则点错机器无从察觉。
            graphics.drawCenteredString(this.font,
                    Component.translatable("extendedae_plus.screen.choose_provider.mapping_key", this.mappingKey),
                    this.width / 2, Math.max(4, this.mappingKeyTextY), 0xFFFFAA00);
            return;
        }
        if (this.pending != null) {
            this.renderPendingHeader(graphics);
        }
    }

    /**
     * 批量待选模式的抬头：进度 + 产物图标与名字 + 映射为什么没定下来。
     * 一次要处理好几项，看不见正在给哪个配方选机器的话玩家只能瞎点。
     */
    private void renderPendingHeader(GuiGraphics graphics) {
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font,
                Component.translatable("extendedae_plus.screen.pending_progress",
                        this.pending.index(), this.pending.total()),
                centerX, Math.max(4, this.mappingKeyTextY - 11), 0xFFFFAA00);

        ItemStack output = this.pending.output();
        String key = this.pending.searchKey() == null ? "" : this.pending.searchKey();
        Component reason = key.isBlank()
                ? Component.translatable("extendedae_plus.screen.pending_no_mapping")
                : this.pending.candidates() > 0
                        ? Component.translatable("extendedae_plus.screen.pending_ambiguous",
                                key, this.pending.candidates())
                        : Component.translatable("extendedae_plus.screen.pending_unmatched", key);
        Component line = Component.empty()
                .append(output.isEmpty()
                        ? Component.translatable("extendedae_plus.screen.pending_unknown_output")
                        : output.getHoverName())
                .append(Component.literal("  "))
                .append(reason);

        int textY = Math.max(15, this.mappingKeyTextY);
        int textX = centerX - this.font.width(line) / 2;
        if (!output.isEmpty()) {
            // 图标贴在文字左侧，位置随文字宽度走，长名字也不会压到搜索框。
            graphics.renderFakeItem(output, textX - 20, textY - 4);
        }
        graphics.drawString(this.font, line, textX, textY, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右键点击搜索框区域时，清空搜索框内容并刷新
        if (button == 1 && this.searchBox != null) {
            // AETextField 的 getX/getWidth 是内部 EditBox 边界，右键清空需使用完整可视区域。
            var bounds = this.searchBox.getTooltipArea();
            if (mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth()
                    && mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight()) {
                if (!this.searchBox.getValue().isEmpty()) {
                    this.searchBox.setValue("");
                }
                this.query = "";
                this.page = 0;
                this.applyFilter();
                this.needsRefresh = true;
                return true;
            }
        }

        // 右键点击条目按钮时，切换置顶状态
        if (button == 1) {
            for (int i = 0; i < this.entryButtons.size(); i++) {
                Button btn = this.entryButtons.get(i);
                if (btn.visible && btn.active) {
                    int x = btn.getX();
                    int y = btn.getY();
                    int w = btn.getWidth();
                    int h = btn.getHeight();
                    if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                        // 计算实际索引
                        int start = this.page * this.pageSize;
                        int actualIdx = start + i;
                        if (actualIdx >= 0 && actualIdx < this.fNames.size()) {
                            togglePin(actualIdx);
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void addMappingFromUI() {
        // 挑映射机器模式下键已由合成树给定，普通模式沿用「搜索词即映射键」的旧行为。
        String key = this.mappingKey != null
                ? this.mappingKey
                : (this.query == null ? "" : this.query.trim());
        String value = this.cnInput == null ? "" : this.cnInput.getValue().trim();
        var player = Minecraft.getInstance().player;
        if (key.isEmpty()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.search_required"));
            }
            return;
        }
        if (value.isEmpty()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.cn_required"));
            }
            return;
        }

        if (this.mappingKey != null) {
            this.saveMappingTo(value);
            return;
        }

        if (!ExtendedAEPatternUploadUtil.addOrUpdateRecipeTypeMapping(key, value)) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.add_fail"));
            }
            return;
        }

        if (player != null) {
            player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.add_success", key, value));
        }
        this.query = value;
        this.searchBox.setValue(value);
        this.applyFilter();
        this.page = 0;
        this.needsRefresh = true;
    }

    // 切换供应器的置顶状态
    private void togglePin(int idx) {
        if (idx < 0 || idx >= this.fNames.size()) return;
        String name = this.fNames.get(idx);

        if (pinnedProviders.contains(name)) {
            pinnedProviders.remove(name);
        } else {
            pinnedProviders.add(name);
        }

        // 保存到配置文件
        savePinnedProviders();

        // 重新应用过滤和排序
        this.applyFilter();
        this.needsRefresh = true;
    }

    /**
     * 从配置文件加载置顶的供应器名称列表
     */
    private static synchronized void loadPinnedProviders() {
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(PINNED_CONFIG_PATH);
            if (!Files.exists(cfgPath)) {
                return; // 文件不存在时不做处理
            }

            String json = Files.readString(cfgPath);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return;

            JsonElement pinnedElement = obj.get("pinned");
            if (pinnedElement != null && pinnedElement.isJsonArray()) {
                JsonArray arr = pinnedElement.getAsJsonArray();
                pinnedProviders.clear();
                for (JsonElement elem : arr) {
                    if (elem.isJsonPrimitive()) {
                        String name = elem.getAsString();
                        if (name != null && !name.isBlank()) {
                            pinnedProviders.add(name);
                        }
                    }
                }
            }

            JsonElement autoUploadElement = obj.get(AUTO_UPLOAD_UNIQUE_MATCH_KEY);
            if (autoUploadElement != null && autoUploadElement.isJsonPrimitive()) {
                autoUploadUniqueMatchEnabled = autoUploadElement.getAsBoolean();
            }

            JsonElement showProcessingButtonsElement = obj.get(SHOW_PROCESSING_BUTTONS_KEY);
            if (showProcessingButtonsElement != null && showProcessingButtonsElement.isJsonPrimitive()) {
                showProcessingButtonsEnabled = showProcessingButtonsElement.getAsBoolean();
            }
        } catch (IOException | JsonSyntaxException e) {
            // 加载失败时静默处理
        }
    }

    /**
     * 保存置顶的供应器名称列表到配置文件
     */
    private static synchronized void savePinnedProviders() {
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(PINNED_CONFIG_PATH);
            Files.createDirectories(cfgPath.getParent());

            JsonObject obj = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String name : pinnedProviders) {
                arr.add(name);
            }
            obj.add("pinned", arr);
            obj.addProperty(AUTO_UPLOAD_UNIQUE_MATCH_KEY, autoUploadUniqueMatchEnabled);
            obj.addProperty(SHOW_PROCESSING_BUTTONS_KEY, showProcessingButtonsEnabled);

            Files.writeString(cfgPath, GSON.toJson(obj));
        } catch (IOException e) {
            // 保存失败时静默处理
        }
    }

    // 自然排序比较方法
    private static final Pattern NATURAL_PATTERN = Pattern.compile("(\\D*)(\\d*)");
    private static int compareNatural(String s1, String s2) {
        Matcher m1 = NATURAL_PATTERN.matcher(s1);
        Matcher m2 = NATURAL_PATTERN.matcher(s2);

        while (m1.find() && m2.find()) {
            // 比较非数字部分
            int cmp = m1.group(1).compareTo(m2.group(1));
            if (cmp != 0) return cmp;

            // 比较数字部分
            String num1 = m1.group(2);
            String num2 = m2.group(2);
            if (!num1.isEmpty() || !num2.isEmpty()) {
                int n1 = num1.isEmpty() ? 0 : Integer.parseInt(num1);
                int n2 = num2.isEmpty() ? 0 : Integer.parseInt(num2);
                if (n1 != n2) return Integer.compare(n1, n2);
            }
        }
        return s1.length() - s2.length();
    }

    private static class Group {
        long bestId = Long.MIN_VALUE;
        int bestSlots = Integer.MIN_VALUE;
        int totalSlots = 0;
        int count = 0;
    }
}
