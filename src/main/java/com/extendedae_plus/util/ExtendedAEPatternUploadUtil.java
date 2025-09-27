package com.extendedae_plus.util;

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
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * ExtendedAE扩展样板管理终端专用的样板上传工具类
 * 兼容ExtendedAE的ContainerExPatternTerminal和原版AE2的PatternAccessTermMenu
 */
public class ExtendedAEPatternUploadUtil {

    // --------------------------- 配置：RecipeType 中文名称映射 ---------------------------
    private static final String CONFIG_RELATIVE = "extendedae_plus/recipe_type_names.json";
    private static final Map<ResourceLocation, String> CUSTOM_NAMES = new ConcurrentHashMap<>();
    // 允许使用最终搜索关键字（通常为 path 或自定义短语）作为键，例如："assembler": "组装机"
    private static final Map<String, String> CUSTOM_ALIASES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static {
        try {
            loadRecipeTypeNames();
        } catch (Throwable t) {
            // 安静失败，使用内置映射
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
                // 创建目录并写入模板
                Files.createDirectories(cfgPath.getParent());
                JsonObject tmpl = new JsonObject();
                // 提供一些常见原版默认（仅作为示例，实际仍以内置 switch 为兜底）
                tmpl.addProperty("minecraft:smelting", "熔炉");
                tmpl.addProperty("minecraft:blasting", "高炉");
                tmpl.addProperty("minecraft:smoking", "烟熏");
                tmpl.addProperty("minecraft:campfire_cooking", "营火");
                // GTCEu 示例占位
                tmpl.addProperty("gtceu:assembler", "组装机");
                tmpl.addProperty("gtceu:arc_furnace", "电弧炉");
                tmpl.addProperty("gtceu:chemical_reactor", "化学反应器");
                // 也支持别名（最终搜索关键字）形式，例如：
                tmpl.addProperty("assembler", "组装机");
                Files.writeString(cfgPath, GSON.toJson(tmpl));
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

    // 最近一次通过 JEI 填充到编码终端的“处理配方”的中文名称（如：烧炼/高炉/烟熏...）
    public static volatile List<String> lastProcessingNameList = new ArrayList<>();

    public static void addLastProcessingNameList(String name) {
        lastProcessingNameList.add(name);
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

            java.util.List<String> toRemove = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, JsonElement> e : obj.entrySet()) {
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

    public static String mapRecipeTypeToCn(Recipe<?> recipe) {
        if (recipe == null) return null;
        RecipeType<?> type = recipe.getType();
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key == null) return null;
        // 1) 自定义配置优先
        String custom = CUSTOM_NAMES.get(key);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        String id = key.toString();
        String path = key.getPath();
        // 常见原版类型映射
        return switch (path) {
            case "smelting" -> "熔炉"; // 熔炉
            case "blasting" -> "高炉";
            case "smoking" -> "烟熏";
            case "campfire_cooking" -> "营火";
            // 其他模组类型，若未配置中文则返回原始ID（namespace:path）作为英文回退
            default -> id;
        };
    }

    /**
     * 供搜索使用的关键字映射：
     * - 有中文映射则返回中文；
     * - 否则返回配方类型的 path（不含命名空间），例如 assembler。
     */
    public static String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        if (recipe == null) return null;
        RecipeType<?> type = recipe.getType();
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key == null) return null;
        // 先查别名（按 path 匹配）
        // 别名替换放在init做, 提前别名的次序
//        String alias = CUSTOM_ALIASES.get(key.getPath().toLowerCase());
//        if (alias != null && !alias.isBlank()) return alias;
        // 再查完整ID映射
//        String custom = CUSTOM_NAMES.get(key);
//        if (custom != null && !custom.isBlank()) {
//            return custom;
//        }
        return key.getPath();
    }

    // 注意：GTCEu 的映射方法已在下方提供基于 Object 的反射版本，避免重复定义。

    /**
     * 仅使用反射的 GTCEu GTRecipe -> 搜索关键字（避免在运行时直接引用 GTCEu 类）。
     */
    public static String mapGTCEuRecipeToSearchKey(Object gtRecipeObj) {
        if (gtRecipeObj == null) return null;
        try {
            // 通过反射调用 getType()，其 toString() 应返回 registryName，即 namespace:path
            java.lang.reflect.Method mGetType = gtRecipeObj.getClass().getMethod("getType");
            Object typeObj = mGetType.invoke(gtRecipeObj);
            String idStr = String.valueOf(typeObj);
            if (idStr == null || idStr.isBlank()) return null;
            var rl = ResourceLocation.tryParse(idStr);
            // 1) 别名优先（使用 path 作为最终搜索关键字）
            String path = rl != null ? rl.getPath() : null;
            if (path != null) {
                String alias = CUSTOM_ALIASES.get(path.toLowerCase());
                if (alias != null && !alias.isBlank()) return alias;
            }
            // 2) 再查完整ID映射
            String custom = rl != null ? CUSTOM_NAMES.get(rl) : null;
            if (custom != null && !custom.isBlank()) return custom;
            // 3) 默认返回 path 作为搜索关键字
            return (path != null && !path.isBlank()) ? path : idStr;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 当 JEI 传入的 recipeBase 不是原版 Recipe<?> 时，根据类的包名/类名推导一个尽量可用的搜索关键字。
     * 例如："moe.gregtech.recipe.SomeAssemblerRecipe" -> "gtceu assembler"
     */
    public static String deriveSearchKeyFromUnknownRecipe(Object recipeBase) {
        if (recipeBase == null) return null;
        try {
            Class<?> cls = recipeBase.getClass();
            String simple = cls.getSimpleName();
            String pkg = cls.getName();

            String ns = null;
            String lower = pkg.toLowerCase();
            if (lower.contains("gtceu")) ns = "gtceu";
            else if (lower.contains("gregtech")) ns = "gregtech";
            else if (lower.contains("projecte")) ns = "projecte";
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
        } catch (Throwable ignored) {
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

    public static String findMapping(String key) {
        if (key == null || key.isBlank()) return null;

        if (CUSTOM_ALIASES.containsKey(key.toLowerCase()))
            return CUSTOM_ALIASES.get(key.toLowerCase());

        if (key.contains(":")) {
            try {
                ResourceLocation location = ResourceLocation.tryParse(key);
                if (location != null && CUSTOM_NAMES.containsKey(location))
                    return CUSTOM_NAMES.get(location);
            } catch (Exception ignored) {}
        }

        if (!Pattern.matches(".*[:._/\\-].*", key)) return key;

        return null;
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
            sendMessage(player, "ExtendedAE Plus: 没有可上传的编码样板");
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
        IGrid grid = null;
        try {
            if (menu instanceof AEBaseMenu abm) {
                Object target = abm.getTarget();
                if (target instanceof IActionHost host && host.getActionableNode() != null) {
                    grid = host.getActionableNode().getGrid();
                }
            }
        } catch (Throwable ignored) {}
        if (grid == null) {
            sendMessage(player, "ExtendedAE Plus: 当前不在有效的 AE 网络中");
            return false;
        }

        // 在尝试上传之前，检查装配矩阵是否已经存在相同样板（物品与NBT完全一致）
        if (matrixContainsPattern(grid, stack)) {
            // 直接提醒并跳过上传，并将同等数量的空白样板放回空白样板槽，否则退回玩家背包
            if (player != null) {
                player.sendSystemMessage(Component.literal("ExtendedAE Plus: 装配矩阵已存在相同样板，已跳过上传并返还空白样板"));
            }
            try {
                var accessor = (PatternEncodingTermMenuAccessor) (Object) menu;
                var blankSlot = accessor.eap$getBlankPatternSlot();
                ItemStack blanks = AEItems.BLANK_PATTERN.stack(stack.getCount());
                if (blankSlot != null && blankSlot.mayPlace(blanks)) {
                    ItemStack remain = blankSlot.safeInsert(blanks);
                    if (!remain.isEmpty() && player != null) {
                        player.getInventory().placeItemBackInInventory(remain, false);
                    }
                } else if (player != null) {
                    player.getInventory().placeItemBackInInventory(blanks, false);
                }
            } catch (Throwable t) {
                if (player != null) {
                    // 兜底：直接还给玩家背包
                    player.getInventory().placeItemBackInInventory(AEItems.BLANK_PATTERN.stack(stack.getCount()), false);
                }
            }
            // 清空编码样板槽，防止再次输出
            encodedSlot.set(ItemStack.EMPTY);
            return false;
        }

        // 收集所有可用的装配矩阵（图样模块）内部库存并逐一尝试（遵循其过滤规则）
        List<InternalInventory> inventories = findAllMatrixPatternInventories(grid);
        if (!inventories.isEmpty()) {
            for (InternalInventory inv : inventories) {
                ItemStack toInsert = stack.copy();
                ItemStack remain = inv.addItems(toInsert);
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
            // 所有内部库存都无法接收 -> 尝试 capability 回退
        }

        // 回退：尝试 Forge 能力（可能为聚合图样仓），同样遍历所有矩阵
        List<?> handlers = findAllMatrixPatternHandlers(grid);
        if (!handlers.isEmpty()) {
            for (Object cap : handlers) {
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
     * 在给定 AE Grid 中收集所有已成型且在线的装配矩阵“图样模块”的用于外部插入的内部库存。
     * 优先使用 TileAssemblerMatrixPattern#getExposedInventory（仅允许插入，且已带AE过滤规则）。
     */
    private static List<InternalInventory> findAllMatrixPatternInventories(IGrid grid) {
        List<InternalInventory> result = new ArrayList<>();
        try {
            var tiles = grid.getMachines(com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern.class);
            int idx = 0;
            for (com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern tile : tiles) {
                if (tile != null && tile.isFormed() && tile.getMainNode().isActive()) {
                    var inv = tile.getExposedInventory();
                    if (inv != null) {
                        result.add(inv);
                    }
                }
                idx++;
            }
        } catch (Throwable t) {
        }
        return result;
    }

    /**
     * 在给定 AE Grid 中收集所有已成型的装配矩阵的聚合图样仓 IItemHandler（若可用）。
     */
    private static List<?> findAllMatrixPatternHandlers(IGrid grid) {
        // NeoForge 1.21 能力系统与 API 变更，此处先返回空列表，避免编译期依赖旧能力系统
        return java.util.Collections.emptyList();
    }

    private static boolean matrixContainsPattern(IGrid grid, ItemStack pattern) {
        if (grid == null || pattern == null || pattern.isEmpty()) return false;
        try {
            // 先检查提供外部插入视图的内部库存
            List<InternalInventory> inventories = findAllMatrixPatternInventories(grid);
            for (InternalInventory inv : inventories) {
                if (inv == null) continue;
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (!s.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, pattern)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
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
    public static boolean isExtendedAETerminal(ServerPlayer player) {
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
        PatternContainer patternContainer = getPatternContainerById(menu, providerId);
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

        // 6. 使用AE2的标准样板过滤器进行插入
        var patternFilter = new ExtendedAEPatternFilter();
        var filteredInventory = new FilteredInternalInventory(patternInventory, patternFilter);

        // 7. 尝试插入样板
        ItemStack itemToInsert = playerItem.copy();
        ItemStack remaining = filteredInventory.addItems(itemToInsert);

        if (remaining.getCount() < itemToInsert.getCount()) {
            // 插入成功（部分或全部）
            int insertedCount = itemToInsert.getCount() - remaining.getCount();
            playerItem.shrink(insertedCount);
            
            if (playerItem.isEmpty()) {
                player.getInventory().setItem(playerSlotIndex, ItemStack.EMPTY);
            }
            
            String terminalType = isExtendedAETerminal(player) ? "扩展样板管理终端" : "样板访问终端";
            sendMessage(player, "ExtendedAE Plus: 通过" + terminalType + "成功上传 " + insertedCount + " 个样板");
            return true;
        } else {
            sendMessage(player, "ExtendedAE Plus: 上传失败 - 样板供应器已满或样板无效");
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
        
        String terminalType = isExtendedAETerminal(player) ? "扩展样板管理终端" : "样板访问终端";
        sendMessage(player, "ExtendedAE Plus: 通过" + terminalType + "批量上传完成，成功上传 " + successCount + " 个样板");
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
        for (int i = 0; i < inventory.size(); i++) {
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
        for (int i = 0; i < inventory.size(); i++) {
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
                System.err.println("ExtendedAE Plus: 无法找到byId字段");
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
                System.err.println("ExtendedAE Plus: 无法找到container字段");
                return null;
            }
            
            containerField.setAccessible(true);
            return (PatternContainer) containerField.get(containerTracker);
            
        } catch (Exception e) {
            System.err.println("ExtendedAE Plus: 无法获取PatternContainer，错误: " + e.getMessage());
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

    /**
     * 获取样板供应器的显示名称
     * 
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单
     * @return 显示名称，如果无法获取则返回"未知供应器"
     */
    public static String getProviderDisplayName(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return "未知供应器";
        }

        try {
            // 尝试获取供应器的组信息来构建显示名称
            var group = container.getTerminalGroup();
            if (group != null) {
                return group.name().getString();
            }
        } catch (Exception e) {
            // 忽略异常，使用默认名称
        }

        return "样板供应器 #" + providerId;
    }

    /** 获取供应器显示名（优先组名） */
    public static String getProviderDisplayName(PatternContainer container) {
        if (container == null) return "未知供应器";
        try {
            var group = container.getTerminalGroup();
            if (group != null) return group.name().getString();
        } catch (Throwable ignored) {
        }
        return "样板供应器";
    }

    public static String getProviderI18nName(Long providerId, PatternAccessTermMenu menu) {
        return getProviderI18nName(getPatternContainerById(menu, providerId));
    }

    /** 获取样板供应器默认选取的方块名称 */
    public static String getProviderI18nName(PatternContainer container) {
        if (container == null) return "";
        try {
            var group = container.getTerminalGroup();
            var name = group.name();
            return name.toString().contains("literal") ? "" : name.toString();
        } catch (Throwable ignored) {}
        return "";
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
            return "ExtendedAE扩展样板管理终端";
        } else if (getPatternAccessMenu(player) != null) {
            return "AE2样板访问终端";
        } else {
            return "未知终端类型";
        }
    }

    /**
     * 从 AE2 的图样编码终端菜单上传当前“已编码图样”至当前网络中任意可用的样板供应器。
     * 策略：
     * 1) 仅当 encoded 槽位存在有效编码样板时执行；
     * 2) 通过 menu.getNetworkNode() 获取 IGrid，遍历在线的 PatternContainer；
     * 3) 仅选择在终端中可见（isVisibleInTerminal）且库存存在空位的供应器；
     * 4) 使用 AE2 的标准 FilteredInternalInventory + Pattern 过滤器尝试插入；
     * 5) 成功后清空 encoded 槽位，返回 true；否则返回 false。
     */
    public static boolean uploadFromEncodingMenuToAnyProvider(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu == null) {
            return false;
        }
        // 读取已编码槽位的物品（通过 accessor）
        var encodedSlot = ((PatternEncodingTermMenuAccessor) menu)
                .eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        // 获取 AE 网络（1.21 经由 AEBaseMenu target + IActionHost）
        IGrid grid = null;
        try {
            if (menu instanceof AEBaseMenu abm) {
                Object target = abm.getTarget();
                if (target instanceof IActionHost host && host.getActionableNode() != null) {
                    grid = host.getActionableNode().getGrid();
                }
            }
        } catch (Throwable ignored) {}
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
                        for (int i = 0; i < inv.size(); i++) {
                            if (inv.getStackInSlot(i).isEmpty()) {
                                hasEmpty = true;
                                break;
                            }
                        }
                        if (!hasEmpty) {
                            continue;
                        }

                        // 按 AE2 样板过滤规则尝试插入
                        var filtered = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter());
                        ItemStack toInsert = stack.copy();
                        ItemStack remain = filtered.addItems(toInsert);
                        if (remain.getCount() < toInsert.getCount()) {
                            int inserted = toInsert.getCount() - remain.getCount();
                            stack.shrink(inserted);
                            if (stack.isEmpty()) {
                                encodedSlot.set(ItemStack.EMPTY);
                            } else {
                                encodedSlot.set(stack);
                            }
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
    public static boolean uploadFromEncodingMenuToProvider(ServerPlayer player, PatternEncodingTermMenu menu, long providerId) {
        if (player == null || menu == null) {
            return false;
        }
        var encodedSlot = ((PatternEncodingTermMenuAccessor) (Object) menu)
                .eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        PatternAccessTermMenu accessMenu = getPatternAccessMenu(player);
        if (accessMenu == null) {
            return false;
        }
        // 先确定目标容器名称，用于同名回退
        String targetName = getProviderDisplayName(providerId, accessMenu);
        // 构建尝试顺序：先指定ID，其次同名的其他ID
        java.util.List<Long> tryIds = new java.util.ArrayList<>();
        tryIds.add(providerId);
        try {
            java.util.List<Long> all = getAllProviderIds(accessMenu);
            for (Long id : all) {
                if (id == null || id == providerId) continue;
                String name = getProviderDisplayName(id, accessMenu);
                if (name != null && name.equals(targetName)) {
                    tryIds.add(id);
                }
            }
        } catch (Throwable ignored) {}

        // 按顺序逐个尝试插入
        for (Long id : tryIds) {
            PatternContainer c = getPatternContainerById(accessMenu, id);
            if (c == null || !c.isVisibleInTerminal()) continue;
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;

            var filtered = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter());
            ItemStack toInsert = stack.copy();
            ItemStack remain = filtered.addItems(toInsert);
            if (remain.getCount() < toInsert.getCount()) {
                int inserted = toInsert.getCount() - remain.getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    encodedSlot.set(ItemStack.EMPTY);
                } else {
                    encodedSlot.set(stack);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 列出当前菜单中所有供应器的服务器ID（原样返回 byId 的 key 集合）。
     */
    public static java.util.List<Long> getAllProviderIds(PatternAccessTermMenu menu) {
        java.util.List<Long> result = new java.util.ArrayList<>();
        if (menu == null) return result;
        try {
            java.lang.reflect.Field byIdField = findByIdField(menu.getClass());
            if (byIdField == null) return result;
            byIdField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Object> byId = (java.util.Map<Long, Object>) byIdField.get(menu);
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
        List<PatternContainer> list = new ArrayList<>();
        if (menu == null) return list;
        try {
            IGrid grid = null;
            if (menu instanceof AEBaseMenu abm) {
                Object target = abm.getTarget();
                if (target instanceof IActionHost host && host.getActionableNode() != null) {
                    grid = host.getActionableNode().getGrid();
                }
            }
            if (grid == null) return list;
            for (var machineClass : grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
                    for (var container : grid.getActiveMachines(containerClass)) {
                        if (container == null || !container.isVisibleInTerminal()) continue;
                        InternalInventory inv = container.getTerminalPatternInventory();
                        if (inv == null || inv.size() <= 0) continue;
                        boolean hasEmpty = false;
                        for (int i = 0; i < inv.size(); i++) {
                            if (inv.getStackInSlot(i).isEmpty()) {
                                hasEmpty = true;
                                break;
                            }
                        }
                        if (hasEmpty) list.add(container);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** 计算供应器空槽位数量 */
    public static int getAvailableSlots(PatternContainer container) {
        if (container == null) return -1;
        InternalInventory inv = container.getTerminalPatternInventory();
        if (inv == null) return -1;
        int available = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) available++;
        }
        return available;
    }

    /**
     * 基于“索引”的定向上传：使用 listAvailableProvidersFromGrid(menu) 的顺序，
     * 将编码槽样板插入到第 index 个供应器。
     */
    public static boolean uploadFromEncodingMenuToProviderByIndex(ServerPlayer player, PatternEncodingTermMenu menu, int index) {
        if (player == null || menu == null || index < 0) return false;
        List<PatternContainer> list = listAvailableProvidersFromGrid(menu);
        if (index >= list.size()) return false;
        var container = list.get(index);
        if (container == null) return false;

        var encodedSlot = ((PatternEncodingTermMenuAccessor) (Object) menu)
                .eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        // 以名称为键，同名供应器依次尝试：先 index 指定的，再同名的其他
        String targetName = getProviderDisplayName(container);
        java.util.List<PatternContainer> tryList = new java.util.ArrayList<>();
        tryList.add(container);
        try {
            for (PatternContainer c : list) {
                if (c == null || c == container) continue;
                String name = getProviderDisplayName(c);
                if (name != null && name.equals(targetName)) {
                    tryList.add(c);
                }
            }
        } catch (Throwable ignored) {}

        for (PatternContainer c : tryList) {
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;
            var filtered = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter());
            ItemStack toInsert = stack.copy();
            ItemStack remain = filtered.addItems(toInsert);
            if (remain.getCount() < toInsert.getCount()) {
                int inserted = toInsert.getCount() - remain.getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    encodedSlot.set(ItemStack.EMPTY);
                } else {
                    encodedSlot.set(stack);
                }
                return true;
            }
        }
        return false;
    }
}
