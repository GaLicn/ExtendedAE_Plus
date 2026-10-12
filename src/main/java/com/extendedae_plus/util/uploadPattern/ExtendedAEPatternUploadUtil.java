package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogic;
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
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

/**
 * ExtendedAE扩展样板管理终端专用的样板上传工具类
 * 兼容ExtendedAE的ContainerExPatternTerminal和原版AE2的PatternAccessTermMenu
 */
public class ExtendedAEPatternUploadUtil {

    // --------------------------- 配置：RecipeType 中文名称映射 ---------------------------
    private static final String CONFIG_RELATIVE = "extendedae_plus/recipe_type_names.json";
    private static final Map<ResourceLocation, String> CUSTOM_NAMES = new ConcurrentHashMap<>();
    private static final Map<String, String> CUSTOM_ALIASES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    // 最近一次打开供应器选择界面时的预设搜索关键字。
    // 处理样板会写入映射后的配方类型关键字，合成样板固定使用 crafting（可通过映射改名）。
    public static final String DEFAULT_CRAFTING_SEARCH_KEY = "crafting";
    private static volatile String lastProviderSearchKey = null;
    // 供应器名称匹配：优先使用 JEC 的拼音匹配，否则回退到大小写不敏感子串匹配。
    private static Boolean JEC_AVAILABLE = null;
    private static Method JEC_CONTAINS = null;
    private static final String LAST_UPLOADED_PROVIDER_KEY = "eap_last_uploaded_provider_id";
    private static final String LAST_UPLOAD_KIND_KEY = "eap_last_upload_kind";
    private static final String LAST_UPLOAD_SLOT_KEY = "eap_last_upload_slot";
    private static final String LAST_UPLOAD_POS_KEY = "eap_last_upload_pos";
    private static final String LAST_UPLOAD_SIDE_KEY = "eap_last_upload_side";
    private static final String LAST_UPLOAD_DIM_KEY = "eap_last_upload_dim";
    private static final String LAST_UPLOAD_MATRIX_PLUS_KEY = "eap_last_upload_matrix_plus";
    private static final String LAST_UPLOAD_KIND_PROVIDER = "provider";
    private static final String LAST_UPLOAD_KIND_MATRIX = "matrix";

    public record LastUploadRecord(String kind, long providerId, int slot, long pos, String side, String dimension, boolean matrixPlus) {
        public boolean isMatrix() {
            return LAST_UPLOAD_KIND_MATRIX.equals(this.kind);
        }
    }

    private record HostLocator(long pos, String side, String dimension) {}

    private record MatrixInventoryTarget(InternalInventory insertInventory,
                                         InternalInventory patternInventory,
                                         BlockPos pos,
                                         boolean plus) {}

    public record RecipeTypeMapping(String key, String value) {}

    static {
        try {
            loadRecipeTypeNames();
        } catch (Throwable t) {
            // 配置加载失败时保持空映射，不影响配方记录流程。
        }
    }

    /**
     * 从配置文件加载 RecipeType → 中文名称映射。文件不存在则生成模板。
     * 同时支持“别名”形式：不含冒号的键会被视为最终搜索关键字（大小写不敏感），如：
     * {
     *   "assembler": "组装机"
     * }
     */
    public static synchronized void loadRecipeTypeNames() {
        try {
            Path cfgDir = FMLPaths.CONFIGDIR.get();
            Path cfgPath = cfgDir.resolve(CONFIG_RELATIVE);
            if (!Files.exists(cfgPath)) {
                // 默认不预置映射，分类标题由配方查看器统一提供。
                Files.createDirectories(cfgPath.getParent());
                Files.writeString(cfgPath, GSON.toJson(new JsonObject()));
            }

            String json = Files.readString(cfgPath);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            Map<ResourceLocation, String> map = new HashMap<>();
            Map<String, String> alias = new HashMap<>();
            if (obj != null) {
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    String k = e.getKey();
                    JsonElement v = e.getValue();
                    if (v != null && v.isJsonPrimitive()) {
                        String name = v.getAsString();
                        if (name == null || name.isBlank()) continue;
                        if (k.contains(":")) {
                            // 形如 namespace:path
                            try {
                                var rl = ResourceLocation.tryParse(k);
                                if (rl != null) {
                                    map.put(rl, name);
                                }
                            } catch (Exception ignored) {}
                        } else {
                            // 视为别名：最终搜索关键字（大小写不敏感）
                            alias.put(k.toLowerCase(), name);
                        }
                    }
                }
            }
            CUSTOM_NAMES.clear();
            CUSTOM_NAMES.putAll(map);
            CUSTOM_ALIASES.clear();
            CUSTOM_ALIASES.putAll(alias);
        } catch (IOException ignored) {
        }
    }

    public static void setLastProcessingName(String name) {
        setLastProviderSearchKey(name);
    }

    public static void setLastProviderSearchKey(String name) {
        if (name == null) {
            lastProviderSearchKey = null;
            return;
        }
        String trimmed = name.trim();
        String resolved = resolveProviderSearchKey(trimmed);
        lastProviderSearchKey = resolved.isEmpty() ? null : resolved;
    }

    public static void presetCraftingProviderSearchKey() {
        setLastProviderSearchKey(resolveSearchKeyAlias(DEFAULT_CRAFTING_SEARCH_KEY));
    }

    public static String consumeLastProviderSearchKey() {
        String searchKey = lastProviderSearchKey;
        lastProviderSearchKey = null;
        return searchKey;
    }

    /**
     * 读取但不消费最近一次的供应器搜索词。
     *
     * <p>编码按钮点击时需要把搜索词随包上传给服务端做「唯一匹配」判定，而弹窗打开时仍需要
     * 消费同一个搜索词。两者互不影响：本方法只读不清，{@link #consumeLastProviderSearchKey()}
     * 保留原有清空语义。</p>
     */
    public static String peekLastProviderSearchKey() {
        return lastProviderSearchKey;
    }

    /**
     * 供应器名称与搜索词的匹配判定（唯一匹配语义的公共实现）。
     *
     * <p>逻辑从 {@code ProviderSelectScreen.nameMatches} 原样迁移：优先使用 JEC 的拼音
     * {@code contains}（含大小写不敏感二次尝试），失败时回退到大小写不敏感子串匹配。
     * 客户端弹窗与服务端自动直传共用此方法，保证「唯一」判定两端一致。</p>
     *
     * @param name 供应器显示名
     * @param key  搜索关键字；为空表示不过滤，视为匹配
     * @return 是否匹配
     */
    public static boolean matchesProviderName(String name, String key) {
        if (name == null) return false;
        if (key == null || key.isEmpty()) return true;
        try {
            if (JEC_AVAILABLE == null) {
                try {
                    Class<?> cls = Class.forName("me.towdium.jecharacters.utils.Match");
                    JEC_CONTAINS = cls.getMethod("contains", CharSequence.class, CharSequence.class);
                    JEC_AVAILABLE = true;
                } catch (Throwable t) {
                    JEC_AVAILABLE = false;
                }
            }
            if (Boolean.TRUE.equals(JEC_AVAILABLE) && JEC_CONTAINS != null) {
                Object r = JEC_CONTAINS.invoke(null, name, key);
                if (r instanceof Boolean && (Boolean) r) return true;
                String nL = name.toLowerCase();
                String kL = key.toLowerCase();
                Object r2 = JEC_CONTAINS.invoke(null, nL, kL);
                if (r2 instanceof Boolean && (Boolean) r2) return true;
            }
        } catch (Throwable ignored) {
            // 回退到子串匹配
        }
        return name.toLowerCase().contains(key.toLowerCase());
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

    /**
     * 向配置中新增或更新“别名 -> 中文”映射，并刷新内存映射。
     * 仅用于非原版（或希望使用最终搜索关键字）场景。
     *
     * @param aliasKey 最终搜索关键字（不含冒号），大小写不敏感
     * @param cnValue  中文名称
     * @return 是否写入成功
     */
    public static synchronized boolean addOrUpdateAliasMapping(String aliasKey, String cnValue) {
        if (aliasKey == null || aliasKey.isBlank() || cnValue == null || cnValue.isBlank()) {
            return false;
        }
        try {
            Path cfgDir = FMLPaths.CONFIGDIR.get();
            Path cfgPath = cfgDir.resolve(CONFIG_RELATIVE);
            if (!Files.exists(cfgPath)) {
                // 若文件不存在，先创建模板
                loadRecipeTypeNames();
            }
            JsonObject obj;
            if (Files.exists(cfgPath)) {
                String json = Files.readString(cfgPath);
                obj = GSON.fromJson(json, JsonObject.class);
                if (obj == null) obj = new JsonObject();
            } else {
                obj = new JsonObject();
            }
            String key = aliasKey.trim();
            // 仅允许作为别名写入（不含冒号），如包含冒号，仍按原样写入，但推荐别名
            obj.addProperty(key, cnValue);
            Files.createDirectories(cfgPath.getParent());
            Files.writeString(cfgPath, GSON.toJson(obj));

            // 更新内存映射
            if (key.contains(":")) {
                try {
                    var rl = ResourceLocation.tryParse(key);
                    if (rl != null) {
                        CUSTOM_NAMES.put(rl, cnValue);
                    }
                } catch (Exception ignored) {}
            } else {
                CUSTOM_ALIASES.put(key.toLowerCase(), cnValue);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean addOrUpdateRecipeTypeMapping(String key, String value) {
        return addOrUpdateAliasMapping(key, value);
    }

    /** 返回当前生效的映射快照，供可视化管理界面展示。 */
    public static List<RecipeTypeMapping> getRecipeTypeMappings() {
        List<RecipeTypeMapping> mappings = new ArrayList<>();
        CUSTOM_NAMES.forEach((key, value) -> mappings.add(new RecipeTypeMapping(key.toString(), value)));
        CUSTOM_ALIASES.forEach((key, value) -> mappings.add(new RecipeTypeMapping(key, value)));
        mappings.sort(Comparator.comparing(RecipeTypeMapping::key, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(mappings);
    }

    /** 按配置键删除单条映射，别名键匹配时忽略大小写。 */
    public static synchronized boolean removeRecipeTypeMapping(String mappingKey) {
        if (mappingKey == null || mappingKey.isBlank()) {
            return false;
        }
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_RELATIVE);
            if (!Files.exists(cfgPath)) {
                return false;
            }
            JsonObject obj = GSON.fromJson(Files.readString(cfgPath), JsonObject.class);
            if (obj == null) {
                return false;
            }

            String requested = mappingKey.trim();
            String storedKey = obj.keySet().stream()
                    .filter(key -> mappingKeysEqual(key, requested))
                    .findFirst()
                    .orElse(null);
            if (storedKey == null) {
                return false;
            }

            obj.remove(storedKey);
            Files.writeString(cfgPath, GSON.toJson(obj));
            loadRecipeTypeNames();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean mappingKeysEqual(String first, String second) {
        if (first.contains(":") || second.contains(":")) {
            return Objects.equals(ResourceLocation.tryParse(first), ResourceLocation.tryParse(second));
        }
        return first.equalsIgnoreCase(second);
    }

    /**
     * 按中文值精确匹配删除映射（支持别名与完整ID）。
     * 返回删除的条目数量。
     */
    public static synchronized int removeMappingsByCnValue(String cnValue) {
        if (cnValue == null) return 0;
        String target = cnValue.trim();
        if (target.isEmpty()) return 0;
        try {
            Path cfgDir = FMLPaths.CONFIGDIR.get();
            Path cfgPath = cfgDir.resolve(CONFIG_RELATIVE);
            if (!Files.exists(cfgPath)) {
                return 0;
            }
            String json = Files.readString(cfgPath);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return 0;

            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                JsonElement v = e.getValue();
                if (v != null && v.isJsonPrimitive()) {
                    String name = v.getAsString();
                    if (target.equals(name)) {
                        toRemove.add(e.getKey());
                    }
                }
            }
            if (toRemove.isEmpty()) return 0;

            // 从 JSON 中移除
            for (String k : toRemove) {
                obj.remove(k);
            }
            Files.createDirectories(cfgPath.getParent());
            Files.writeString(cfgPath, GSON.toJson(obj));

            // 同步移除内存映射
            for (String k : toRemove) {
                if (k.contains(":")) {
                    try {
                        var rl = ResourceLocation.tryParse(k);
                        if (rl != null) {
                            String cur = CUSTOM_NAMES.get(rl);
                            if (target.equals(cur)) {
                                CUSTOM_NAMES.remove(rl);
                            }
                        }
                    } catch (Exception ignored) {}
                } else {
                    // 别名按小写存放
                    String lower = k.toLowerCase();
                    String cur = CUSTOM_ALIASES.get(lower);
                    if (target.equals(cur)) {
                        CUSTOM_ALIASES.remove(lower);
                    }
                }
            }
            return toRemove.size();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 供搜索使用的关键字映射：
     * - 有中文映射则返回中文；
     * - 否则返回配方类型的 path（不含命名空间），例如 assembler。
     */
    public static String resolveSearchKeyAlias(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String normalized = rawKey.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String alias = CUSTOM_ALIASES.get(normalized.toLowerCase());
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        return normalized;
    }

    /** 将供应器界面的原始输入转换为映射后的实际搜索关键字。 */
    public static String resolveProviderSearchKey(String rawKey) {
        String normalized = rawKey == null ? "" : rawKey.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }

        String alias = resolveSearchKeyAlias(normalized);
        if (!normalized.equals(alias)) {
            return alias;
        }

        ResourceLocation recipeType = ResourceLocation.tryParse(normalized);
        if (recipeType != null) {
            String mapped = resolveRecipeTypeSearchKey(recipeType, null);
            if (mapped != null && !mapped.isBlank() && !mapped.equals(recipeType.getPath())) {
                return mapped;
            }
        }
        return normalized;
    }

    public static String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        if (recipe == null) return null;
        return resolveRecipeTypeSearchKey(resolveRecipeTypeId(recipe.getType()), null);
    }

    /** 统一解析配方类型，优先配置映射，其次使用配方查看器提供的分类标题。 */
    public static String resolveRecipeTypeSearchKey(ResourceLocation key, String displayName) {
        if (key == null) return null;

        String custom = CUSTOM_NAMES.get(key);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }

        String alias = CUSTOM_ALIASES.get(key.getPath().toLowerCase(Locale.ROOT));
        if (alias != null && !alias.isBlank()) {
            return alias;
        }

        return displayName != null && !displayName.isBlank() ? displayName : key.getPath();
    }

    /** 注册表反查失败时，RecipeType.simple 创建的类型仍会通过 toString 暴露其 ID。 */
    private static ResourceLocation resolveRecipeTypeId(RecipeType<?> type) {
        if (type == null) return null;
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key != null) {
            return key;
        }
        return ResourceLocation.tryParse(type.toString());
    }

    /**
     * 当 JEI 传入的 recipeBase 不是原版 Recipe<?> 时，优先解析类型 ID，再根据类名推导搜索关键字。
     */
    public static String deriveSearchKeyFromUnknownRecipe(Object recipeBase) {
        if (recipeBase == null) return null;
        // 优先尝试反射 getType()：覆盖“单一配方类 + 类型字段”设计的模组（如 Oritech），
        // 避免所有机器配方被类名推导成同一个关键字。
        try {
            Object type = recipeBase.getClass().getMethod("getType").invoke(recipeBase);
            // 非原版类型也可通过其 namespace:path 标识走同一套映射。
            ResourceLocation key = type instanceof RecipeType<?> rt
                    ? resolveRecipeTypeId(rt)
                    : type == null ? null : ResourceLocation.tryParse(type.toString());
            if (key != null) {
                String resolved = resolveRecipeTypeSearchKey(key, null);
                if (resolved != null && !resolved.isBlank()) return resolved;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            LOGGER.debug("无法从配方类型解析搜索关键字，改用类名: {}", recipeBase.getClass().getName(), e);
        }
        try {
            Class<?> cls = recipeBase.getClass();
            String simple = cls.getSimpleName();
            String pkg = cls.getName();

            String ns = null;
            String lower = pkg.toLowerCase();
            if (lower.contains("projecte")) ns = "projecte";
            else if (lower.contains("create")) ns = "create";
            else if (lower.contains("immersiveengineering")) ns = "immersive";

            String token = toSearchToken(simple);
            String key;
            if (ns != null && token != null && !token.isBlank()) key = ns + " " + token;
            else key = token != null && !token.isBlank() ? token : ns;
            if (key == null || key.isBlank()) return null;
            // 尝试别名映射（大小写不敏感）
            String alias = CUSTOM_ALIASES.get(key.toLowerCase());
            return (alias != null && !alias.isBlank()) ? alias : key;
        } catch (RuntimeException e) {
            LOGGER.warn("无法根据配方类名推导搜索关键字: {}", recipeBase.getClass().getName(), e);
            return null;
        }
    }

    private static String toSearchToken(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) return null;
        // 去掉常见后缀
        String s = simpleName
                .replaceAll("Recipe$", "")
                .replaceAll("Recipes$", "")
                .replaceAll("Category$", "")
                .replaceAll("JEI$", "");
        // 驼峰转空格并小写
        s = s.replaceAll("(?<!^)([A-Z])", " $1").toLowerCase();
        // 取首个关键词
        s = s.trim();
        return s;
    }

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
     * 通过上传菜单接口读取编码槽物品。
     */
    public static ItemStack getEncodedPatternFromMenu(Object menu) {
        var slot = getEncodedPatternSlot(menu);
        return slot != null ? slot.getItem() : ItemStack.EMPTY;
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
            if (uploadFromEncodingMenuToMatrix(player, menu)) {
                notifyMatrixUpload(player);
                return true;
            }
            return false;
        }

        if (uploadFromEncodingMenuToMatrix(player, menu)) {
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
     *
     * <p>不复用 {@link #sendMessage}：该方法是刻意保留的空实现，恢复其实现会同时激活回退流程中
     * 被拒目标的失败文案。此处只负责已确认成功的提示。</p>
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
        candidates.sort(Comparator.<PatternContainer>comparingInt(ExtendedAEPatternUploadUtil::getAvailableSlots).reversed());

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
     * 从 AE2 的图样编码终端菜单上传当前“已编码图样”至 ExtendedAE 装配矩阵（仅合成图样）。
     * 不会处理“处理图样”。
     *
     * @param player 服务器玩家
     * @param menu   PatternEncodingTermMenu
     * @return 是否成功插入矩阵
     */
    public static boolean uploadFromEncodingMenuToMatrix(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu == null) {
            return false;
        }

        // 读取已编码槽位的物品
        var encodedSlot = ((PatternEncodingTermMenuAccessor) (Object) menu)
                .eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            sendMessage(player, "extendedae_plus.message.upload.no_pattern");
            return false;
        }

        // 仅允许“合成/锻造台/切石机图样”
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
        if (!(details instanceof AECraftingPattern
                || details instanceof AESmithingTablePattern
                || details instanceof AEStonecuttingPattern)) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail");
            return false;
        }

        // 获取 AE 网络
        IGrid grid = resolveGrid(menu);
        if (grid == null) {
            sendMessage(player, "extendedae_plus.message.network.invalid");
            return false;
        }

        // 在尝试上传之前，检查装配矩阵是否已经存在相同样板（物品与NBT完全一致）
        if (matrixContainsPattern(grid, stack)) {
            // 直接提醒并跳过上传，并将同等数量的空白样板放回空白样板槽，否则退回玩家背包
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.matrix.duplicate"));
            }
            // 回收逻辑与供应器重复命中共用，避免两处漂移
            recycleEncodedPatternAsBlanks(player, menu, stack);
            return false;
        }

        // 收集所有可用的装配矩阵（图样模块）内部库存并逐一尝试（遵循其过滤规则）
        List<MatrixInventoryTarget> inventories = findAllMatrixPatternInventories(grid);
        if (!inventories.isEmpty()) {
            for (MatrixInventoryTarget target : inventories) {
                if (target == null || target.insertInventory() == null || target.patternInventory() == null) continue;
                ItemStack toInsert = stack.copy();
                ItemStack[] before = snapshotInventory(target.patternInventory(), target.patternInventory().size());
                ItemStack remain = target.insertInventory().addItems(toInsert);
                if (remain.getCount() < stack.getCount()) {
                    int inserted = stack.getCount() - remain.getCount();
                    stack.shrink(inserted);
                    if (stack.isEmpty()) {
                        encodedSlot.set(ItemStack.EMPTY);
                    }
                    recordMatrixUpload(
                            player,
                            target.pos(),
                            player.level().dimension().location().toString(),
                            target.plus(),
                            findLastChangedSlot(target.patternInventory(), before, target.patternInventory().size())
                    );
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
            // 所有内部库存都无法接收 -> 尝试 capability 回退
        }

        // 回退：尝试 Forge 能力（可能为聚合图样仓），同样遍历所有矩阵
        List<?> handlers = findAllMatrixPatternHandlers(grid);
        if (!handlers.isEmpty()) {
            for (int i = 0; i < handlers.size(); i++) {
                var cap = handlers.get(i);
                ItemStack toInsert = stack.copy();
                ItemStack remain = insertIntoAnySlot(cap, toInsert);
                if (remain.getCount() < stack.getCount()) {
                    int inserted = stack.getCount() - remain.getCount();
                    stack.shrink(inserted);
                    if (stack.isEmpty()) {
                        encodedSlot.set(ItemStack.EMPTY);
                    }
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
        }

        // 未找到可用矩阵或全部拒收
        if (inventories.isEmpty() && handlers.isEmpty()) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_no_matrix");
        } else {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_full");
        }
        return false;
    }

    /**
     * 将给定的已编码样板直接上传到装配矩阵（不依赖编码终端槽位）。
     *
     * @param player 服务器玩家
     * @param pattern 已编码样板
     * @param grid AE 网络
     * @return 是否成功插入矩阵
     */
    public static boolean uploadPatternToMatrix(ServerPlayer player, ItemStack pattern, IGrid grid) {
        if (player == null || grid == null || pattern == null || pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
            return false;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, player.level());
        if (!(details instanceof AECraftingPattern
                || details instanceof AESmithingTablePattern
                || details instanceof AEStonecuttingPattern)) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail");
            return false;
        }

        if (matrixContainsPattern(grid, pattern)) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.matrix.duplicate"));
            }
            return false;
        }

        List<MatrixInventoryTarget> inventories = findAllMatrixPatternInventories(grid);
        if (!inventories.isEmpty()) {
            for (MatrixInventoryTarget target : inventories) {
                if (target == null || target.insertInventory() == null || target.patternInventory() == null) continue;
                ItemStack toInsert = pattern.copy();
                ItemStack[] before = snapshotInventory(target.patternInventory(), target.patternInventory().size());
                ItemStack remain = target.insertInventory().addItems(toInsert);
                if (remain.getCount() < toInsert.getCount()) {
                    recordMatrixUpload(
                            player,
                            target.pos(),
                            player.level().dimension().location().toString(),
                            target.plus(),
                            findLastChangedSlot(target.patternInventory(), before, target.patternInventory().size())
                    );
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
        }

        List<?> handlers = findAllMatrixPatternHandlers(grid);
        if (!handlers.isEmpty()) {
            for (Object cap : handlers) {
                ItemStack toInsert = pattern.copy();
                ItemStack remain = insertIntoAnySlot(cap, toInsert);
                if (remain.getCount() < toInsert.getCount()) {
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
        }

        if (inventories.isEmpty() && handlers.isEmpty()) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_no_matrix");
        } else {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_full");
        }
        return false;
    }

    /**
     * 在给定 AE Grid 中收集所有已成型且在线的装配矩阵“图样模块”的用于外部插入的内部库存。
     * 优先使用 TileAssemblerMatrixPattern#getExposedInventory（仅允许插入，且已带AE过滤规则）。
     */
    private static List<MatrixInventoryTarget> findAllMatrixPatternInventories(IGrid grid) {
        List<MatrixInventoryTarget> result = new ArrayList<>();
        if(grid== null) return result;
        try {
            // 超级矩阵由主控聚合所有混合核心库存，不依赖装配矩阵上传核心。
            for (SuperAssemblerMatrixBlockEntity superMatrix : findSuperMatrices(grid)) {
                if (superMatrix == null || !superMatrix.isVisibleInTerminal() || superMatrix.getGrid() != grid) {
                    continue;
                }
                InternalInventory inventory = superMatrix.getTerminalPatternInventory();
                if (inventory != null && inventory.size() > 0) {
                    result.add(new MatrixInventoryTarget(inventory, inventory,
                            superMatrix.getBlockPos(), true));
                }
            }

            Set<TileAssemblerMatrixPattern> allTiles = grid.getMachines(TileAssemblerMatrixPattern.class);
            Set<PatternCorePlusBlockEntity> myAllTiles = grid.getMachines(PatternCorePlusBlockEntity.class);

            // 用 Set 记录已经扫描过的集群，避免重复调用 clusterHasSingleUploadCore
            Set<ClusterAssemblerMatrix> scannedClusters = new HashSet<>();

            for (TileAssemblerMatrixPattern tile : allTiles) {
                if (tile == null || !tile.isFormed() || !tile.getMainNode().isActive()) continue;

                ClusterAssemblerMatrix cluster = tile.getCluster();
                if (cluster == null) continue;

                // 如果该集群已经扫描过，或者该集群含 UploadCore，则处理 tile
                if (scannedClusters.contains(cluster) || clusterHasSingleUploadCore(cluster)) {
                    scannedClusters.add(cluster); // 标记为已扫描

                    InternalInventory insertInv = tile.getExposedInventory();
                    InternalInventory patternInv = tile.getTerminalPatternInventory();
                    if (insertInv != null && patternInv != null) {
                        result.add(new MatrixInventoryTarget(insertInv, patternInv, tile.getBlockPos(), false));
                    }
                }
            }

            for (PatternCorePlusBlockEntity myTile : myAllTiles) {
                if (myTile == null || !myTile.isFormed() || !myTile.getMainNode().isActive()) continue;

                ClusterAssemblerMatrix cluster = myTile.getCluster();
                if (cluster == null) continue;

                // 如果该集群已经扫描过，或者该集群含 UploadCore，则处理 tile
                if (scannedClusters.contains(cluster) || clusterHasSingleUploadCore(cluster)) {
                    scannedClusters.add(cluster); // 标记为已扫描

                    InternalInventory insertInv = myTile.getExposedInventory();
                    InternalInventory patternInv = myTile.getTerminalPatternInventory();
                    if (insertInv != null && patternInv != null) {
                        result.add(new MatrixInventoryTarget(insertInv, patternInv, myTile.getBlockPos(), true));
                    }
                }
            }

            // 【新增部分】：适配 ae2cs 自装配式样板供应器 (Meteorite Pattern Provider)
            try {
                for (Class<?> machineClass : grid.getMachineClasses()) {
                    String className = machineClass.getName();
                    if (className != null && className.contains("MeteoritePatternProvider")) {
                        @SuppressWarnings("unchecked")
                        Set<?> hosts = grid.getMachines((Class) machineClass);
                        for (Object host : hosts) {
                            if (host instanceof PatternProviderLogicHost logicHost) {
                                PatternProviderLogic logic = logicHost.getLogic();
                                if (logic != null) {
                                    InternalInventory inv = logic.getPatternInv();
                                    if (inv != null) {
                                        result.add(new MatrixInventoryTarget(inv, inv, null, false));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
        }
        return result;
    }

    /**
     * AE2 按节点实际类索引机器，需遍历框架/墙等具体子类才能找到超级矩阵主控。
     */
    private static List<SuperAssemblerMatrixBlockEntity> findSuperMatrices(IGrid grid) {
        var result = new ArrayList<SuperAssemblerMatrixBlockEntity>();
        if (grid == null) {
            return result;
        }

        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!SuperAssemblerMatrixBlockEntity.class.isAssignableFrom(machineClass)) {
                continue;
            }
            for (Object machine : grid.getMachines(machineClass)) {
                if (machine instanceof SuperAssemblerMatrixBlockEntity superMatrix) {
                    result.add(superMatrix);
                }
            }
        }
        return result;
    }

    /**
     * 在给定 AE Grid 中收集所有已成型的装配矩阵的聚合图样仓 IItemHandler（若可用）。
     */
    private static List<?> findAllMatrixPatternHandlers(IGrid grid) {
        // NeoForge 1.21 能力系统与 API 变更，此处先返回空列表，避免编译期依赖旧能力系统
        return Collections.emptyList();
    }

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

    /**
     * 复制样板并剔除编码作者标记（{@code encodePlayer}）。
     *
     * <p>「除编码作者以外完全相同」的比对语义统一以此为准：不同玩家编码的同一配方样板
     * 剔除作者后应当视为相同。矩阵与供应器两处重复检测共用，避免逻辑漂移。</p>
     */
    private static ItemStack stripEncodePlayer(ItemStack stack) {
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

    private static boolean matrixContainsPattern(IGrid grid, ItemStack pattern) {
        if (grid == null || pattern == null || pattern.isEmpty()) return false;
        ItemStack patternCopy = stripEncodePlayer(pattern);
        // 先检查提供外部插入视图的内部库存
        for (MatrixInventoryTarget target : findAllMatrixPatternInventories(grid)) {
            InternalInventory inv = target == null ? null : target.patternInventory();
            if (inv == null) continue;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(stripEncodePlayer(s), patternCopy)) {
                    return true;
                }
            }
        }
        // 1.21 暂不检查聚合能力视图，能力系统适配后再补充
        return false;
    }

    /**
     * 能力系统（IItemHandler）未迁移前的占位插入：直接返回原始栈，表示未能插入。
     */
    private static ItemStack insertIntoAnySlot(Object handler, ItemStack stack) {
        return stack.copy();
    }

    /**
     * 检查当前菜单是否为ExtendedAE的扩展样板管理终端
     * 
     * @param player 玩家
     * @return 是否为ExtendedAE扩展终端
     */
    private static boolean isExtendedAETerminal(ServerPlayer player) {
        if (player == null || player.containerMenu == null) {
            return false;
        }
        
        String containerClassName = player.containerMenu.getClass().getName();
        return containerClassName.equals("com.glodblock.github.extendedae.container.ContainerExPatternTerminal");
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
            sendMessage(player, "extendedae_plus.message.open_terminal_first");
            return false;
        }

        // 2. 获取玩家背包中的物品
        ItemStack playerItem = player.getInventory().getItem(playerSlotIndex);
        if (playerItem.isEmpty()) {
            sendMessage(player, "extendedae_plus.message.inventory_slot_empty");
            return false;
        }

        // 3. 验证是否是编码样板
        if (!PatternDetailsHelper.isEncodedPattern(playerItem)) {
            sendMessage(player, "extendedae_plus.message.invalid_pattern_item");
            return false;
        }

        // 4. 获取目标样板供应器
        PatternContainer patternContainer = getPatternContainerById(menu, providerId);
        if (patternContainer == null) {
            sendMessage(player, "extendedae_plus.message.provider.not_found", providerId);
            return false;
        }

        // 5. 获取样板供应器的库存
        InternalInventory patternInventory = patternContainer.getTerminalPatternInventory();
        if (patternInventory == null) {
            sendMessage(player, "extendedae_plus.message.provider.inventory_unavailable");
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

            String terminalTypeKey = isExtendedAETerminal(player)
                    ? "extendedae_plus.terminal.expattern"
                    : "extendedae_plus.terminal.pattern_access";
            sendMessage(player, "extendedae_plus.message.upload.success_single",
                    Component.translatable(terminalTypeKey), insertedCount);
            recordProviderUpload(player, providerId, patternContainer, findLastChangedSlot(patternInventory, before, slotLimit));
            return true;
        } else {
            sendMessage(player, "extendedae_plus.message.upload.fail");
            return false;
        }
    }

    /**
     * 批量上传样板到指定供应器（支持ExtendedAE和原版AE2）
     * 
     * @param player 玩家
     * @param playerSlotIndices 玩家背包槽位索引数组
     * @param providerId 目标样板供应器ID
     * @return 成功上传的样板数量
     */
    public static int uploadMultiplePatterns(ServerPlayer player, int[] playerSlotIndices, long providerId) {
        int successCount = 0;
        
        for (int slotIndex : playerSlotIndices) {
            if (uploadPatternToProvider(player, slotIndex, providerId)) {
                successCount++;
            }
        }
        
        String terminalTypeKey = isExtendedAETerminal(player)
                ? "extendedae_plus.terminal.expattern"
                : "extendedae_plus.terminal.pattern_access";
        sendMessage(player, "extendedae_plus.message.upload.success_batch",
                Component.translatable(terminalTypeKey), successCount);
        return successCount;
    }

    /**
     * 检查样板供应器是否有足够的空槽位
     * 
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单（支持ExtendedAE）
     * @param requiredSlots 需要的槽位数
     * @return 是否有足够的空槽位
     */
    public static boolean hasEnoughSlots(long providerId, PatternAccessTermMenu menu, int requiredSlots) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return false;
        }

        InternalInventory inventory = container.getTerminalPatternInventory();
        if (inventory == null) {
            return false;
        }

        int availableSlots = 0;
        int slotLimit = getAccessiblePatternSlotCount(container, inventory);
        for (int i = 0; i < slotLimit; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                availableSlots++;
                if (availableSlots >= requiredSlots) {
                    return true;
                }
            }
        }

        return false;
    }

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

    private static ItemStack[] snapshotInventory(InternalInventory inv, int slotLimit) {
        int limit = Math.max(0, Math.min(inv.size(), slotLimit));
        ItemStack[] snapshot = new ItemStack[limit];
        for (int i = 0; i < limit; i++) {
            snapshot[i] = inv.getStackInSlot(i).copy();
        }
        return snapshot;
    }

    private static int findLastChangedSlot(InternalInventory inv, ItemStack[] before, int slotLimit) {
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

    /**
     * 发送消息给玩家
     * 
     * @param player 玩家
     * @param key 翻译键
     * @param args 参数
     */
    private static void sendMessage(ServerPlayer player, String key, Object... args) {
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
     * 获取当前终端类型的描述
     *
     * @param player 玩家
     * @return 终端类型描述
     */
    public static String getTerminalTypeDescription(ServerPlayer player) {
        if (isExtendedAETerminal(player)) {
            return Component.translatable("extendedae_plus.terminal.expattern").getString();
        } else if (getPatternAccessMenu(player) != null) {
            return Component.translatable("extendedae_plus.terminal.pattern_access").getString();
        } else {
            return Component.translatable("extendedae_plus.terminal.unknown").getString();
        }
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
        ItemStack stack = getEncodedPatternFromMenu(menu);
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
     * 判断给定矩阵集群中是否存在"装配矩阵上传核心"。
     * 要求：至少存在 1 个即可，不限制数量。
     * 传入任意属于该集群的 Tile（如 Pattern/Crafter/Frame 等）。
     */
    private static boolean clusterHasSingleUploadCore(ClusterAssemblerMatrix any) {
        try {
            if (any == null) return false;
            int cores = 0;
            var it = any.getBlockEntities();
            while (it.hasNext()) {
                var te = it.next();
                if (te instanceof UploadCoreBlockEntity) {
                    cores++;
                }
            }
            return cores >= 1; // 至少一个即可
        } catch (Throwable t) {
            return false;
        }
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
