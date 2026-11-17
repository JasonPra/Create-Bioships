package org.Azkerash.create_bioships;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.IronGolem;

/** Shared dietary eligibility; retaliation is a separate safety decision. */
public final class BioshipPrey {
  public static boolean eligible(LivingEntity entity) {
    return entity instanceof Mob
        && !(entity instanceof BioshipTargetEntity)
        && !(entity instanceof IronGolem)
        && !entity.getType().is(EntityTypeTags.UNDEAD);
  }
}
