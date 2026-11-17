package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.network.BioshipNetwork;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** Player-care feedback is independent of passive metabolism and behavior selection. */
public final class BioshipTrustFeedback {
  public static final String INBOX = "BioshipTrustBursts";
  static final int PARTICLES = TrustParticleBudget.PARTICLES;
  private long nextTick;

  boolean ready(long tick) {
    return tick >= nextTick;
  }

  long nextTick() {
    return nextTick;
  }

  void restore(long value) {
    nextTick = value;
  }

  boolean emit(LivingCoreBlockEntity core, boolean positive) {
    if (!(core.getLevel() instanceof ServerLevel level)
        || core.ship() == null
        || !ready(level.getGameTime())) return false;
    nextTick = level.getGameTime() + 60;
    var surface = BioshipParticleSurface.points(core);
    var points = new ListTag();
    for (int i = 0; i < PARTICLES && !surface.isEmpty(); i++) {
      var p = surface.get((int) (((long) i * surface.size()) / PARTICLES));
      var tag = new CompoundTag();
      tag.putDouble("X", p.x);
      tag.putDouble("Y", p.y);
      tag.putDouble("Z", p.z);
      points.add(tag);
    }
    var data = new CompoundTag();
    data.putBoolean("Positive", positive);
    data.put("Points", points);
    var at = BodyCoordinates.physical(core.amoebaComponents(), net.minecraft.core.BlockPos.ZERO);
    PacketDistributor.sendToPlayersNear(
        level, null, at.x, at.y, at.z, 96, new BioshipNetwork.TrustBurst(data));
    if (!positive && !core.body().flashing(core)) BioshipLarynx.growl(core);
    core.setChanged();
    return true;
  }
}
