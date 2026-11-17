package org.Azkerash.create_bioships.core;

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
    BuiltInRegistries.BLOCK.keySet().forEach(id -> VALUES.put(id, 10d));
  }

  public static double health(BlockState state) {
    return state.isAir()
        ? 0
        : VALUES.getOrDefault(BuiltInRegistries.BLOCK.getKey(state.getBlock()), 10d);
  }

  public static NavigableMap<ResourceLocation, Double> entries() {
    return Collections.unmodifiableNavigableMap(VALUES);
  }
}
