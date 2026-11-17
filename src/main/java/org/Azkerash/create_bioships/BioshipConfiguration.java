package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.amoeba.merging.ComponentMerging;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.integration.biomancy.NativeStorage;

/** Native organ conversion and accessibility, independent of movement and combat selection. */
final class BioshipConfiguration {
  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    if (BioshipAnatomy.needsBatchRepair(core)) {
      if (BioshipAnatomy.repairPlan(core).isEmpty()) return null;
      return new BioshipAdaptation.Proposal(
          BioshipBrain.Action.CONFIGURE,
          core.getBlockPos(),
          core.getBlockState(),
          0,
          0,
          0,
          1150,
          "Recall and redeploy a validated organ layout with valid apertures and attachments");
    }
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
        DigestiveSystem.mouths(core).size() < BioshipDigestiveGrowth.mouthTarget(core) && forge
            || DigestiveSystem.stomachs(core).size() < BioshipDigestiveGrowth.target(core)
                && decomposer
                && digester;
    var stomach = DigestiveSystem.stomach(core);
    if (stomach != null && stomach.connected(core))
      for (var entity : entities)
        if (NativeStorage.isStorage(entity)
            && !entity.getPersistentData().getBoolean("BioshipManualPlacement")
            && !stomach.sacPaths.containsKey(entity.getBlockPos())
            && DigestiveSystem.storagePort(core, entity.getBlockPos()) != null) urgent = true;
    if (!urgent) return null;
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.CONFIGURE,
        core.getBlockPos(),
        core.getBlockState(),
        0,
        0,
        0,
        1100,
        "Restore an accessible functioning digestive system");
  }

  static void execute(LivingCoreBlockEntity core) {
    if (!BioshipBodyBehavior.blocker(core).isEmpty()) return;
    if (BioshipAnatomy.needsBatchRepair(core)) {
      core.assimilation().beginRepair(core, BioshipAnatomy.repairPlan(core));
      return;
    }
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
