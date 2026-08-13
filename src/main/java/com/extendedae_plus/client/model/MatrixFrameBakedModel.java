package com.extendedae_plus.client.model;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModBlocks;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class MatrixFrameBakedModel implements IDynamicBakedModel {

    private static final ModelProperty<FrameConnections> CONNECTIONS = new ModelProperty<>();
    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.solid());

    private static final Material SINGLE_ON = material("single_on");
    private static final Material STRIP_ON = material("strip_on");
    private static final Material CORNER_ON = material("corner_on");
    private static final Material EDGE_ON = material("edge_on");
    private static final Material SINGLE_OFF = material("single_off");
    private static final Material STRIP_OFF = material("strip_off");
    private static final Material CORNER_OFF = material("corner_off");
    private static final Material EDGE_OFF = material("edge_off");
    private static final Material INTERIOR = material("interior");

    private final TextureSet poweredTextures;
    private final TextureSet unpoweredTextures;

    MatrixFrameBakedModel(Function<Material, TextureAtlasSprite> spriteGetter) {
        var interior = spriteGetter.apply(INTERIOR);
        this.poweredTextures = new TextureSet(
                spriteGetter.apply(SINGLE_ON),
                spriteGetter.apply(STRIP_ON),
                spriteGetter.apply(CORNER_ON),
                spriteGetter.apply(EDGE_ON),
                interior);
        this.unpoweredTextures = new TextureSet(
                spriteGetter.apply(SINGLE_OFF),
                spriteGetter.apply(STRIP_OFF),
                spriteGetter.apply(CORNER_OFF),
                spriteGetter.apply(EDGE_OFF),
                interior);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
            @NotNull BlockState state, @NotNull ModelData modelData) {
        var connections = new FrameConnections();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if ((x != 0 || y != 0 || z != 0)
                            && level.getBlockState(pos.offset(x, y, z))
                                    .is(ModBlocks.SUPER_ASSEMBLER_MATRIX_FRAME.get())) {
                        connections.set(x, y, z);
                    }
                }
            }
        }
        return modelData.derive().with(CONNECTIONS, connections).build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
            @NotNull RandomSource random, @NotNull ModelData modelData, @Nullable RenderType renderType) {
        if (side == null || renderType != null && renderType != RenderType.solid()) {
            return Collections.emptyList();
        }

        var connections = modelData.get(CONNECTIONS);
        if (connections == null) {
            return Collections.emptyList();
        }

        var face = FaceBasis.forSide(side);
        var selection = selectTexture(connections, face);
        var powered = state != null
                && state.hasProperty(BlockAssemblerMatrixBase.POWERED)
                && state.getValue(BlockAssemblerMatrixBase.POWERED);
        var textures = powered ? this.poweredTextures : this.unpoweredTextures;
        return bakeFace(side, face, textures.get(selection.type()), selection.rotation());
    }

    private static TextureSelection selectTexture(FrameConnections connections, FaceBasis face) {
        int connectedMask = 0;
        if (connections.contains(face.top())) {
            connectedMask |= 1;
        }
        if (connections.contains(face.right())) {
            connectedMask |= 2;
        }
        if (connections.contains(face.bottom())) {
            connectedMask |= 4;
        }
        if (connections.contains(face.left())) {
            connectedMask |= 8;
        }

        int count = Integer.bitCount(connectedMask);
        if (count <= 1) {
            // 单独方块和柱体端点保持原有 single 外观。
            return new TextureSelection(TextureType.SINGLE, 0);
        }
        if (count == 4) {
            return new TextureSelection(TextureType.INTERIOR, 0);
        }

        int borderMask = (~connectedMask) & 15;
        if (count == 3) {
            return new TextureSelection(TextureType.EDGE, rotationForSingleBorder(borderMask));
        }
        if (borderMask == 5 || borderMask == 10) {
            return new TextureSelection(TextureType.STRIP, borderMask == 10 ? 0 : 1);
        }

        var connectedSides = connectedCornerSides(connectedMask, face);
        if (connectedSides != null && !connections.contains(connectedSides.first(), connectedSides.second())) {
            // L 形柱体没有补齐 2×2 对角块时，拐点仍显示完整四边框。
            return new TextureSelection(TextureType.SINGLE, 0);
        }
        return new TextureSelection(TextureType.CORNER, rotationForCorner(borderMask));
    }

    private static DirectionPair connectedCornerSides(int connectedMask, FaceBasis face) {
        return switch (connectedMask) {
            case 3 -> new DirectionPair(face.top(), face.right());
            case 6 -> new DirectionPair(face.right(), face.bottom());
            case 12 -> new DirectionPair(face.bottom(), face.left());
            case 9 -> new DirectionPair(face.left(), face.top());
            default -> null;
        };
    }

    private static int rotationForSingleBorder(int borderMask) {
        return switch (borderMask) {
            case 1 -> 0;
            case 2 -> 1;
            case 4 -> 2;
            case 8 -> 3;
            default -> 0;
        };
    }

    private static int rotationForCorner(int borderMask) {
        return switch (borderMask) {
            case 9 -> 0;
            case 3 -> 1;
            case 6 -> 2;
            case 12 -> 3;
            default -> 0;
        };
    }

    private static List<BakedQuad> bakeFace(Direction side, FaceBasis face, TextureAtlasSprite sprite, int rotation) {
        var quads = new ArrayList<BakedQuad>(1);
        var builder = new QuadBakingVertexConsumer(quads::add);
        builder.setSprite(sprite);
        builder.setDirection(side);
        builder.setShade(true);

        var normal = side.getNormal();
        putVertex(builder, sprite, normal, face.topLeft(), rotateU(0, 0, rotation), rotateV(0, 0, rotation));
        putVertex(builder, sprite, normal, face.bottomLeft(), rotateU(0, 16, rotation), rotateV(0, 16, rotation));
        putVertex(builder, sprite, normal, face.bottomRight(), rotateU(16, 16, rotation), rotateV(16, 16, rotation));
        putVertex(builder, sprite, normal, face.topRight(), rotateU(16, 0, rotation), rotateV(16, 0, rotation));
        return quads;
    }

    private static float rotateU(float u, float v, int rotation) {
        return switch (rotation & 3) {
            case 1 -> v;
            case 2 -> 16 - u;
            case 3 -> 16 - v;
            default -> u;
        };
    }

    private static float rotateV(float u, float v, int rotation) {
        return switch (rotation & 3) {
            case 1 -> 16 - u;
            case 2 -> 16 - v;
            case 3 -> u;
            default -> v;
        };
    }

    private static void putVertex(QuadBakingVertexConsumer builder, TextureAtlasSprite sprite, Vec3i normal,
            Vector3f position, float u, float v) {
        builder.vertex(position.x(), position.y(), position.z());
        builder.color(1.0f, 1.0f, 1.0f, 1.0f);
        builder.normal(normal.getX(), normal.getY(), normal.getZ());
        builder.uv(sprite.getU(u), sprite.getV(v));
        builder.endVertex();
    }

    private static Material material(String name) {
        return new Material(InventoryMenu.BLOCK_ATLAS,
                ExtendedAEPlus.id("block/matrix_frame_connections/" + name));
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return this.poweredTextures.single();
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random,
            @NotNull ModelData modelData) {
        return RENDER_TYPES;
    }

    private enum TextureType {
        SINGLE,
        STRIP,
        CORNER,
        EDGE,
        INTERIOR
    }

    private record TextureSelection(TextureType type, int rotation) {
    }

    private record DirectionPair(Direction first, Direction second) {
    }

    private static final class FrameConnections {

        private final boolean[][][] connections = new boolean[3][3][3];

        void set(int x, int y, int z) {
            this.connections[x + 1][y + 1][z + 1] = true;
        }

        boolean contains(Direction direction) {
            return this.contains(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }

        boolean contains(Direction first, Direction second) {
            return this.contains(
                    first.getStepX() + second.getStepX(),
                    first.getStepY() + second.getStepY(),
                    first.getStepZ() + second.getStepZ());
        }

        private boolean contains(int x, int y, int z) {
            return this.connections[x + 1][y + 1][z + 1];
        }
    }

    private record TextureSet(TextureAtlasSprite single, TextureAtlasSprite strip, TextureAtlasSprite corner,
            TextureAtlasSprite edge, TextureAtlasSprite interior) {

        TextureAtlasSprite get(TextureType type) {
            return switch (type) {
                case SINGLE -> this.single;
                case STRIP -> this.strip;
                case CORNER -> this.corner;
                case EDGE -> this.edge;
                case INTERIOR -> this.interior;
            };
        }
    }

    private record FaceBasis(Direction top, Direction right, Direction bottom, Direction left,
            Vector3f topLeft, Vector3f bottomLeft, Vector3f bottomRight, Vector3f topRight) {

        static FaceBasis forSide(Direction side) {
            return switch (side) {
                case EAST -> new FaceBasis(Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH,
                        point(1, 1, 1), point(1, 0, 1), point(1, 0, 0), point(1, 1, 0));
                case WEST -> new FaceBasis(Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH,
                        point(0, 1, 0), point(0, 0, 0), point(0, 0, 1), point(0, 1, 1));
                case UP -> new FaceBasis(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST,
                        point(1, 1, 1), point(1, 1, 0), point(0, 1, 0), point(0, 1, 1));
                case DOWN -> new FaceBasis(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST,
                        point(0, 0, 1), point(0, 0, 0), point(1, 0, 0), point(1, 0, 1));
                case SOUTH -> new FaceBasis(Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST,
                        point(0, 1, 1), point(0, 0, 1), point(1, 0, 1), point(1, 1, 1));
                case NORTH -> new FaceBasis(Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST,
                        point(1, 1, 0), point(1, 0, 0), point(0, 0, 0), point(0, 1, 0));
            };
        }

        private static Vector3f point(float x, float y, float z) {
            return new Vector3f(x, y, z);
        }
    }
}
