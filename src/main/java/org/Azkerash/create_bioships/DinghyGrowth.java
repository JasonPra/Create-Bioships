package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A fixed first body: walkable deck, raised flanks, prow and six hanging tendrils. */
public final class DinghyGrowth {
    public record Part(BlockPos offset, BlockState state) {}
    public static List<Part> body() {
        List<Part> parts = new ArrayList<>();
        BlockState flesh = Create_bioships.FLESH.get().defaultBlockState();
        for (int z = -2; z <= 2; z++) for (int x = -1; x <= 1; x++) {
            if (x != 0 || z != 0) parts.add(new Part(new BlockPos(x, 0, z), flesh));
        }
        for (int z = -2; z <= 2; z++) for (int x : new int[]{-2, 2}) {
            parts.add(new Part(new BlockPos(x, 0, z), flesh));
            parts.add(new Part(new BlockPos(x, 1, z), flesh));
        }
        parts.add(new Part(new BlockPos(0, 0, -3), flesh));
        parts.add(new Part(new BlockPos(0, 1, -3), flesh));
        parts.add(new Part(new BlockPos(0, 0, 3), flesh));
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 0, 2}) for (int y = -1; y >= -3; y--)
            parts.add(new Part(new BlockPos(x, y, z), Create_bioships.TENTACLE.get().defaultBlockState()));
        parts.add(new Part(new BlockPos(0, 1, 1), Create_bioships.PERCH.get().defaultBlockState()));
        parts.add(new Part(new BlockPos(0, 1, -1), Create_bioships.HELM.get().defaultBlockState()));
        parts.add(new Part(new BlockPos(-1, 1, 1), Create_bioships.THROTTLE.get().defaultBlockState()));
        for (var control : Create_bioships.CONTROLS)
            parts.add(new Part(control.get().command.offset, control.get().defaultBlockState()));
        // Sable can split disconnected blocks into separate objects. Grow outward
        // from the core so every new piece is attached to the existing body.
        Map<BlockPos, Part> remaining = new LinkedHashMap<>();
        for (Part part : parts) remaining.put(part.offset(), part);
        List<Part> connected = new ArrayList<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(BlockPos.ZERO);
        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            for (var direction : net.minecraft.core.Direction.values()) {
                Part next = remaining.remove(current.relative(direction));
                if (next != null) { connected.add(next); frontier.addLast(next.offset()); }
            }
        }
        if (!remaining.isEmpty()) throw new IllegalStateException("Dinghy contains detached parts");
        return List.copyOf(connected);
    }
}
