package org.Azkerash.create_bioships.components.logic.serum;

import com.github.elenterius.biomancy.serum.BasicSerum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Native injector-compatible gene identity. Component classes own compatibility and effects. */
public final class ComponentGeneSerum extends BasicSerum {
  public ComponentGeneSerum(int color) {
    super(color);
  }

  public boolean canAffectEntity(CompoundTag data, LivingEntity user, LivingEntity target) {
    return false;
  }

  public boolean canAffectPlayerSelf(CompoundTag data, Player player) {
    return false;
  }

  public void affectEntity(
      ServerLevel level, CompoundTag data, LivingEntity user, LivingEntity target) {}

  public void affectPlayerSelf(ServerLevel level, CompoundTag data, ServerPlayer player) {}
}
