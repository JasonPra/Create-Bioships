package org.Azkerash.create_bioships;

import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** An oval shell with a forward cockpit and a separate aft engine/workshop room. */
public final class DinghyGrowth {
  public static final int LAYOUT_VERSION = 5;
  public static final BlockPos HELM = new BlockPos(0, 2, -4),
      THROTTLE = new BlockPos(-1, 2, -2),
      VERTICAL_TRIM = new BlockPos(1, 2, -2),
      HUNT = new BlockPos(2, 2, -4),
      ACCESS_PANEL = new BlockPos(-2, 2, -4);
  public static final BlockPos DOOR = new BlockPos(0, 2, 6),
      MOUTH = new BlockPos(0, 0, -7),
      LARYNX = new BlockPos(0, 5, 4);
  public static final List<BlockPos> ACCESS_MEMBRANES =
      List.of(DOOR, DOOR.above(), new BlockPos(0, 2, 0), new BlockPos(0, 3, 0));
  public static final List<BlockPos> CABIN_LIGHTS =
      List.of(
          new BlockPos(-2, 5, -3),
          new BlockPos(2, 5, -3),
          new BlockPos(-2, 5, 3),
          new BlockPos(2, 5, 3));
  public static final List<BlockPos> FLAGELLA =
      List.of(
          new BlockPos(-4, 0, -3),
          new BlockPos(-4, 0, 0),
          new BlockPos(-4, 0, 3),
          new BlockPos(4, 0, -3),
          new BlockPos(4, 0, 0),
          new BlockPos(4, 0, 3));
  public static final List<BlockPos> BUOYANCY_ORGANS =
      List.of(
          new BlockPos(-1, 0, -2),
          new BlockPos(1, 0, -2),
          new BlockPos(-1, 0, -1),
          new BlockPos(1, 0, -1),
          new BlockPos(-1, 0, 0),
          new BlockPos(1, 0, 0),
          new BlockPos(-1, 0, 1),
          new BlockPos(1, 0, 1));

  public record Part(BlockPos offset, BlockState state) {}

  public static BlockState cabinLantern() {
    return BuiltInRegistries.BLOCK
        .get(ResourceLocation.parse("biomancy:bio_lantern_yellow"))
        .defaultBlockState()
        .setValue(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING, true);
  }

  public static BlockState helm() {
    return BuiltInRegistries.BLOCK
        .get(ResourceLocation.parse("simulated:steering_wheel"))
        .defaultBlockState()
        .setValue(SteeringWheelBlock.FACING, Direction.SOUTH)
        .setValue(SteeringWheelBlock.ON_FLOOR, true);
  }

  public static int radius(int z) {
    return switch (Math.abs(z)) {
      case 9, 8 -> 0;
      case 7 -> 1;
      case 6 -> 2;
      case 5, 4 -> 3;
      default -> 4;
    };
  }

  public static List<Part> body() {
    return Layout.BODY;
  }

  private static final class Layout {
    static final List<Part> BODY = createBody();
  }

  private static List<Part> createBody() {
    Map<BlockPos, Part> parts = new LinkedHashMap<>();
    var flesh = Create_bioships.FLESH.get().defaultBlockState();
    java.util.function.BiConsumer<BlockPos, BlockState> put =
        (pos, state) -> {
          if (!pos.equals(BlockPos.ZERO)) parts.put(pos, new Part(pos, state));
        };
    for (int z = -9; z <= 9; z++) {
      int r = radius(z);
      for (int x = -r; x <= r; x++)
        put.accept(
            new BlockPos(x, 1, z),
            Math.abs(x) == r && r > 0 ? shell(x < 0 ? Direction.WEST : Direction.EAST) : flesh);
      if (Math.abs(z) <= 8) put.accept(new BlockPos(0, 0, z), flesh);
    }
    for (int z = -6; z <= 6; z++) {
      int width = Math.abs(z) >= 5 ? 2 : 3;
      for (int y = 2; y <= 6; y++) {
        int inner = Math.max(0, width - Math.max(0, y - 4));
        int previous = y <= 4 ? inner : inner + 1;
        for (int x = -previous; x <= previous; x++) {
          boolean cap = Math.abs(z) == 6, boundary = Math.abs(x) >= inner || cap || y == 6;
          if (!boundary) continue;
          if (z == 6 && x == 0 && y <= 3) continue;
          boolean window =
              y == 3
                  && (cap
                      ? z == -6 && Math.abs(x) < width
                      : Math.abs(x) == width && Math.abs(z) <= 4);
          BlockState state =
              window
                  ? Create_bioships.ONE_WAY_WINDOW
                      .get()
                      .defaultBlockState()
                      .setValue(
                          OneWayWindowBlock.FACING,
                          cap ? Direction.NORTH : x < 0 ? Direction.WEST : Direction.EAST)
                  : y >= 5 && x != 0 ? shell(x < 0 ? Direction.WEST : Direction.EAST) : flesh;
          put.accept(new BlockPos(x, y, z), state);
        }
      }
    }
    for (int x = -3; x <= 3; x++)
      for (int y = 2; y <= 5; y++) if (x != 0 || y > 3) put.accept(new BlockPos(x, y, 0), flesh);
    for (int z = -4; z <= 4; z++)
      put.accept(new BlockPos(0, 7, z), shell(z <= 0 ? Direction.NORTH : Direction.SOUTH));
    for (var root : FLAGELLA) put.accept(root, Create_bioships.TENTACLE.get().defaultBlockState());
    for (var organ : BUOYANCY_ORGANS) put.accept(organ, flesh);
    put.accept(new BlockPos(0, 2, -1), Create_bioships.PERCH.get().defaultBlockState());
    put.accept(HELM, helm());
    put.accept(THROTTLE, Create_bioships.THROTTLE.get().defaultBlockState());
    put.accept(VERTICAL_TRIM, Create_bioships.VERTICAL_TRIM.get().defaultBlockState());
    put.accept(
        HUNT,
        Create_bioships.CONTROLS.stream()
            .filter(c -> c.get().command == NerveControlBlock.Command.HUNT)
            .findFirst()
            .orElseThrow()
            .get()
            .defaultBlockState());
    put.accept(ACCESS_PANEL, Create_bioships.ACCESS_PANEL.get().defaultBlockState());
    var barrier =
        BuiltInRegistries.BLOCK
            .get(ResourceLocation.parse("biomancy:biometric_membrane"))
            .defaultBlockState();
    for (var pos : ACCESS_MEMBRANES) put.accept(pos, barrier);
    for (var light : CABIN_LIGHTS) put.accept(light, cabinLantern());
    put.accept(new BlockPos(0, 0, 8), flesh);
    put.accept(new BlockPos(0, 0, 9), flesh);
    put.accept(new BlockPos(0, 0, 10), flesh);
    List<Part> connected = new ArrayList<>();
    ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
    frontier.add(BlockPos.ZERO);
    while (!frontier.isEmpty()) {
      var point = frontier.removeFirst();
      for (var direction : Direction.values()) {
        var next = parts.remove(point.relative(direction));
        if (next != null) {
          connected.add(next);
          frontier.addLast(next.offset());
        }
      }
    }
    if (!parts.isEmpty()) throw new IllegalStateException("Detached body parts: " + parts.keySet());
    connected.sort(Comparator.comparing(part -> CABIN_LIGHTS.contains(part.offset())));
    return List.copyOf(connected);
  }

  private static BlockState shell(Direction facing) {
    return Create_bioships.CARAPACE
        .get()
        .defaultBlockState()
        .setValue(CarapaceBlock.FACING, facing);
  }
}
