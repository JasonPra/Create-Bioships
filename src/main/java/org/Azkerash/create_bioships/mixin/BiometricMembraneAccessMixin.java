package org.Azkerash.create_bioships.mixin;

import org.Azkerash.create_bioships.BioshipAccess;
import org.Azkerash.create_bioships.ShipMembraneAccess;

import com.github.elenterius.biomancy.block.membrane.BiometricMembraneBlockEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BiometricMembraneBlockEntity.class)
public class BiometricMembraneAccessMixin implements ShipMembraneAccess {
  @Unique private CompoundTag bioships$permissions = new CompoundTag();

  @Override
  public void bioships$access(CompoundTag access) {
    if (bioships$permissions.equals(access)) return;
    bioships$permissions = access.copy();
    var self = (BlockEntity) (Object) this;
    self.setChanged();
    if (self.getLevel() != null)
      self.getLevel()
          .sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 2);
  }

  @Inject(
      method = "shouldIgnoreEntityCollisionAt",
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private void allow(
      BlockState state,
      BlockGetter level,
      BlockPos pos,
      Entity entity,
      CallbackInfoReturnable<Boolean> callback) {
    if (bioships$permissions.getBoolean("Managed"))
      callback.setReturnValue(BioshipAccess.permits(bioships$permissions, entity));
  }

  @Inject(method = "save", at = @At("TAIL"), remap = false)
  private void save(CompoundTag tag, CallbackInfo callback) {
    if (bioships$permissions.getBoolean("Managed"))
      tag.put("BioshipAccess", bioships$permissions.copy());
  }

  @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
  private void load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
    bioships$permissions = tag.getCompound("BioshipAccess").copy();
  }
}
