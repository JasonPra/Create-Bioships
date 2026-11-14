package org.Azkerash.create_bioships;

/** Server-owned needs; pure logic kept separate from world mutation and physics. */
public final class BioshipNeeds {
    public static final int BIOMASS_REQUIRED = 64, LEVITITE_REQUIRED = 8, MAX_SATIETY = 1200;
    private int biomass, levitite, satiety = 600, trust = 60;
    public int biomass() { return biomass; }
    public int levitite() { return levitite; }
    public int satiety() { return satiety; }
    public int trust() { return trust; }
    public boolean canEatMeat() { return biomass < BIOMASS_REQUIRED || satiety < MAX_SATIETY; }
    public boolean feedMeat(int nutrition, boolean grown) {
        if ((!grown && !canEatMeat()) || (grown && satiety == MAX_SATIETY)) return false;
        if (!grown) biomass = Math.min(BIOMASS_REQUIRED, biomass + 1);
        satiety = Math.min(MAX_SATIETY, satiety + nutrition);
        trust = Math.min(100, trust + 1);
        return true;
    }
    public boolean feedLevitite() {
        if (levitite >= LEVITITE_REQUIRED) return false;
        levitite++; return true;
    }
    public boolean readyToGrow() { return biomass >= BIOMASS_REQUIRED && levitite >= LEVITITE_REQUIRED; }
    public void metabolize() {
        satiety = Math.max(0, satiety - 1);
        if (satiety == 0) trust = Math.max(0, trust - 1);
    }
    public void mistreat() { trust = Math.max(0, trust - 15); }
    public String mood() { return satiety == 0 || trust < 25 ? "grumpy" : satiety < 240 || trust < 50 ? "hungry" : "content"; }
    public double obedience() { return mood().equals("grumpy") ? 0.3 : mood().equals("hungry") ? 0.65 : 1; }
    public void restore(int biomass, int levitite, int satiety, int trust) {
        this.biomass = Math.clamp(biomass, 0, BIOMASS_REQUIRED);
        this.levitite = Math.clamp(levitite, 0, LEVITITE_REQUIRED);
        this.satiety = Math.clamp(satiety, 0, MAX_SATIETY);
        this.trust = Math.clamp(trust, 0, 100);
    }
}
