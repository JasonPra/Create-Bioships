package org.Azkerash.create_bioships.mixin;

import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.compat.letitwalk.SocketJointRig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Native IK targets a point; our wide, solid soles also need clearance when tilted. */
@Mixin(value = BuiltLegDriver.class, remap = false)
public abstract class SocketSoleClearanceMixin {
  @Redirect(
      method = "solve",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/letitwalk/walker/LegRuntime;footWorld(D)Lnet/minecraft/world/phys/Vec3;"))
  private static Vec3 bioships$sole(
      LegRuntime leg, double time, WalkerCoreBlockEntity walker, ServerSubLevel body, long tick) {
    return leg.footWorld(time).add(0, SocketJointRig.soleClearance(leg.built), 0);
  }
}
