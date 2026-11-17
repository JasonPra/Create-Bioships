package org.Azkerash.create_bioships.development;

import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.BioshipStructure;
import org.Azkerash.create_bioships.BioshipIntegration;
import org.Azkerash.create_bioships.registry.Create_bioships;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Preset-owned starters and bounded cleanup; independent of the optional test observer. */
public final class LabStarters {
  private static final String MARKER = "BioshipEcosystemFixture";

  public static void seed(ServerLevel level, EcosystemLab.State state) {
    for (int n = 0; n < 2; n++) {
      BlockPos chosen = null;
      for (int d = 0; d < 64; d++) {
        int x = state.center.getX() - 20 + n * 40 + d % 8 * 3;
        int z = state.center.getZ() - 16 + d / 8 * 3;
        level.getChunk(x >> 4, z >> 4);
        var pos =
            level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (level.getFluidState(pos.below()).isEmpty()
            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
            && level.getBlockState(pos).canBeReplaced()) {
          chosen = pos;
          break;
        }
      }
      if (chosen == null) throw new IllegalStateException("No dry ecosystem starter site found");
      core(level, state, chosen);
      for (int i = 0; i < 3; i++) {
        var p = chosen.offset(i * 2 - 2, 0, -3);
        var pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
        component(level, state, pos, i);
      }
    }
    var water = AquaticLab.center(level).offset(-8, -2, 0);
    core(level, state, water);
    for (int i = 0; i < 3; i++) component(level, state, water.offset(i * 2 - 2, 0, -2), i);
    state.setDirty();
  }

  private static void component(
      ServerLevel level, EcosystemLab.State state, BlockPos pos, int index) {
    var name = List.of("bio_forge", "decomposer", "digester").get(index);
    level.setBlock(
        pos,
        BuiltInRegistries.BLOCK.get(ResourceLocation.parse("biomancy:" + name)).defaultBlockState(),
        3);
    state.starters.add(pos.immutable());
  }

  public static void core(ServerLevel level, EcosystemLab.State state, BlockPos pos) {
    level.setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    BioshipIntegration.awaken(level, pos);
    for (var entry : SubLevelContainer.getContainer(level).getAllSubLevels())
      if (entry instanceof ServerSubLevel ship && !ship.isRemoved())
        for (var actor : BioshipIntegration.blockEntities(ship))
          if (actor instanceof LivingCoreBlockEntity core
              && BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO).distanceToSqr(Vec3.atCenterOf(pos))
                  < 1) {
            var tag = ship.getUserDataTag().copy();
            tag.putBoolean(MARKER, true);
            ship.setUserDataTag(tag);
            core.chunkLoading().update(core);
          }
    state.starters.add(pos.immutable());
  }

  public static void clear(ServerLevel level, EcosystemLab.State state) {
    var container = SubLevelContainer.getContainer(level);
    var ids = new HashSet<UUID>();
    for (var entry : List.copyOf(container.getAllSubLevels())) {
      if (!(entry instanceof ServerSubLevel ship) || ship.isRemoved()) continue;
      var tag = ship.getUserDataTag();
      boolean owned = tag != null && tag.getBoolean(MARKER);
      if (!owned && BioshipIntegration.bound(ship)) {
        var cells = BioshipStructure.cells(ship);
        if (!cells.isEmpty()) {
          var p =
              ship.logicalPose()
                  .transformPosition(Vec3.atCenterOf(cells.keySet().iterator().next()));
          owned =
              LabPopulation.landBounds(level, state.center).contains(p)
                  || AquaticLab.contains(level, p);
        }
      }
      if (owned) {
        ids.add(ship.getUniqueId());
        for (var actor : BioshipIntegration.blockEntities(ship))
          if (actor instanceof LivingCoreBlockEntity core) core.chunkLoading().release();
      }
    }
    for (var entry : List.copyOf(container.getAllSubLevels()))
      if (entry instanceof ServerSubLevel ship
          && !ship.isRemoved()
          && (ids.contains(ship.getUniqueId())
              || ship.getUserDataTag() != null
                  && ship.getUserDataTag().hasUUID("BioshipCargoOwner")
                  && ids.contains(ship.getUserDataTag().getUUID("BioshipCargoOwner"))))
        // Let Sable retire the body during its own removal phase, before its holding-map
        // update; inline destruction can leave a stale body in a pending save/unload.
        ship.markRemoved();
    for (var pos : state.starters) level.removeBlock(pos, false);
    state.starters.clear();
    state.setDirty();
  }
}
