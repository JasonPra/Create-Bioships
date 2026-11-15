package org.Azkerash.create_bioships;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Gameplay loading follows the creature, with separate view and simulation ticket ranges. */
final class BioshipChunks {
  private static final TicketType<UUID> VIEW =
      TicketType.create("bioship_view", UUID::compareTo, 60);
  private static final TicketType<UUID> SIMULATION =
      TicketType.create("bioship_simulation", UUID::compareTo, 60);

  private record Lease(ServerLevel level, ChunkPos center, UUID owner, int view, int simulation) {}

  private Lease lease;
  private long refreshed = Long.MIN_VALUE;

  void update(LivingCoreBlockEntity core) {
    var ship = core.ship();
    if (!(core.getLevel() instanceof ServerLevel level) || ship == null || core.body().dead()) {
      release();
      return;
    }
    var center = new ChunkPos(BlockPos.containing(BioshipBlueprint.physical(core, BlockPos.ZERO)));
    int view = core.vision().maximumDistance(core) / 16;
    int simulation = Math.min(view, level.getServer().getPlayerList().getSimulationDistance());
    var next = new Lease(level, center, ship.getUniqueId(), view, simulation);
    if (!next.equals(lease)) {
      release();
      lease = next;
    }
    long now = level.getGameTime();
    if (refreshed != Long.MIN_VALUE && now - refreshed < 20) return;
    var source = level.getChunkSource();
    // A load-only ticket does not enter the simulation-distance tracker.
    source
        .chunkMap
        .getDistanceManager()
        .addTicket(VIEW, center, ChunkLevel.byStatus(FullChunkStatus.FULL) - view, next.owner());
    // FULL tickets are two levels outside ENTITY_TICKING; include that vanilla border.
    source.addRegionTicket(SIMULATION, center, simulation + 2, next.owner(), true);
    refreshed = now;
  }

  void release() {
    var previous = lease;
    lease = null;
    refreshed = Long.MIN_VALUE;
    if (previous == null) return;
    var source = previous.level().getChunkSource();
    source
        .chunkMap
        .getDistanceManager()
        .removeTicket(
            VIEW,
            previous.center(),
            ChunkLevel.byStatus(FullChunkStatus.FULL) - previous.view(),
            previous.owner());
    source.removeRegionTicket(
        SIMULATION, previous.center(), previous.simulation() + 2, previous.owner(), true);
  }

  boolean active() {
    return lease != null;
  }

  int viewDistance() {
    var current = lease;
    return current == null ? 0 : current.view();
  }

  int simulationDistance() {
    var current = lease;
    return current == null ? 0 : current.simulation();
  }
}
