package org.Azkerash.create_bioships;

/** One metabolic step, independent of controller cadence, navigation and action selection. */
final class BioshipMetabolism {
  static void step(LivingCoreBlockEntity core, BioshipNeeds needs) {
    int demand = BioshipEnergy.demand(core);
    needs.metabolizeDemand(demand);
    for (var organ : DigestiveSystem.stomachs(core))
      if (demand > 0 && needs.satiety() <= BioshipNeeds.MAX_SATIETY - 20) {
        int energy = organ.burnPaste();
        if (energy > 0) needs.emergencyEnergy(energy);
      }
    if (needs.satiety() > 0 || demand == 0) return;
    int energy = 0;
    while (energy < demand) {
      int next = emergencyFuel(core);
      if (next == 0) break;
      energy += next;
    }
    if (energy > 0) needs.emergencyEnergy(energy);
    if (energy < demand) core.body().starve(core, demand - energy);
  }

  private static int emergencyFuel(LivingCoreBlockEntity core) {
    for (var organ : DigestiveSystem.stomachs(core)) {
      int energy = organ.burnNutrients();
      if (energy > 0) return energy;
    }
    for (var organ : DigestiveSystem.stomachs(core)) {
      int energy = organ.burnReserve();
      if (energy > 0) return energy;
    }
    int buffered = core.blueprint().burnBufferedReserve();
    return buffered > 0 ? buffered : core.adaptation().burnEmergency(core);
  }
}
