package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse each projectile's own impact damage and enchantment handling after a Sable block hit. */
@Mixin(Projectile.class)
public interface ProjectileImpactInvoker {
  @Invoker("onHit")
  void core$hitEntity(net.minecraft.world.phys.HitResult hit);
}
