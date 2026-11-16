package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** World-space exposed surface samples shared by cosmetic effects. */
final class BioshipParticleSurface {
  static List<Vec3> points(LivingCoreBlockEntity core) {
    var result = new ArrayList<Vec3>();
    if (core.ship() == null || core.getLevel() == null) return result;
    for (var cell : BioshipDecay.blocks(core.ship()))
      for (var face : Direction.values())
        if (core.getLevel().getBlockState(cell.relative(face)).isAir())
          result.add(
              core.ship()
                  .logicalPose()
                  .transformPosition(
                      Vec3.atCenterOf(cell)
                          .add(Vec3.atLowerCornerOf(face.getNormal()).scale(.62))));
    return result;
  }
}
