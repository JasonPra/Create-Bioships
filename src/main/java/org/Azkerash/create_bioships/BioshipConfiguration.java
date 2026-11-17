package org.Azkerash.create_bioships;

/** Native organ conversion and accessibility, independent of movement and combat selection. */
final class BioshipConfiguration {
  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    var entities = BioshipIntegration.blockEntities(core.ship());
    boolean forge = false, decomposer = false, digester = false;
    for (var entity : entities) {
      String id =
          net.minecraft.core.registries.BuiltInRegistries.BLOCK
              .getKey(entity.getBlockState().getBlock())
              .toString();
      forge |= id.equals("biomancy:bio_forge");
      decomposer |= id.equals("biomancy:decomposer");
      digester |= id.equals("biomancy:digester");
    }
    boolean urgent =
        DigestiveSystem.mouths(core).size() < BioshipDigestiveGrowth.target(core) && forge
            || DigestiveSystem.stomachs(core).size() < BioshipDigestiveGrowth.target(core)
                && decomposer
                && digester;
    boolean optional = false;
    var mouth = core.mouthBlock();
    if (mouth != null
        && core.getLevel().getBlockState(mouth).is(Create_bioships.FEEDING_MOUTH.get())) {
      var entity = core.getLevel().getBlockEntity(mouth);
      if (entity != null && !entity.getPersistentData().getBoolean("BioshipManualPlacement")) {
        urgent |=
            !core.buildingHull()
                && (!core.hatchling() || HatchlingFront.canArrange(core))
                && !FeedingMouthBlock.openFace(
                    core,
                    mouth,
                    core.getLevel().getBlockState(mouth).getValue(FeedingMouthBlock.FACING));
        if (core.hatchling()) optional |= HatchlingFront.canArrange(core);
        else urgent |= core.huntingAllowed() && EngineRoom.inside(core, mouth);
      }
    }
    var stomach = DigestiveSystem.stomach(core);
    if (stomach != null && stomach.connected(core))
      for (var entity : entities)
        if (EngineRoom.storage(entity)
            && !entity.getPersistentData().getBoolean("BioshipManualPlacement")
            && !stomach.sacPaths.containsKey(entity.getBlockPos())
            && DigestiveSystem.storagePort(core, entity.getBlockPos()) != null) urgent = true;
    if (!urgent && !optional) return null;
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.CONFIGURE,
        core.getBlockPos(),
        core.getBlockState(),
        0,
        0,
        0,
        urgent ? 1100 : 450,
        urgent
            ? "Restore an accessible functioning digestive system"
            : "Arrange organs without obstructing the eye");
  }

  static void execute(LivingCoreBlockEntity core) {
    if (!BioshipBodyBehavior.blocker(core).isEmpty()) return;
    ComponentMerging.tryAvailable(core.amoebaComponents());
    if (ComponentTransport.busy(core.amoebaComponents())) return;
    for (var actor : BioshipIntegration.blockEntities(core.ship())) {
      BioshipIntegration.convertFirstBioForge(core, actor.getBlockPos());
    }
    BioshipIntegration.orientMouth(core);
    DigestiveSystem.connect(core, core.ship());
    DigestiveSystem.arrangeStorage(core);
  }

  private BioshipConfiguration() {}
}
