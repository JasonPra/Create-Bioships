package org.Azkerash.create_bioships;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BioshipNeedsTest {
    @Test void biomassBuildsBeforeAmalgamEnablesFlight() {
        var needs = new BioshipNeeds();
        for (int i = 0; i < 64; i++) assertTrue(needs.feedMeat(40, false));
        assertTrue(needs.readyToGrow());
        assertFalse(needs.flightNourished());
        for (int i = 0; i < 7; i++) assertTrue(needs.feedLevitite());
        assertTrue(needs.readyToGrow());
        assertFalse(needs.flightNourished());
        assertTrue(needs.feedLevitite());
        assertTrue(needs.readyToGrow());
        assertTrue(needs.flightNourished());
        assertFalse(needs.feedLevitite());
    }
    @Test void incorporatedTissueBuildsWithoutNutritionOrTrust() {
        var needs = new BioshipNeeds();
        needs.restore(0, 0, 100, 20);
        assertEquals(64, needs.incorporateBiomass(1000));
        assertTrue(needs.readyToGrow());
        assertEquals(100, needs.satiety()); assertEquals(20, needs.trust());
        assertEquals(0, needs.incorporateBiomass(64));
        assertEquals(0, needs.incorporateBiomass(-10));
        assertFalse(needs.flightNourished());
    }
    @Test void neglectReducesObedienceAndFeedingRebuildsTrust() {
        var needs = new BioshipNeeds();
        for (int i = 0; i < 700; i++) needs.metabolize();
        assertEquals(0, needs.satiety());
        assertEquals(-41, needs.trust());
        assertEquals("aggressive", needs.mood());
        assertEquals(0, needs.obedience());
        for (int i = 0; i < 120; i++) {
            for (int second = 0; second < 10; second++) needs.metabolize();
            needs.feedMeat(70, true);
        }
        assertEquals("content", needs.mood());
        assertTrue(needs.trust() >= 50);
    }
    @Test void aFullGrownShipDoesNotConsumeMoreFood() {
        var needs = new BioshipNeeds();
        needs.restore(64, 8, 1200, 100);
        assertFalse(needs.feedMeat(40, true));
        needs.metabolize();
        assertTrue(needs.feedMeat(40, true));
        assertEquals(64, needs.biomass());
        assertEquals(1200, needs.satiety());
    }
    @Test void mistreatmentAndPersistentStateStayWithinBounds() {
        var needs = new BioshipNeeds();
        needs.restore(1000, -1, 10000, 999);
        assertEquals(64, needs.biomass()); assertEquals(0, needs.levitite());
        assertEquals(1200, needs.satiety()); assertEquals(100, needs.trust());
        for (int i = 0; i < 10; i++) needs.mistreat();
        assertEquals(-50, needs.trust()); assertEquals("aggressive", needs.mood());
    }
    @Test void feedingRepairsMoodWithoutExceedingMaterialCapacity() {
        var needs = new BioshipNeeds();
        needs.restore(63, 7, 200, 49);
        assertTrue(needs.feedMeat(70, false));
        assertEquals("content", needs.mood());
        assertEquals(64, needs.biomass());
        assertTrue(needs.feedLevitite()); assertTrue(needs.readyToGrow());
    }
    @Test void sizeIncreasesFoodDemandAndStarvationCrossesZero() {
        var small = new BioshipNeeds(); var large = new BioshipNeeds();
        small.restore(64, 8, 100, 0); large.restore(64, 8, 100, 0);
        small.metabolize(1); large.metabolize(257);
        assertEquals(99, small.satiety()); assertEquals(95, large.satiety());
        large.restore(64, 8, 1, 0); large.metabolize(257);
        assertEquals(-1, large.trust()); assertTrue(large.aggressive());
        assertEquals(0, large.obedience());
    }
    @Test void huntingIsLessEfficientAndDoesNotRewardTrust() {
        var handFed = new BioshipNeeds(); var hunting = new BioshipNeeds();
        handFed.restore(64, 8, 100, 20); hunting.restore(64, 8, 100, 20);
        handFed.feedMeat(70, true); handFed.feedMeat(70, true);
        hunting.consumeHuntedPrey(false);
        assertTrue(hunting.satiety() < handFed.satiety());
        assertEquals(20, hunting.trust()); assertEquals(22, handFed.trust());
    }
    @Test void zombieTissuePoisonsAndPoisonPersistsWithinBounds() {
        var needs = new BioshipNeeds(); needs.restore(64, 8, 200, 20);
        needs.consumeHuntedPrey(true);
        assertEquals(120, needs.satiety()); assertEquals(18, needs.trust());
        assertEquals(30, needs.poisonSeconds());
        needs.metabolize(1); assertEquals(117, needs.satiety()); assertEquals(29, needs.poisonSeconds());
        needs.restorePoison(999); assertEquals(120, needs.poisonSeconds());
        needs.restorePoison(-10); assertEquals(0, needs.poisonSeconds());
    }
    @Test void digestedProvisionedFoodBuildsTrustAtFullSatiety() {
        var needs = new BioshipNeeds();
        needs.restore(64, 8, BioshipNeeds.MAX_SATIETY, 20);
        needs.digestFedMeal(70);
        assertEquals(BioshipNeeds.MAX_SATIETY, needs.satiety());
        assertEquals(21, needs.trust());
    }
}
