package com.extendedae_plus.util.storage;

public interface InfinityConstants {
    // 当前磁盘格式版本号，增加字段用于向后/向前兼容
    int FORMAT_VERSION = 2;
    // 存储磁盘数据的格式版本号
    String FORMAT_VERSION_FIELD = "infinity_format_version";

    // savedData 文件名常量
    String SAVE_FILE_NAME = "infinity_biginteger_cells";

    // 磁盘的唯一标识符键名，存储在 ItemStack 和 InfinityStorageManager 中
    String INFINITY_CELL_UUID = "infinity_cell_uuid";
    // 单个磁盘的 InfinityDataStorage 数据键名
    String INFINITY_CELL_DATA = "infinity_cell_data";
    // 所有磁盘数据的列表键名，存储在 InfinityStorageManager 的 NBT 中
    String INFINITY_CELL_LIST = "infinity_cell_list";

    // 磁盘中所有物品键的键名（ListTag of CompoundTag）
    String INFINITY_CELL_KEYS = "infinity_cell_keys";
    // 磁盘中每种物品数量的键名（ListTag of CompoundTag，包含 "value"）
    String INFINITY_CELL_AMOUNTS = "infinity_cell_amounts";
    // 磁盘中所有物品的总数键名（ListTag，包含一个 CompoundTag 的 "value"）
    String INFINITY_CELL_ITEM_COUNT = "infinity_cell_item_count";

    // ItemStack 的 NBT 中存储总物品数量的键名
    String INFINITY_ITEM_TOTAL = "infinity_item_total";
    // ItemStack 的 NBT 中存储物品种类数量的键名
    String INFINITY_ITEM_TYPES = "infinity_item_types";
}
