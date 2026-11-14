package org.Azkerash.create_bioships;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BioshipNeedsTest {
    @Test void growthRequiresBothDietaryResources() {
        var needs = new BioshipNeeds();
        for (int i = 0; i < 64; i++) assertTrue(needs.feedMeat(40, false));
        assertFalse(needs.readyToGrow());
        for (int i = 0; i < 7; i++) assertTrue(needs.feedLevitite());
        assertFalse(needs.readyToGrow());
        assertTrue(needs.feedLevitite());
        assertTrue(needs.readyToGrow());
        assertFalse(needs.feedLevitite());
    }
    @Test void neglectReducesObedienceAndFeedingRebuildsTrust() {
        var needs = new BioshipNeeds();
        for (int i = 0; i < 700; i++) needs.metabolize();
        assertEquals(0, needs.satiety());
        assertEquals(0, needs.trust());
        assertEquals("grumpy", needs.mood());
        assertEquals(0.3, needs.obedience());
        for (int i = 0; i < 60; i++) {
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
        assertEquals(0, needs.trust()); assertEquals("grumpy", needs.mood());
    }
    @Test void feedingRepairsMoodWithoutExceedingMaterialCapacity() {
        var needs = new BioshipNeeds();
        needs.restore(63, 7, 200, 49);
        assertTrue(needs.feedMeat(70, false));
        assertEquals("content", needs.mood());
        assertEquals(64, needs.biomass());
        assertTrue(needs.feedLevitite()); assertTrue(needs.readyToGrow());
    }
}
