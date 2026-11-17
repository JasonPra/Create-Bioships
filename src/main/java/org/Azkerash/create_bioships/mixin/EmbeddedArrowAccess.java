package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractArrow.class)
public interface EmbeddedArrowAccess {
  @Accessor("inGround")
  void core$inGround(boolean value);
}
