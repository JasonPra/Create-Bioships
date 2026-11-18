package org.Azkerash.create_bioships.mixin;

@org.spongepowered.asm.mixin.Mixin(com.github.elenterius.biomancy.menu.StorageSacMenu.class)
public interface StorageSacMenuAccess {
  @org.spongepowered.asm.mixin.gen.Accessor(value = "storageSac", remap = false)
  com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity bioships$sac();
}
