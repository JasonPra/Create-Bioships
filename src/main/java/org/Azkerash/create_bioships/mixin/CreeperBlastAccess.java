package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read native explosion settings without serializing a mob every AI tick. */
@Mixin(Creeper.class)
public interface CreeperBlastAccess {
  @Accessor("explosionRadius")
  int bioships$explosionRadius();

  @Accessor("swell")
  int bioships$swell();

  @Accessor("maxSwell")
  int bioships$maxSwell();
}
