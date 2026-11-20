package org.azkerash.createbioships.core;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Ordered per-block HP definitions, independent of creature identity or damage handling. */
public final class BlockHealthValues {
  private static final NavigableMap<ResourceLocation, Double> VALUES = new TreeMap<>();

  private BlockHealthValues() {}

  public static void registerBlocks() {
    VALUES.clear();
    BuiltInRegistries.BLOCK
        .keySet()
        .forEach(id -> VALUES.put(id, healthless(BuiltInRegistries.BLOCK.get(id)) ? 0d : 10d));
  }

  public static double health(BlockState state) {
    return state.isAir() || healthless(state.getBlock())
        ? 0
        : VALUES.getOrDefault(BuiltInRegistries.BLOCK.getKey(state.getBlock()), 10d);
  }

  private static boolean healthless(net.minecraft.world.level.block.Block block) {
    // Plating is a protection pool, not living tissue. Armored organs retain their organ HP.
    return block instanceof org.azkerash.createbioships.components.logic.armor.ArmorBlock
        || block instanceof org.azkerash.createbioships.components.logic.legs.CompactLimbBlock;
  }

  public static NavigableMap<ResourceLocation, Double> entries() {
    return Collections.unmodifiableNavigableMap(VALUES);
  }
}
