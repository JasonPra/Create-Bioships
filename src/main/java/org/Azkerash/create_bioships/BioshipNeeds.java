package org.Azkerash.create_bioships;

/** Server-owned needs; incorporated tissue and food have deliberately separate effects. */
public final class BioshipNeeds {
  public static final int BIOMASS_REQUIRED = 64, LEVITITE_REQUIRED = 8, MAX_SATIETY = 1200;
  private int biomass, levitite, satiety = 600, trust = 60, poison;

  public int biomass() {
    return biomass;
  }

  public int levitite() {
    return levitite;
  }

  public int satiety() {
    return satiety;
  }

  public int trust() {
    return trust;
  }

  public int poisonSeconds() {
    return poison;
  }

  public boolean aggressive() {
    return trust < 0;
  }

  /** Existing tissue supplies building material, with no nutrition or trust reward. */
  public int incorporateBiomass(int amount) {
    int accepted = Math.clamp(amount, 0, BIOMASS_REQUIRED - biomass);
    biomass += accepted;
    return accepted;
  }

  public boolean canEatMeat() {
    return biomass < BIOMASS_REQUIRED || satiety < MAX_SATIETY;
  }

  public boolean feedMeat(int nutrition, boolean grown) {
    if ((!grown && !canEatMeat()) || (grown && satiety == MAX_SATIETY)) return false;
    if (!grown) incorporateBiomass(1);
    satiety = Math.min(MAX_SATIETY, satiety + Math.max(0, nutrition));
    trust = Math.min(100, trust + 1);
    return true;
  }

  /** Actual provisioned meals reward care even when their nutrition tops off a full stomach. */
  public void digestFedMeal(int nutrition) {
    satiety = Math.min(MAX_SATIETY, satiety + Math.max(0, nutrition));
    trust = Math.min(100, trust + 1);
  }

  public void consumeHuntedPrey(boolean toxic) {
    // Hunting provides less nutrition, and does not reward the captain with trust.
    if (toxic) poison();
    else satiety = Math.min(MAX_SATIETY, satiety + 80);
  }

  public void poison() {
    poison = Math.min(120, poison + 30);
    satiety = Math.max(0, satiety - 80);
    trust = Math.max(-100, trust - 2);
  }

  public void restorePoison(int seconds) {
    poison = Math.clamp(seconds, 0, 120);
  }

  public boolean feedLevitite() {
    if (levitite >= LEVITITE_REQUIRED) return false;
    levitite++;
    return true;
  }

  public boolean readyToGrow() {
    return biomass >= BIOMASS_REQUIRED;
  }

  public boolean flightNourished() {
    return levitite >= LEVITITE_REQUIRED;
  }

  public static int foodDemand(int bodyBlocks) {
    return 1 + Math.max(0, bodyBlocks - 1) / 64;
  }

  public void metabolize() {
    metabolize(1);
  }

  public void metabolize(int bodyBlocks) {
    metabolizeDemand(foodDemand(bodyBlocks));
  }

  public void metabolizeDemand(int demand) {
    satiety = Math.max(0, satiety - Math.max(0, demand) - (poison > 0 ? 2 : 0));
    if (poison > 0) poison--;
    if (satiety == 0 && demand > 0) trust = Math.max(-100, trust - 1);
  }

  /** Emergency stored tissue supplies energy, never a trust reward. */
  public void emergencyEnergy(int amount) {
    satiety = Math.clamp(satiety + Math.max(0, amount), 0, MAX_SATIETY);
  }

  public void mistreat() {
    trust = Math.max(-100, trust - 15);
  }

  public String mood() {
    return aggressive()
        ? "aggressive"
        : satiety == 0 || trust < 25
            ? "grumpy"
            : satiety < 240 || trust < 50 ? "hungry" : "content";
  }

  public double obedience() {
    return (poison > 0 ? 0.6 : 1)
        * (aggressive() ? 0 : mood().equals("grumpy") ? 0.3 : mood().equals("hungry") ? 0.65 : 1);
  }

  public void restore(int biomass, int levitite, int satiety, int trust) {
    this.biomass = Math.clamp(biomass, 0, BIOMASS_REQUIRED);
    this.levitite = Math.clamp(levitite, 0, LEVITITE_REQUIRED);
    this.satiety = Math.clamp(satiety, 0, MAX_SATIETY);
    this.trust = Math.clamp(trust, -100, 100);
  }
}
