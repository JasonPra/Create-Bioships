package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock;

/** Tapered living hull with a vaulted ribbed cabin, dermal windows, and a rear valve door. */
public final class DinghyGrowth {
    public static final int LAYOUT_VERSION = 4;
    public static final BlockPos HELM = new BlockPos(0, 2, -2);
    public static final BlockPos THROTTLE = new BlockPos(-1, 2, 0);
    public static final BlockPos VERTICAL_TRIM = new BlockPos(1, 2, 0);
    public static final BlockPos DOOR = new BlockPos(0, 2, 3);
    public static final BlockPos HUNT = new BlockPos(1, 2, -2);
    public static final BlockPos MOUTH = new BlockPos(0, 0, -6);
    public static final List<BlockPos> FLAGELLA = List.of(new BlockPos(-4, 0, -2), new BlockPos(-4, 0, 0), new BlockPos(-4, 0, 2),
            new BlockPos(4, 0, -2), new BlockPos(4, 0, 0), new BlockPos(4, 0, 2));
    public static final List<BlockPos> BUOYANCY_ORGANS = List.of(new BlockPos(-1, 0, -2), new BlockPos(1, 0, -2),
            new BlockPos(-1, 0, -1), new BlockPos(1, 0, -1), new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0),
            new BlockPos(-1, 0, 1), new BlockPos(1, 0, 1));
    public record Part(BlockPos offset, BlockState state) {}
    public static BlockState helm() {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulated", "steering_wheel")).defaultBlockState()
                .setValue(SteeringWheelBlock.FACING, Direction.SOUTH).setValue(SteeringWheelBlock.ON_FLOOR, true);
    }
    public static int radius(int z) {
        return switch (Math.abs(z)) { case 6 -> 0; case 5 -> 1; case 4 -> 2; case 3 -> 3; default -> 4; };
    }
    public static List<Part> body() { return Layout.BODY; }
    private static final class Layout { static final List<Part> BODY = createBody(); }
    private static List<Part> createBody() {
        Map<BlockPos, Part> parts = new LinkedHashMap<>();
        BlockState flesh = Create_bioships.FLESH.get().defaultBlockState();
        BlockState membrane = Create_bioships.MEMBRANE.get().defaultBlockState();
        java.util.function.BiConsumer<BlockPos, BlockState> put = (pos, state) -> { BlockPos raised = pos.above(); if (!raised.equals(BlockPos.ZERO)) parts.put(raised, new Part(raised, state)); };
        for (int z = -6; z <= 6; z++) {
            int radius = radius(z);
            for (int x = -radius; x <= radius; x++) {
                Direction edge = Math.abs(x) == radius && radius > 0 ? x < 0 ? Direction.WEST : Direction.EAST : z < 0 ? Direction.NORTH : Direction.SOUTH;
                put.accept(new BlockPos(x, 0, z), Math.abs(x) == radius ? shell(edge) : flesh);
            }
            if (Math.abs(z) <= 5) put.accept(new BlockPos(0, -1, z), flesh);
        }
        for (int z = -3; z <= 3; z++) {
            int lowerWidth = Math.abs(z) == 3 ? 2 : 3;
            for (int y = 1; y <= 4; y++) {
                int width = Math.max(0, lowerWidth - Math.max(0, y - 2));
                boolean cap = Math.abs(z) == 3;
                int previous = y <= 2 ? width : width + 1;
                for (int x = -previous; x <= previous; x++) {
                    boolean boundary = Math.abs(x) >= width || cap || y == 4;
                    if (!boundary) continue;
                    if (z == 3 && x == 0 && y <= 2) continue;
                    boolean window = y == 2 && (cap ? z == -3 && Math.abs(x) < lowerWidth : Math.abs(x) == lowerWidth && Math.abs(z) < 3);
                    BlockState state = window ? membrane : y >= 3 && x != 0 ? shell(x < 0 ? Direction.WEST : Direction.EAST) : flesh;
                    put.accept(new BlockPos(x, y, z), state);
                }
            }
        }
        // Raised dorsal ribs, asymmetric shoulder lobes, and a ventral keel break up the silhouette.
        for (int z = -2; z <= 2; z++) put.accept(new BlockPos(0, 5, z), shell(z <= 0 ? Direction.NORTH : Direction.SOUTH));
        for (int z = -1; z <= 1; z++) put.accept(new BlockPos(-4, 1, z), shell(Direction.WEST));
        for (int z = 0; z <= 1; z++) put.accept(new BlockPos(4, 1, z), shell(Direction.EAST));
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) put.accept(new BlockPos(x, 3, z), Create_bioships.PULSE_LIGHT.get().defaultBlockState());
        for (BlockPos root : FLAGELLA) put.accept(root.below(), Create_bioships.TENTACLE.get().defaultBlockState());
        put.accept(new BlockPos(0, 1, 1), Create_bioships.PERCH.get().defaultBlockState());
        put.accept(HELM.below(), helm()); put.accept(THROTTLE.below(), Create_bioships.THROTTLE.get().defaultBlockState());
        put.accept(VERTICAL_TRIM.below(), Create_bioships.VERTICAL_TRIM.get().defaultBlockState());
        put.accept(HUNT.below(), Create_bioships.CONTROLS.stream().filter(c -> c.get().command == NerveControlBlock.Command.HUNT).findFirst().orElseThrow().get().defaultBlockState());
        BlockState door = Create_bioships.DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        put.accept(DOOR.below(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put.accept(DOOR, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (BlockPos organ : BUOYANCY_ORGANS) put.accept(organ.below(), flesh);
        put.accept(new BlockPos(0, -1, 6), flesh);
        put.accept(new BlockPos(0, -1, 7), flesh);
        List<Part> connected = new ArrayList<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>(); frontier.add(BlockPos.ZERO);
        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            for (Direction direction : Direction.values()) {
                Part next = parts.remove(current.relative(direction));
                if (next != null) { connected.add(next); frontier.addLast(next.offset()); }
            }
        }
        if (!parts.isEmpty()) throw new IllegalStateException("Detached body parts: " + parts.keySet());
        return List.copyOf(connected);
    }
    private static BlockState shell(Direction facing) { return Create_bioships.CARAPACE.get().defaultBlockState().setValue(CarapaceBlock.FACING, facing); }
}
