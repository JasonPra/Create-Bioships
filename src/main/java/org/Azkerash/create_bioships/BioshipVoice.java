package org.Azkerash.create_bioships;

import com.github.elenterius.biomancy.block.modularlarynx.ModularLarynxBlockEntity;
import com.github.elenterius.biomancy.init.ModSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.network.BioshipNetwork;

/** Native larynx samples, spatialized at a mouth and modulated by the client voice envelope. */
final class BioshipVoice {
  enum Utterance {
    HURT,
    GROWL
  }

  static void play(FeedingMouthBlockEntity mouth, CompoundTag capsule, Utterance utterance) {
    var profile =
        utterance == Utterance.HURT
            ? new BioshipVoicePalette.Profile(.95f, 7, .65f, 1, false)
            : new BioshipVoicePalette.Profile(.7f, 18, .65f, 3, false);
    emit(mouth, capsule, profile, utterance == Utterance.HURT);
  }

  static void letter(FeedingMouthBlockEntity mouth, CompoundTag capsule, char letter) {
    emit(mouth, capsule, BioshipVoicePalette.letter(letter), false);
  }

  private static void emit(
      FeedingMouthBlockEntity mouth,
      CompoundTag capsule,
      BioshipVoicePalette.Profile profile,
      boolean hurt) {
    if (!(mouth.getLevel() instanceof ServerLevel level) || !mouth.voiced()) return;
    String sample = capsule.getString(ModularLarynxBlockEntity.SOUND_EVENT_TAG);
    if (sample.isBlank())
      sample =
          BuiltInRegistries.SOUND_EVENT.getKey(ModSoundEvents.FLESH_BLOB_GROWL.get()).toString();
    // A hurt grunt samples Biomancy's matching short hurt sound; consonants can use its rough
    // attack.
    if (hurt || profile.breathy())
      sample =
          BuiltInRegistries.SOUND_EVENT.getKey(ModSoundEvents.FLESH_BLOB_HURT.get()).toString();
    var ship = BioshipIntegration.containing(level, mouth.getBlockPos());
    if (ship == null) return;
    var pos = ship.logicalPose().transformPosition(Vec3.atCenterOf(mouth.getBlockPos()));
    var data = new CompoundTag();
    data.putLong("Mouth", mouth.getBlockPos().asLong());
    data.putString("Sample", sample);
    data.putFloat("Pitch", profile.pitch());
    data.putFloat("Volume", profile.volume());
    data.putInt("Ticks", profile.ticks());
    data.putInt("Attack", profile.attack());
    PacketDistributor.sendToPlayersNear(
        level, null, pos.x, pos.y, pos.z, 64, new BioshipNetwork.Voice(data));
    mouth.animate(profile.ticks(), true);
  }
}
