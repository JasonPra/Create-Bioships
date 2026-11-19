package org.Azkerash.create_bioships.mixin;

import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse native rowing without its surface-only navigation policy. */
@Mixin(value = WalkerSwim.class, remap = false)
public interface WalkerSwimInvoker {
  @Invoker("row")
  static void bioships$row(
      WalkerCoreBlockEntity context,
      List<LegRuntime> flippers,
      Pose3dc pose,
      double speed,
      double surface,
      Vec3 bank) {
    throw new AssertionError();
  }
}
