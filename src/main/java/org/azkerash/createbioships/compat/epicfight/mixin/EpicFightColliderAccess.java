package org.azkerash.createbioships.compat.epicfight.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "yesman.epicfight.api.collider.Collider", remap = false)
public interface EpicFightColliderAccess {
  @Invoker("getHitboxAABB")
  AABB core$bounds();

  @Invoker("isCollide")
  boolean core$collides(Entity entity);
}
