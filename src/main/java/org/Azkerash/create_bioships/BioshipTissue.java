package org.Azkerash.create_bioships;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Ordinary tissue is native Packed Flesh; registered hull blocks remain future armor. */
public final class BioshipTissue {
  public static Block body() {
    return com.github.elenterius.biomancy.init.ModBlocks.PACKED_FLESH.get();
  }

  public static boolean isTissue(BlockState state) {
    return state.is(body()) || state.is(Create_bioships.FLESH.get());
  }

  static void remember(
      net.minecraft.world.level.block.entity.BlockEntity entity, BlockState state) {
    if (entity == null) return;
    var data = entity.getPersistentData();
    data.putBoolean("BioshipHostFlesh", isTissue(state));
    if (isTissue(state))
      data.put("BioshipHostState", net.minecraft.nbt.NbtUtils.writeBlockState(state));
    else data.remove("BioshipHostState");
  }

  static BlockState host(net.minecraft.world.level.block.entity.BlockEntity entity) {
    if (entity == null || !entity.getPersistentData().getBoolean("BioshipHostFlesh"))
      return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    var data = entity.getPersistentData();
    if (data.contains("BioshipHostState") && entity.getLevel() != null)
      return net.minecraft.nbt.NbtUtils.readBlockState(
          entity
              .getLevel()
              .registryAccess()
              .lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
          data.getCompound("BioshipHostState"));
    // Old saves used the custom hull exclusively; do not turn legacy armor into native flesh.
    return Create_bioships.FLESH.get().defaultBlockState();
  }
}
