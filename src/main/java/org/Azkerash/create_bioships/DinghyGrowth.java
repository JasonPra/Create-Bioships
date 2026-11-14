package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock;

/** A connected 9-by-11 body with a roofed cabin and functional flagellum roots. */
public final class DinghyGrowth {
    public static final BlockPos HELM = new BlockPos(0, 1, -2);
    public static final BlockPos THROTTLE = new BlockPos(-1, 1, 0);
    public static final BlockPos MOUTH = new BlockPos(0, -1, -5);
    public static final List<BlockPos> FLAGELLA = List.of(new BlockPos(-4, -1, -2), new BlockPos(-4, -1, 0), new BlockPos(-4, -1, 2),
            new BlockPos(4, -1, -2), new BlockPos(4, -1, 0), new BlockPos(4, -1, 2));
    public record Part(BlockPos offset, BlockState state) {}
    public static BlockState helm() {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulated", "steering_wheel")).defaultBlockState()
                .setValue(SteeringWheelBlock.FACING, Direction.SOUTH).setValue(SteeringWheelBlock.ON_FLOOR, true);
    }
    public static List<Part> body() { return Layout.BODY; }
    private static final class Layout { static final List<Part> BODY = createBody(); }
    private static List<Part> createBody() {
        Map<BlockPos, Part> parts = new LinkedHashMap<>();
        BlockState flesh = Create_bioships.FLESH.get().defaultBlockState();
        java.util.function.BiConsumer<BlockPos, BlockState> put = (pos, state) -> { if (!pos.equals(BlockPos.ZERO)) parts.put(pos, new Part(pos, state)); };
        for (int z = -4; z <= 4; z++) for (int x = -3; x <= 3; x++) put.accept(new BlockPos(x, 0, z), flesh);
        for (int z = -3; z <= 3; z++) for (int x : new int[]{-4, 4}) put.accept(new BlockPos(x, 0, z), flesh);
        for (int x = -1; x <= 1; x++) put.accept(new BlockPos(x, 0, -5), flesh);
        for (int x = -2; x <= 2; x++) put.accept(new BlockPos(x, 0, 5), flesh);
        // Enclosed cabin with a two-block-high rear entrance and a generous clear aisle.
        for (int y = 1; y <= 3; y++) {
            for (int z = -3; z <= 3; z++) for (int x : new int[]{-3, 3})
                put.accept(new BlockPos(x, y, z), y >= 2 && Math.abs(z) < 3 ? Blocks.PURPLE_STAINED_GLASS.defaultBlockState() : flesh);
            for (int x = -2; x <= 2; x++) {
                put.accept(new BlockPos(x, y, -3), y >= 2 ? Blocks.PURPLE_STAINED_GLASS.defaultBlockState() : flesh);
                if (x != 0 || y == 3) put.accept(new BlockPos(x, y, 3), flesh);
            }
        }
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            put.accept(new BlockPos(x, 4, z), Math.abs(x) <= 1 && Math.abs(z) <= 1 ? Blocks.PURPLE_STAINED_GLASS.defaultBlockState() : flesh);
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2})
            put.accept(new BlockPos(x, 3, z), Create_bioships.PULSE_LIGHT.get().defaultBlockState());
        for (BlockPos root : FLAGELLA) put.accept(root, Create_bioships.TENTACLE.get().defaultBlockState());
        put.accept(new BlockPos(0, 1, 1), Create_bioships.PERCH.get().defaultBlockState());
        put.accept(HELM, helm());
        put.accept(THROTTLE, Create_bioships.THROTTLE.get().defaultBlockState());
        for (var control : Create_bioships.CONTROLS) {
            if (control.get().command == NerveControlBlock.Command.RISE) put.accept(new BlockPos(2, 1, -1), control.get().defaultBlockState());
            if (control.get().command == NerveControlBlock.Command.DIVE) put.accept(new BlockPos(2, 1, 0), control.get().defaultBlockState());
        }
        List<Part> connected = new ArrayList<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>(); frontier.add(BlockPos.ZERO);
        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            for (Direction direction : Direction.values()) {
                Part next = parts.remove(current.relative(direction));
                if (next != null) { connected.add(next); frontier.addLast(next.offset()); }
            }
        }
        if (!parts.isEmpty()) throw new IllegalStateException("Body contains detached parts: " + parts.keySet());
        return List.copyOf(connected);
    }
}
