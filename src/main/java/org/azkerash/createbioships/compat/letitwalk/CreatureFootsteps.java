package org.azkerash.createbioships.compat.letitwalk;

import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.azkerash.createbioships.components.logic.legs.CompactLimbBlock;
import org.azkerash.createbioships.core.*;

/** Surface-specific creature steps at native foot contacts; ordinary walkers keep their sounds. */
public final class CreatureFootsteps {
  private CreatureFootsteps() {}

  public record Profile(float volume, float pitch) {}

  public static Profile profile(boolean compact) {
    return compact ? new Profile(.15f, 1f) : new Profile(.35f, .85f);
  }

  public static boolean play(
      Level level, TerrainProbe.Ground ground, float weight, LegRuntime leg) {
    if (leg.built.segments.isEmpty()) {
      return false;
    }
    var container = SubLevelContainer.getContainer(level);
    if (container == null) {
      return false;
    }
    var found = container.getSubLevel(leg.built.segments.getFirst());
    if (!(found instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel part)
        || !SubLevelCreatureParts.isPart(part)
        || !SubLevelData.isLiving(SubLevelCreatureParts.root(part))) {
      return false;
    }
    var compact =
        level.getBlockState(part.getPlot().getCenterBlock()).getBlock() instanceof CompactLimbBlock;
    var profile = profile(compact);
    var point = WalkerFrame.toWorld(ground.point());
    var sound =
        ground.state().getSoundType(level, BlockPos.containing(point.add(0, -.01, 0)), null);
    level.playSound(
        null,
        point.x,
        point.y,
        point.z,
        sound.getStepSound(),
        SoundSource.NEUTRAL,
        sound.getVolume() * profile.volume() * Math.clamp(weight, .4f, 1.5f),
        sound.getPitch() * profile.pitch() * (.95f + level.random.nextFloat() * .1f));
    if (level instanceof net.minecraft.server.level.ServerLevel server && !ground.state().isAir()) {
      server.sendParticles(
          new net.minecraft.core.particles.BlockParticleOption(
              net.minecraft.core.particles.ParticleTypes.BLOCK, ground.state()),
          point.x,
          point.y + .04,
          point.z,
          compact ? 3 : 6,
          compact ? .09 : .16,
          .04,
          compact ? .09 : .16,
          .015);
    }
    return true;
  }
}
