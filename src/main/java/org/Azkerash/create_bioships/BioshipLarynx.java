package org.Azkerash.create_bioships;

import com.github.elenterius.biomancy.block.modularlarynx.ModularLarynxBlockEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.amoeba.ComponentMerging;
import org.Azkerash.create_bioships.amoeba.ComponentTransport;

/** One conserved larynx capsule per upgraded mouth, acquired only after functional digestion. */
final class BioshipLarynx {
  static final MaterialCost COST = new MaterialCost(8, 4, 2);

  static boolean isLarynx(BlockState state) {
    return BuiltInRegistries.BLOCK
        .getKey(state.getBlock())
        .toString()
        .equals("biomancy:modular_larynx");
  }

  static List<FeedingMouthBlockEntity> mouths(LivingCoreBlockEntity core) {
    if (core.ship() == null) return List.of();
    return BioshipIntegration.blockEntities(core.ship()).stream()
        .filter(e -> e instanceof FeedingMouthBlockEntity)
        .map(e -> (FeedingMouthBlockEntity) e)
        .toList();
  }

  static long missingCount(LivingCoreBlockEntity core) {
    return mouths(core).stream().filter(m -> !m.voiced()).count();
  }

  static boolean missing(LivingCoreBlockEntity core) {
    return missingCount(core) > 0;
  }

  static boolean ready(LivingCoreBlockEntity core) {
    return !BioshipNutrition.critical(core) && DigestiveSystem.functional(core);
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    if (!ready(core) || !missing(core)) return null;
    var mouth = mouths(core).stream().filter(m -> !m.voiced()).findFirst().orElseThrow();
    boolean nativeOrgan = nativeSource(core, mouth) != null;
    if (!nativeOrgan) {
      if (!EmergencyReserve.after(core, COST)) return null;
      for (var source : BioshipPriorities.worldObjectives(core)) {
        var state = core.vision().sourceState(core, source.pos());
        if (state != null && isLarynx(state)) return null;
      }
    }
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.GROW_LARYNX,
        mouth.getBlockPos(),
        mouth.getBlockState(),
        nativeOrgan ? 0 : COST.flesh(),
        nativeOrgan ? 0 : COST.fiber(),
        nativeOrgan ? 0 : COST.bone(),
        970,
        "Combine a larynx with a mouth after completing digestion");
  }

  static boolean develop(LivingCoreBlockEntity core, BlockPos pos) {
    if (!ready(core)
        || !(core.getLevel().getBlockEntity(pos) instanceof FeedingMouthBlockEntity mouth)
        || mouth.voiced()) return false;
    var organ = nativeSource(core, mouth);
    if (organ != null)
      return ComponentTransport.storeComponent(core.amoebaComponents(), organ.getBlockPos());
    if (!EmergencyReserve.after(core, COST) || !COST.pay(BioshipStorage.account(core)))
      return false;
    var capsule = new CompoundTag();
    capsule.putString(
        ModularLarynxBlockEntity.SOUND_EVENT_TAG,
        BuiltInRegistries.SOUND_EVENT
            .getKey(com.github.elenterius.biomancy.init.ModSoundEvents.FLESH_BLOB_GROWL.get())
            .toString());
    ComponentMerging.attachLarynx(core.amoebaComponents(), mouth, capsule);
    core.setChanged();
    return true;
  }

  private static BlockEntity nativeSource(
      LivingCoreBlockEntity core, FeedingMouthBlockEntity mouth) {
    return BioshipIntegration.blockEntities(core.ship()).stream()
        .filter(e -> isLarynx(e.getBlockState()))
        .filter(
            e ->
                BioshipSupport.safeMove(
                    core, e.getBlockPos(), mouth.getBlockPos(), !BioshipTissue.host(e).isAir()))
        .findFirst()
        .orElse(null);
  }

  static void growl(LivingCoreBlockEntity core) {
    vocalize(core, false);
  }

  static void hurt(LivingCoreBlockEntity core) {
    vocalize(core, true);
  }

  private static void vocalize(LivingCoreBlockEntity core, boolean hurt) {
    // One utterance per hit avoids a multi-mouth volume explosion; every mouth can be selected.
    var voiced = mouths(core).stream().filter(FeedingMouthBlockEntity::voiced).toList();
    if (voiced.isEmpty()) return;
    var mouth = voiced.get(core.getLevel().random.nextInt(voiced.size()));
    mouth.vocalize(hurt);
  }
}
