package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;

public final class BioshipAccess {
  public final Map<UUID, String> players = new LinkedHashMap<>(), animals = new LinkedHashMap<>();

  public boolean allows(LivingCoreBlockEntity core, Entity entity) {
    if (entity instanceof Player)
      return entity.getUUID().equals(core.creator()) || players.containsKey(entity.getUUID());
    return entity instanceof Animal
        && entity.hasCustomName()
        && animals.containsKey(entity.getUUID());
  }

  public CompoundTag save(UUID owner) {
    CompoundTag tag = new CompoundTag();
    tag.putBoolean("Managed", true);
    if (owner != null) tag.putUUID("Owner", owner);
    CompoundTag p = new CompoundTag(), a = new CompoundTag();
    players.forEach((key, value) -> p.putString(key.toString(), value));
    animals.forEach((key, value) -> a.putString(key.toString(), value));
    tag.put("Players", p);
    tag.put("Animals", a);
    return tag;
  }

  public void load(CompoundTag tag) {
    players.clear();
    animals.clear();
    read(tag.getCompound("Players"), players);
    read(tag.getCompound("Animals"), animals);
  }

  private static void read(CompoundTag tag, Map<UUID, String> out) {
    for (String key : tag.getAllKeys())
      try {
        if (out.size() < 128) out.put(UUID.fromString(key), tag.getString(key));
      } catch (IllegalArgumentException ignored) {
      }
  }

  public static boolean permits(CompoundTag tag, Entity entity) {
    if (entity instanceof Player)
      return tag.hasUUID("Owner") && tag.getUUID("Owner").equals(entity.getUUID())
          || tag.getCompound("Players").contains(entity.getUUID().toString());
    return entity instanceof Animal
        && entity.hasCustomName()
        && tag.getCompound("Animals").contains(entity.getUUID().toString());
  }

  public static LivingCoreBlockEntity core(net.minecraft.world.level.Level level, BlockPos pos) {
    var ship = BioshipIntegration.containing(level, pos);
    if (ship == null) return null;
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity core && core.primary(ship)) return core;
    return null;
  }

  public static void tick(LivingCoreBlockEntity core) {
    if (!core.isMature()) return;
    var level = core.getLevel();
    var lower = core.getBlockPos().offset(DinghyGrowth.DOOR);
    var membrane =
        BuiltInRegistries.BLOCK
            .get(ResourceLocation.parse("biomancy:biometric_membrane"))
            .defaultBlockState();
    if (level.getBlockState(lower).is(Create_bioships.DOOR.get())) {
      level.setBlock(lower, membrane, 18);
      level.setBlock(lower.above(), membrane, 18);
    }
    var panel = core.getBlockPos().offset(DinghyGrowth.ACCESS_PANEL);
    if (level.getBlockState(panel).isAir())
      level.setBlock(panel, Create_bioships.ACCESS_PANEL.get().defaultBlockState(), 3);
    CompoundTag permissions = core.access().save(core.creator());
    for (var offset : DinghyGrowth.ACCESS_MEMBRANES)
      if (level.getBlockEntity(core.getBlockPos().offset(offset))
          instanceof ShipMembraneAccess barrier) barrier.bioships$access(permissions);
  }
}
