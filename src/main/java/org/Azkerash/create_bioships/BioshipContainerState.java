package org.Azkerash.create_bioships;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Conserves current inventory and data-only reserves across a synchronous physical handoff. */
final class BioshipContainerState {
  static CompoundTag capture(Level level, BlockEntity entity) {
    var data = entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
    if (Boolean.getBoolean("bioships.traceContainers") && entity instanceof FeedingMouthBlockEntity)
      System.out.println(
          "MOUTH_CAPTURE pos="
              + entity.getBlockPos()
              + " instance="
              + System.identityHashCode(entity)
              + " data="
              + data);
    return data;
  }

  static BlockEntity entity(
      Level level, dev.ryanhcode.sable.sublevel.ServerSubLevel owner, BlockPos pos) {
    if (owner == null) return level.getBlockEntity(pos);
    var plot = owner.getPlot();
    var holder = plot.getChunkHolder(plot.toLocal(new net.minecraft.world.level.ChunkPos(pos)));
    return holder == null ? null : holder.getChunk().getBlockEntity(pos);
  }

  static void restore(
      Level level,
      dev.ryanhcode.sable.sublevel.ServerSubLevel owner,
      BlockPos destination,
      CompoundTag capsule) {
    if (capsule == null) return;
    var entity = entity(level, owner, destination);
    if (entity == null
        || !net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
            .getKey(entity.getType())
            .toString()
            .equals(capsule.getString("id"))) {
      if (Boolean.getBoolean("bioships.traceContainers"))
        System.out.println(
            "CONTAINER_RESTORE_MISSING pos="
                + destination
                + " expected="
                + capsule.getString("id")
                + " actual="
                + entity
                + " state="
                + level.getBlockState(destination));
      return;
    }
    var relocated = capsule.copy();
    relocated.putInt("x", destination.getX());
    relocated.putInt("y", destination.getY());
    relocated.putInt("z", destination.getZ());
    entity.loadWithComponents(relocated, level.registryAccess());
    entity.setChanged();
  }

  static List<ItemStack> packDrops(List<ItemStack> drops, LootParams.Builder params) {
    var entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
    if (entity instanceof LivingCoreBlockEntity) return List.of();
    if (entity != null)
      for (var stack : drops)
        if (stack.is(entity.getBlockState().getBlock().asItem())) {
          entity.saveToItem(stack, params.getLevel().registryAccess());
          var component = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
          if (component == null) continue;
          var data = component.copyTag();
          if (entity instanceof StomachBlockEntity) {
            for (var key : List.of("ConnectedMouth", "GutPath", "SacPaths")) data.remove(key);
          }
          stack.set(
              net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
              net.minecraft.world.item.component.CustomData.of(data));
        }
    return drops;
  }

  static void creativeBreak(
      Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
    if (level.isClientSide || !player.isCreative()) return;
    var entity = level.getBlockEntity(pos);
    if (entity == null || entity instanceof LivingCoreBlockEntity) return;
    var data = capture(level, entity);
    boolean stored =
        !data.getList("Items", 10).isEmpty()
            || !data.getCompound("Larynx").isEmpty()
            || !data.getCompound("TissueReserves").isEmpty()
            || !data.getCompound("Adaptation").getCompound("Reserve").isEmpty()
            || data.getInt("NutrientReserve") > 0
            || data.getInt("Biomass") > 0
            || data.getInt("Satiety") > 0
            || data.getInt("SwimmingGraspers") > 0
            || data.getInt("PaidTubeStock") > 0
            || data.getInt("FreeTubeStock") > 0
            || data.getBoolean("TubeBootstrapped");
    if (!stored) return;
    var params =
        new LootParams.Builder((net.minecraft.server.level.ServerLevel) level)
            .withParameter(LootContextParams.BLOCK_ENTITY, entity);
    var stack =
        packDrops(List.of(new ItemStack(entity.getBlockState().getBlock())), params).getFirst();
    var drop =
        new net.minecraft.world.entity.item.ItemEntity(
            level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
    drop.setDefaultPickUpDelay();
    level.addFreshEntity(drop);
  }

  private BioshipContainerState() {}
}
