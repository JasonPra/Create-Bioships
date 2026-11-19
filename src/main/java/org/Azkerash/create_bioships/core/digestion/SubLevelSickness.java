package org.Azkerash.create_bioships.core.digestion;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.components.logic.mouth.MouthBlock;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity;
import org.Azkerash.create_bioships.core.*;

/** One saved sickness timer and bounded vomiting episode per creature, driven by digestion. */
public final class SubLevelSickness {
  public static final int DURATION = 30 * 20, INTERVAL = 5 * 20;

  private record Organ(ServerSubLevel body, BlockPos pos) {}

  private SubLevelSickness() {}

  public static int remaining(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) return 0;
    body = SubLevelCreatureParts.root(body);
    if (!SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) return 0;
    return (int)
        Math.clamp(
            SubLevelData.digestiveSickness(body).until() - body.getLevel().getGameTime(),
            0,
            DURATION);
  }

  public static void infect(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    if (!SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) return;
    long now = body.getLevel().getGameTime();
    var old = SubLevelData.digestiveSickness(body);
    SubLevelData.digestiveSickness(
        body,
        new SubLevelData.DigestiveSickness(
            Math.max(old.until(), now + DURATION),
            old.until() > now ? old.nextEpisode() : now + INTERVAL));
  }

  public static int advance(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) return 0;
    body = SubLevelCreatureParts.root(body);
    if (!SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) return 0;
    long now = body.getLevel().getGameTime();
    var state = SubLevelData.digestiveSickness(body);
    if (state.until() <= now) {
      if (state.until() != 0)
        SubLevelData.digestiveSickness(body, new SubLevelData.DigestiveSickness(0, 0));
      return 0;
    }
    if (now < state.nextEpisode()) return 0;
    SubLevelData.digestiveSickness(
        body, new SubLevelData.DigestiveSickness(state.until(), now + INTERVAL));
    var mouths = new ArrayList<Organ>();
    var stomachs = new ArrayList<Organ>();
    for (var member : SubLevelCreatureParts.members(body))
      for (var group : SubLevelDigestion.groups(member)) {
        for (var p : group.mouths()) mouths.add(new Organ(member, p));
        for (var p : group.stomachs()) stomachs.add(new Organ(member, p));
      }
    if (!mouths.isEmpty()) {
      var mouth = mouths.get(Math.floorMod(now / INTERVAL, mouths.size()));
      var block = mouth.body().getLevel().getBlockState(mouth.pos());
      if (block.hasProperty(MouthBlock.FACING)) {
        var at =
            mouth
                .body()
                .logicalPose()
                .transformPosition(
                    Vec3.atCenterOf(mouth.pos()).relative(block.getValue(MouthBlock.FACING), .7));
        body.getLevel()
            .sendParticles(
                new BlockParticleOption(
                    ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState()),
                at.x,
                at.y,
                at.z,
                8,
                .14,
                .1,
                .14,
                .025);
        var sound =
            BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.parse("biomancy:block.digester.crafting_random"));
        if (sound != null)
          body.getLevel().playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, .4f, .8f);
      }
    }
    for (var organ : stomachs)
      if (body.getLevel().getBlockEntity(organ.pos()) instanceof StomachBlockEntity stomach) {
        for (int offset = 0; offset < 18; offset++) {
          int slot =
              offset == 0
                  ? StomachBlockEntity.PASTE_SLOT
                  : StomachBlockEntity.INPUT_SLOTS + offset - 1;
          if (stomach.getItem(slot).is(StomachBlockEntity.paste())) {
            stomach.removeItem(slot, 1);
            return 1;
          }
        }
      }
    return 0;
  }
}
