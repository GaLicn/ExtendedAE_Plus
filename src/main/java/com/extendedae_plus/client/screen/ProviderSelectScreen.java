package com.extendedae_plus.client.screen;

import com.extendedae_plus.network.CancelPendingPatternC2SPacket;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
    private int pageSize = 6;
    // 优先使用 JEC 的拼音匹配，否则回退到大小写不敏感子串匹配
    private static Boolean JEC_AVAILABLE = null;
    private static java.lang.reflect.Method JEC_CONTAINS = null;

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
    private EditBox searchBox;
    // 快捷映射值输入框，映射键使用当前供应器搜索词。
    private EditBox cnInput;
    private Button processingButtonsToggleButton;
    private Button autoUploadToggleButton;
    private String query = "";
    private boolean needsRefresh = false;
    private int page = 0;
    private boolean autoUploadRequestedFromPresetSearch = false;
    private boolean autoUploadAttempted = false;
    private int lastExactMatchCount = 0;

    public ProviderSelectScreen(Screen parent, List<Long> ids, List<Component> names, List<Integer> emptySlots) {
        super(Component.translatable("extendedae_plus.screen.choose_provider.title"));
        this.parent = parent;
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
        this.buildGroups();
        this.applyFilter();
    }

    private static boolean nameMatches(String name, String key) {
        if (name == null) return false;
        if (key == null || key.isEmpty()) return true;
        try {
            if (JEC_AVAILABLE == null) {
                try {
                    Class<?> cls = Class.forName("me.towdium.jecharacters.utils.Match");
                    // 使用 contains(CharSequence, CharSequence)
                    JEC_CONTAINS = cls.getMethod("contains", CharSequence.class, CharSequence.class);
                    JEC_AVAILABLE = true;
                } catch (Throwable t) {
                    JEC_AVAILABLE = false;
                }
            }
            if (Boolean.TRUE.equals(JEC_AVAILABLE) && JEC_CONTAINS != null) {
                Object r = JEC_CONTAINS.invoke(null, name, key);
                if (r instanceof Boolean && (Boolean) r) return true;
                // 再尝试大小写不敏感：双方转为小写重新匹配
                String nL = name.toLowerCase();
                String kL = key.toLowerCase();
                Object r2 = JEC_CONTAINS.invoke(null, nL, kL);
                if (r2 instanceof Boolean && (Boolean) r2) return true;
            }
        } catch (Throwable ignored) {
            // 回退
        }
        // 默认大小写不敏感子串
        return name.toLowerCase().contains(key.toLowerCase());
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
        long providerId = this.fIds.get(idx);
        String providerName = this.fNames.get(idx);
        PacketDistributor.sendToServer(new UploadEncodedPatternToProviderC2SPacket(providerId, showStatusMessage, providerName));
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
        PacketDistributor.sendToServer(CancelPendingPatternC2SPacket.INSTANCE);
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.entryButtons.clear();

        int centerX = this.width / 2;
        
        // 动态计算页面大小，确保所有元素都能显示
        // 布局结构：搜索框(30) + 条目按钮 + 分页按钮(30) + 切换按钮(30) + 其他按钮(20) + 边距(40)
        int buttonHeight = 20;
        int gap = 5;
        int entryUnitHeight = buttonHeight + gap;
        int reservedHeight = 30 + 30 + 30 + 20 + 65;
        int availableHeight = this.height - reservedHeight;
        this.pageSize = Math.max(MIN_PAGE_SIZE, availableHeight / entryUnitHeight);
        
        // 动态计算起始高度，使内容垂直居中
        int totalEntriesHeight = this.pageSize * entryUnitHeight;
        int contentHeight = 30 + totalEntriesHeight + 30 + 30 + 45;
        int startY = (this.height - contentHeight) / 2 + 30;

        // 搜索框（置于条目上方）
        if (this.searchBox == null) {
            this.searchBox = new EditBox(this.font, centerX - 120, startY - 25, 240, 18, Component.translatable("extendedae_plus.screen.search"));
        } else {
            // 重新定位，保持输入值
            this.searchBox.setX(centerX - 120);
            this.searchBox.setY(startY - 25);
            this.searchBox.setWidth(240);
        }
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
            Button btn = Button.builder(Component.literal(label), b -> this.onChoose(idx))
                    .bounds(centerX - buttonWidth / 2, startY + (i - start) * (buttonHeight + gap), buttonWidth, buttonHeight)
                    .build();
            this.entryButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        // 分页按钮
        int navY = startY + this.pageSize * (buttonHeight + gap) + 10;
        Button prev = Button.builder(Component.literal("<"), b -> this.changePage(-1))
                .bounds(centerX - 60, navY, 20, 20)
                .build();
        Button next = Button.builder(Component.literal(">"), b -> this.changePage(1))
                .bounds(centerX + 40, navY, 20, 20)
                .build();
        prev.active = this.page > 0;
        next.active = (this.page + 1) * this.pageSize < this.fIds.size();
        this.addRenderableWidget(prev);
        this.addRenderableWidget(next);

        int controlsWidth = 240;
        int controlsX = centerX - controlsWidth / 2;
        int toggleGap = 5;
        int toggleWidth = (controlsWidth - toggleGap) / 2;
        int toggleY = navY + 30;

        this.processingButtonsToggleButton = Button.builder(this.buildProcessingButtonsToggleLabel(), b -> this.toggleProcessingButtons())
                .bounds(controlsX, toggleY, toggleWidth, 20)
                .build();
        this.processingButtonsToggleButton.setTooltip(this.buildProcessingButtonsTooltip());
        this.addRenderableWidget(this.processingButtonsToggleButton);

        this.autoUploadToggleButton = Button.builder(this.buildAutoUploadToggleLabel(), b -> this.toggleAutoUploadUniqueMatch())
                .bounds(controlsX + toggleWidth + toggleGap, toggleY, toggleWidth, 20)
                .build();
        this.autoUploadToggleButton.setTooltip(this.buildAutoUploadTooltip());
        this.addRenderableWidget(this.autoUploadToggleButton);

        int quickMappingY = navY + 55;
        int quickInputWidth = 150;
        if (this.cnInput == null) {
            this.cnInput = new EditBox(this.font, controlsX, quickMappingY, quickInputWidth, 20,
                    Component.translatable("extendedae_plus.screen.cn_name"));
        } else {
            this.cnInput.setX(controlsX);
            this.cnInput.setY(quickMappingY);
            this.cnInput.setWidth(quickInputWidth);
        }
        this.addRenderableWidget(this.cnInput);
        this.addRenderableWidget(Button.builder(Component.translatable("extendedae_plus.screen.add_mapping"),
                        b -> this.addMappingFromUI())
                .bounds(controlsX + quickInputWidth + 5, quickMappingY, 85, 20)
                .build());

        // 完整编辑和删除操作集中到独立的可视化管理页面。
        Button mappingManagement = Button.builder(Component.translatable("extendedae_plus.screen.mapping_management.button"),
                        b -> Minecraft.getInstance().setScreen(new RecipeTypeMappingScreen(this)))
                .bounds(controlsX, navY + 80, 155, 20)
                .build();
        this.addRenderableWidget(mappingManagement);

        Button close = Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(controlsX + 160, navY + 80, 80, 20)
                .build();
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右键点击搜索框区域时，清空搜索框内容并刷新
        if (button == 1 && this.searchBox != null) {
            int x = this.searchBox.getX();
            int y = this.searchBox.getY();
            int w = this.searchBox.getWidth();
            int h = this.searchBox.getHeight();
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
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
        String key = this.query == null ? "" : this.query.trim();
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
