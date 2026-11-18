package org.Azkerash.create_bioships.core.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Component-specific state owned by a grid while attached; serialized at item/movement boundaries.
 */
public interface ComponentState {
  CompoundTag save(HolderLookup.Provider registries);

  void load(CompoundTag data, HolderLookup.Provider registries);
}
