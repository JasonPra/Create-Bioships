package org.azkerash.createbioships.components.logic.legs;

/** A paired native swivel/hinge, presented as one armored biological socket. */
public final class SocketJointBlock extends ModularJointBlock {
  public static void initialize(SocketJointBlock block) {
    org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.registerStepHeight(
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block),
        org.azkerash.createbioships.compat.letitwalk.ModularLimbController::stepHeight);
  }

  public SocketJointBlock(Properties properties) {
    super(false, properties);
  }
}
