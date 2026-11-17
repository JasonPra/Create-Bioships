package org.Azkerash.create_bioships.disabled.wip;

import org.Azkerash.create_bioships.CarapaceBlock;
import org.Azkerash.create_bioships.registry.Create_bioships;
import org.Azkerash.create_bioships.OneWayWindowBlock;
import org.Azkerash.create_bioships.BioshipTissue;

import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** An oval shell with a forward cockpit and a separate aft engine/workshop room. */
public final class DinghyGrowth {
  public static final int LAYOUT_VERSION = 6;
  public static final BlockPos HELM = new BlockPos(0, 2, -6),
      THROTTLE = new BlockPos(-1, 2, -4),
      VERTICAL_TRIM = new BlockPos(1, 2, -4),
      HUNT = new BlockPos(2, 2, -6),
      ACCESS_PANEL = new BlockPos(-2, 2, -6);
  public static final BlockPos BLUEPRINT_PANEL = new BlockPos(-2, 3, 0);
  public static final BlockPos DOOR = new BlockPos(0, 2, 6),
      MOUTH = new BlockPos(0, 0, -9),
      LARYNX = new BlockPos(0, 5, 4);
  public static final List<BlockPos> ACCESS_MEMBRANES =
      List.of(DOOR, DOOR.above(), new BlockPos(0, 2, 0), new BlockPos(0, 3, 0));
  public static final List<BlockPos> CABIN_LIGHTS =
      List.of(
          new BlockPos(-2, 5, -5),
          new BlockPos(2, 5, -5),
          new BlockPos(-2, 5, 3),
          new BlockPos(2, 5, 3));
  public static final List<BlockPos> FLAGELLA =
      List.of(
          new BlockPos(-4, 0, -5),
          new BlockPos(-4, 0, 0),
          new BlockPos(-4, 0, 3),
          new BlockPos(4, 0, -5),
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

  public static final BlockPos PERCH = new BlockPos(0, 2, -3);

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
    return radius(z, LAYOUT_VERSION);
  }

  public static int radius(int z, int version) {
    int profile = version >= 6 && z < 0 ? Math.min(0, z + 2) : z;
    return switch (Math.abs(profile)) {
      case 9, 8 -> 0;
      case 7 -> 1;
      case 6 -> 2;
      case 5, 4 -> 3;
      default -> 4;
    };
  }

  public static BlockPos atVersion(BlockPos offset, int version) {
    if (version < 6
        && (List.of(HELM, THROTTLE, VERTICAL_TRIM, HUNT, ACCESS_PANEL, MOUTH, PERCH)
                .contains(offset)
            || CABIN_LIGHTS.subList(0, 2).contains(offset)
            || offset.equals(FLAGELLA.get(0))
            || offset.equals(FLAGELLA.get(3)))) return offset.south(2);
    return offset;
  }

  public static List<Part> body() {
    return body(LAYOUT_VERSION);
  }

  public static List<Part> body(int version) {
    return version >= 6 ? Layout.BODY : Layout.LEGACY_BODY;
  }

  private static final class Layout {
    static final List<Part> BODY = createBody(6), LEGACY_BODY = createBody(5);
  }

  private static List<Part> createBody(int version) {
    boolean extended = version >= 6;
    Map<BlockPos, Part> parts = new LinkedHashMap<>();
    var flesh = BioshipTissue.body().defaultBlockState();
    java.util.function.BiConsumer<BlockPos, BlockState> put =
        (pos, state) -> {
          if (!pos.equals(BlockPos.ZERO)) parts.put(pos, new Part(pos, state));
        };
    for (int z = extended ? -11 : -9; z <= 9; z++) {
      int r = radius(z, version);
      for (int x = -r; x <= r; x++)
        put.accept(
            new BlockPos(x, 1, z),
            Math.abs(x) == r && r > 0 ? shell(x < 0 ? Direction.WEST : Direction.EAST) : flesh);
      if (z >= (extended ? -10 : -8) && z <= 8) put.accept(new BlockPos(0, 0, z), flesh);
    }
    for (int z = extended ? -8 : -6; z <= 6; z++) {
      int width = z <= (extended ? -7 : -5) || z >= 5 ? 2 : 3;
      for (int y = 2; y <= 6; y++) {
        int inner = Math.max(0, width - Math.max(0, y - 4));
        int previous = y <= 4 ? inner : inner + 1;
        for (int x = -previous; x <= previous; x++) {
          boolean cap = z == (extended ? -8 : -6) || z == 6,
              boundary = Math.abs(x) >= inner || cap || y == 6;
          if (!boundary) continue;
          if (z == 6 && x == 0 && y <= 3) continue;
          boolean window =
              y == 3
                  && (cap
                      ? z == (extended ? -8 : -6) && Math.abs(x) < width
                      : Math.abs(x) == width && z >= (extended ? -6 : -4) && z <= 4);
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
    for (int z = extended ? -6 : -4; z <= 4; z++)
      put.accept(new BlockPos(0, 7, z), shell(z <= 0 ? Direction.NORTH : Direction.SOUTH));
    for (var root : FLAGELLA)
      put.accept(atVersion(root, version), Create_bioships.TENTACLE.get().defaultBlockState());
    for (var organ : BUOYANCY_ORGANS) put.accept(organ, flesh);
    put.accept(atVersion(PERCH, version), Create_bioships.PERCH.get().defaultBlockState());
    put.accept(atVersion(HELM, version), helm());
    put.accept(atVersion(THROTTLE, version), Create_bioships.THROTTLE.get().defaultBlockState());
    put.accept(
        atVersion(VERTICAL_TRIM, version), Create_bioships.VERTICAL_TRIM.get().defaultBlockState());
    put.accept(
        atVersion(HUNT, version),
        Create_bioships.CONTROLS.stream()
            .filter(c -> c.get().command == NerveControlBlock.Command.HUNT)
            .findFirst()
            .orElseThrow()
            .get()
            .defaultBlockState());
    put.accept(
        atVersion(ACCESS_PANEL, version), Create_bioships.ACCESS_PANEL.get().defaultBlockState());
    var barrier =
        BuiltInRegistries.BLOCK
            .get(ResourceLocation.parse("biomancy:biometric_membrane"))
            .defaultBlockState();
    for (var pos : ACCESS_MEMBRANES) put.accept(pos, barrier);
    for (var light : CABIN_LIGHTS) put.accept(atVersion(light, version), cabinLantern());
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
    var lights = CABIN_LIGHTS.stream().map(p -> atVersion(p, version)).toList();
    connected.sort(Comparator.comparing(part -> lights.contains(part.offset())));
    return List.copyOf(connected);
  }

  private static BlockState shell(Direction facing) {
    return Create_bioships.CARAPACE
        .get()
        .defaultBlockState()
        .setValue(CarapaceBlock.FACING, facing);
  }
}
