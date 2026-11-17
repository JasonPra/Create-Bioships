package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Observed species and danger evidence, independent of whether the creature is hungry. */
final class EncounterObservations {
  private int cursor;

  void observe(LivingCoreBlockEntity core, Vec3 here) {
    var level = core.getLevel();
    var entities =
        level.getEntities(
            (Entity) null,
            new AABB(here, here).inflate(32),
            e -> e.isAlive() && (e instanceof LivingEntity || e instanceof PrimedTnt));
    if (entities.isEmpty()) return;
    // Bound expensive vision tests, rotating the starting index so dense herds do not starve
    // others.
    int count = Math.min(16, entities.size());
    for (int i = 0; i < count; i++) {
      var entity = entities.get(Math.floorMod(cursor++, entities.size()));
      var p = entity.getBoundingBox().getCenter();
      var pos = BlockPos.containing(p);
      if (!level.hasChunkAt(pos) || !core.vision().visible(core, p)) continue;
      String species = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
      String dimension = PlaceObservations.dimension(core),
          biome = PlaceObservations.biome(core, pos);
      if (entity instanceof LivingEntity living && BioshipDefense.eligiblePrey(core, living))
        core.placeMemory()
            .prey(dimension, PlaceObservations.point(p), biome, species, level.getGameTime());
      if (entity instanceof PrimedTnt
          || entity instanceof Creeper && entity.distanceToSqr(here) < 144
          || entity instanceof LivingEntity living && BioshipDefense.threatening(core, living))
        core.placeMemory()
            .danger(dimension, PlaceObservations.point(p), biome, species, level.getGameTime());
    }
  }
}
