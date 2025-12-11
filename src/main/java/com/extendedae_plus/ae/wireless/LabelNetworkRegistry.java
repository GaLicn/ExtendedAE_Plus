package com.extendedae_plus.ae.wireless;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.util.wireless.WirelessTeamUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.Registries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Comparator;

/**
 * 标签无线网络注册中心（SavedData）。
 * - 负责标签到频道（频率）的分配与复用；
 * - 创建/销毁虚拟节点，所有收发器连接到同一虚拟节点；
 * - 记录在线端点集合，端点卸载后需调用 unregister。
 *
 * UI 未实现，仅提供服务端逻辑。
 */
public class LabelNetworkRegistry extends SavedData {
    public static final String SAVE_ID = ExtendedAEPlus.MODID + "_label_networks";

    private static final long CHANNEL_START = 1_000_000L;

    private final Map<Key, LabelNetwork> networks = new HashMap<>();
    private long nextChannel = CHANNEL_START;

    /* 入口 API */

    public static LabelNetworkRegistry get(MinecraftServer server) {
        ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
        return level.getDataStorage().computeIfAbsent(LabelNetworkRegistry::load, LabelNetworkRegistry::new, SAVE_ID);
    }

    public record LabelNetworkSnapshot(String label, long channel) {}

    public static LabelNetworkRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    /**
     * 规范化标签：trim + toLowerCase。
     */
    public static String normalizeLabel(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        if (t.length() > 64) {
            t = t.substring(0, 64);
        }
        // 过滤非法字符
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                return null;
            }
        }
        return t;
    }

    /**
    * 注册/切换标签。
    */
    public synchronized LabelNetwork register(ServerLevel beLevel, String rawLabel, @Nullable UUID placerId, IWirelessEndpoint endpoint) {
        String label = normalizeLabel(rawLabel);
        if (label == null) return null;

        UUID owner = placerId == null ? WirelessMasterRegistry.PUBLIC_NETWORK_UUID : WirelessTeamUtil.getNetworkOwnerUUID(beLevel, placerId);
        ResourceKey<Level> dimKey = ModConfig.INSTANCE.wirelessCrossDimEnable ? null : beLevel.dimension();
        Key key = new Key(dimKey, label, owner);

        LabelNetwork network = networks.get(key);
        if (network == null) {
            long channel = allocateChannel();
            network = new LabelNetwork(dimKey, label, owner, channel);
            if (!network.ensureVirtualNode(beLevel)) {
                return null;
            }
            networks.put(key, network);
            setDirty();
        } else {
            // 确保虚拟节点存在
            network.ensureVirtualNode(beLevel);
        }

        network.endpoints.add(new EndpointRef(dimKey, endpoint.getBlockPos()));
        setDirty();
        return network;
    }

    /**
     * 注销端点；若网络无端点则清理。
     */
    public synchronized void unregister(IWirelessEndpoint endpoint) {
        ServerLevel level = endpoint.getServerLevel();
        if (level == null) return;
        ResourceKey<Level> dimKey = ModConfig.INSTANCE.wirelessCrossDimEnable ? null : level.dimension();
        BlockPos pos = endpoint.getBlockPos();
        Iterator<Map.Entry<Key, LabelNetwork>> it = networks.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            LabelNetwork net = entry.getValue();
            net.endpoints.removeIf(ref -> ref.matches(dimKey, pos));
            if (net.endpoints.isEmpty()) {
                net.destroyVirtualNode();
                it.remove();
                setDirty();
            }
        }
    }

    public synchronized LabelNetwork getNetwork(ServerLevel level, String rawLabel, @Nullable UUID placerId) {
        String label = normalizeLabel(rawLabel);
        if (label == null) return null;
        UUID owner = placerId == null ? WirelessMasterRegistry.PUBLIC_NETWORK_UUID : WirelessTeamUtil.getNetworkOwnerUUID(level, placerId);
        ResourceKey<Level> dimKey = ModConfig.INSTANCE.wirelessCrossDimEnable ? null : level.dimension();
        Key key = new Key(dimKey, label, owner);
        return networks.get(key);
    }

    /**
     * 获取当前玩家所属网络列表（按标签排序）。
     */
    public synchronized List<LabelNetworkSnapshot> listNetworks(ServerLevel level, @Nullable UUID placerId) {
        UUID owner = placerId == null ? WirelessMasterRegistry.PUBLIC_NETWORK_UUID : WirelessTeamUtil.getNetworkOwnerUUID(level, placerId);
        ResourceKey<Level> dimKey = ModConfig.INSTANCE.wirelessCrossDimEnable ? null : level.dimension();
        List<LabelNetworkSnapshot> list = new ArrayList<>();
        for (Map.Entry<Key, LabelNetwork> entry : networks.entrySet()) {
            Key key = entry.getKey();
            if (!Objects.equals(key.owner(), owner)) continue;
            if (!Objects.equals(key.dim(), dimKey)) continue;
            list.add(new LabelNetworkSnapshot(key.label(), entry.getValue().channel()));
        }
        list.sort(Comparator.comparing(LabelNetworkSnapshot::label));
        return list;
    }

    /* 序列化 */

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putLong("nextChannel", nextChannel);
        ListTag list = new ListTag();
        networks.forEach((k, v) -> {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("label", k.label());
            if (k.dim() != null) {
                nbt.putString("dim", k.dim().location().toString());
            }
            nbt.putUUID("owner", k.owner());
            nbt.putLong("channel", v.channel);
            nbt.put("endpoints", v.saveEndpoints());
            list.add(nbt);
        });
        tag.put("networks", list);
        return tag;
    }

    public static LabelNetworkRegistry load(CompoundTag tag) {
        LabelNetworkRegistry reg = new LabelNetworkRegistry();
        reg.nextChannel = tag.getLong("nextChannel");
        ListTag list = tag.getList("networks", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag nbt = list.getCompound(i);
            String label = nbt.getString("label");
            ResourceKey<Level> dim = nbt.contains("dim") ? ResourceKey.create(Registries.DIMENSION, new ResourceLocation(nbt.getString("dim"))) : null;
            UUID owner = nbt.getUUID("owner");
            long channel = nbt.getLong("channel");
            LabelNetwork net = new LabelNetwork(dim, label, owner, channel);
            net.loadEndpoints(nbt.getList("endpoints", CompoundTag.TAG_COMPOUND));
            reg.networks.put(new Key(dim, label, owner), net);
        }
        return reg;
    }

    private long allocateChannel() {
        return nextChannel++;
    }

    /* 内部类型 */

    public record Key(@Nullable ResourceKey<Level> dim, String label, UUID owner) {}

    public static class LabelNetwork {
        private final ResourceKey<Level> dim; // null 表示跨维共用
        private final String label;
        private final UUID owner;
        private final long channel;
        private final Set<EndpointRef> endpoints = new HashSet<>();

        @Nullable
        private IManagedGridNode managedNode;
        @Nullable
        private VirtualLabelNodeHost virtualHost;

        LabelNetwork(@Nullable ResourceKey<Level> dim, String label, UUID owner, long channel) {
            this.dim = dim;
            this.label = label;
            this.owner = owner;
            this.channel = channel;
        }

        public long channel() {
            return channel;
        }

        public ResourceKey<Level> dim() {
            return dim;
        }

        public @Nullable IGridNode node() {
            return managedNode == null ? null : managedNode.getNode();
        }

        /**
         * 确保虚拟节点存在；若已存在则复用。
         */
        public boolean ensureVirtualNode(ServerLevel level) {
            if (managedNode != null && managedNode.getNode() != null) {
                return true;
            }
            ServerLevel hostLevel = dim == null ? level.getServer().getLevel(ServerLevel.OVERWORLD) : level.getServer().getLevel(dim);
            if (hostLevel == null) return false;

            // 创建虚拟节点（非 in-world）
            this.virtualHost = new VirtualLabelNodeHost();
            this.managedNode = GridHelper.createManagedNode(virtualHost, NodeListener.INSTANCE);
            this.virtualHost.setManagedNode(this.managedNode);
            this.managedNode.setFlags(GridFlags.REQUIRE_CHANNEL);
            this.managedNode.setIdlePowerUsage(0.0);
            this.managedNode.setInWorldNode(false);
            this.managedNode.setVisualRepresentation(com.extendedae_plus.init.ModItems.LABELED_WIRELESS_TRANSCEIVER.get().getDefaultInstance());
            this.managedNode.setTagName("label_net_" + label);
            this.managedNode.create(hostLevel, null);
            return true;
        }

        public void destroyVirtualNode() {
            if (managedNode != null) {
                managedNode.destroy();
            }
            managedNode = null;
            virtualHost = null;
        }

        public ListTag saveEndpoints() {
            ListTag list = new ListTag();
            for (EndpointRef ref : endpoints) {
                list.add(ref.save());
            }
            return list;
        }

        public void loadEndpoints(ListTag list) {
            endpoints.clear();
            for (int i = 0; i < list.size(); i++) {
                endpoints.add(EndpointRef.load(list.getCompound(i)));
            }
        }
    }

    public record EndpointRef(@Nullable ResourceKey<Level> dim, BlockPos pos) {
        public boolean matches(@Nullable ResourceKey<Level> currentDim, BlockPos currentPos) {
            if (!Objects.equals(dim, currentDim)) return false;
            return pos.equals(currentPos);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (dim != null) {
                tag.putString("dim", dim.location().toString());
            }
            tag.putLong("pos", pos.asLong());
            return tag;
        }

        public static EndpointRef load(CompoundTag tag) {
            ResourceKey<Level> d = tag.contains("dim")
                    ? ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("dim")))
                    : null;
            BlockPos p = BlockPos.of(tag.getLong("pos"));
            return new EndpointRef(d, p);
        }
    }

    enum NodeListener implements IGridNodeListener<VirtualLabelNodeHost> {
        INSTANCE;

        @Override
        public void onSaveChanges(VirtualLabelNodeHost host, IGridNode node) {}

        @Override
        public void onStateChanged(VirtualLabelNodeHost host, IGridNode node, IGridNodeListener.State state) {}

        @Override
        public void onInWorldConnectionChanged(VirtualLabelNodeHost host, IGridNode node) {}

        @Override
        public void onGridChanged(VirtualLabelNodeHost host, IGridNode node) {}

        @Override
        public void onOwnerChanged(VirtualLabelNodeHost host, IGridNode node) {}
    }

    /**
     * 虚拟标签网络节点宿主，不在世界中放置实体。
     */
    static class VirtualLabelNodeHost implements IInWorldGridNodeHost {
        private IManagedGridNode managedNode;

        void setManagedNode(IManagedGridNode managedNode) {
            this.managedNode = managedNode;
        }

        @Override
        public @Nullable IGridNode getGridNode(@Nullable net.minecraft.core.Direction dir) {
            return managedNode == null ? null : managedNode.getNode();
        }
    }
}
