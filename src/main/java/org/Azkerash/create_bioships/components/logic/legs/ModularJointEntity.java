package org.Azkerash.create_bioships.components.logic.legs;

import dev.letitwalk.content.LegJointBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Native joint configuration; no per-block gait or movement ticker. */
public final class ModularJointEntity extends LegJointBlockEntity {
  public ModularJointEntity(BlockPos pos, BlockState state) {
    super(pos, state);
  }

  private java.util.UUID componentIdentity = java.util.UUID.randomUUID();

  public java.util.UUID componentIdentity() {
    return componentIdentity;
  }

  @Override
  protected void write(
      net.minecraft.nbt.CompoundTag tag,
      net.minecraft.core.HolderLookup.Provider registries,
      boolean clientPacket) {
    super.write(tag, registries, clientPacket);
    tag.putUUID("BioshipJointId", componentIdentity);
  }

  @Override
  protected void read(
      net.minecraft.nbt.CompoundTag tag,
      net.minecraft.core.HolderLookup.Provider registries,
      boolean clientPacket) {
    super.read(tag, registries, clientPacket);
    if (tag.hasUUID("BioshipJointId")) {
      componentIdentity = tag.getUUID("BioshipJointId");
    }
  }

  @Override
  public BlockEntityType<?> getType() {
    return ComponentRegistry.MODULAR_JOINT_ENTITY.get();
  }
}
