package org.Azkerash.create_bioships.components.logic.legs;

/** A paired native swivel/hinge, presented as one armored biological socket. */
public final class SocketJointBlock extends ModularJointBlock {
  public static void initialize(SocketJointBlock block) {
    org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.registerStepHeight(
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block),
        org.Azkerash.create_bioships.compat.letitwalk.ModularLimbController::stepHeight);
  }

  public SocketJointBlock(Properties properties) {
    super(false, properties);
  }
}
