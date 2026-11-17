package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import org.Azkerash.create_bioships.creature.digestion.StomachFormation;

/**
 * Derived visual roles and outward faces. Owns no inventory and spends no construction resources.
 */
public final class StomachPorts {
  private record Port(BlockPos pos, Direction face) {}

  public static void refresh(LivingCoreBlockEntity core) {
    var roles = new HashMap<BlockPos, StomachBlock.Role>();
    var faces = new HashMap<BlockPos, Direction>();
    for (var cube : StomachMultiblock.formations(core)) {
      cube.members()
          .keySet()
          .forEach(p -> roles.put(StomachMultiblock.pos(p), StomachBlock.Role.MEMBER));
      var intake = intake(core, cube);
      var panel = panel(core, cube, intake.pos());
      roles.put(intake.pos(), StomachBlock.Role.INTAKE);
      faces.put(intake.pos(), intake.face());
      roles.put(panel.pos(), StomachBlock.Role.PANEL);
      faces.put(panel.pos(), panel.face());
    }
    for (var stomach : DigestiveSystem.stomachs(core)) {
      var p = stomach.getBlockPos();
      var state = core.getLevel().getBlockState(p);
      if (!(state.getBlock() instanceof StomachBlock)) continue;
      var updated =
          state.setValue(StomachBlock.ROLE, roles.getOrDefault(p, StomachBlock.Role.NORMAL));
      if (faces.containsKey(p)) updated = updated.setValue(StomachBlock.FACING, faces.get(p));
      if (!state.equals(updated)) core.getLevel().setBlock(p, updated, 3);
    }
  }

  private static Port intake(
      LivingCoreBlockEntity core, StomachFormation.Cube<StomachBlockEntity> cube) {
    // Keep the established tube endpoint as the visible port whenever its face is suitable.
    for (var mouth : DigestiveSystem.mouths(core))
      for (var route : DigestiveIntakes.routes(core).routes(StomachMultiblock.cell(mouth))) {
        var p = route.stomach().getBlockPos();
        if (!cube.contains(StomachMultiblock.cell(p))) continue;
        var previous =
            route.tubes().isEmpty() ? mouth : StomachMultiblock.pos(route.tubes().getLast());
        for (var face : Direction.values())
          if (face != Direction.DOWN
              && p.relative(face).equals(previous)
              && !cube.contains(StomachMultiblock.cell(previous))) return new Port(p, face);
      }
    return new Port(StomachMultiblock.pos(cube.origin()).offset(1, 2, 1), Direction.UP);
  }

  private static Port panel(
      LivingCoreBlockEntity core, StomachFormation.Cube<StomachBlockEntity> cube, BlockPos intake) {
    Port fallback = null;
    for (var face :
        List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP))
      for (var cell : new TreeSet<>(cube.members().keySet())) {
        var p = StomachMultiblock.pos(cell);
        if (p.equals(intake) || cube.contains(StomachMultiblock.cell(p.relative(face)))) continue;
        var port = new Port(p, face);
        if (fallback == null) fallback = port;
        if (core.getLevel().getBlockState(p.relative(face)).isAir()) return port;
      }
    return Objects.requireNonNull(fallback);
  }

  private StomachPorts() {}
}
