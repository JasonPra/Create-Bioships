package org.Azkerash.create_bioships.integration.biomancy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native storage recognition used by active creature inventories. */
public final class NativeStorage {
  private NativeStorage() {}

  public static boolean isStorage(BlockEntity entity) {
    return BuiltInRegistries.BLOCK
        .getKey(entity.getBlockState().getBlock())
        .toString()
        .equals("biomancy:storage_sac");
  }
}
