# Ecosystem Lab

Choose **Bioship Ecosystem Lab** when creating a world. Unlike the maze preset, it uses vanilla Overworld noise terrain and Savanna generation features: normal elevation, caves, vegetation, light and day/night behavior. Its biome identifies it as a development world; ordinary worlds are unaffected.

A barrier perimeter quarantines a section with a 64-block radius. The interior terrain is not flattened or excavated. Twelve candidate cache sites search for real canopy, grass and sheltered cave features. If a feature is absent, that site falls back to ordinary ground; water or unsuitable locations can be skipped. Cache contents are normal Storage Sac inventories containing pork and construction resources. Consumed caches do not magically refill.

On first joining, the player receives a core, Bio-Forge, Decomposer, Digester, goggles, wrench and pork. Place the essentials and repeatedly fill the resulting mouth's nine slots with pork to observe growth. `/bioship_ecosystem status` reports the arena location and cache count. `/bioship_lab reset` still belongs to the separate maze world type.

Supplemental prey replenishment attempts occur every 600 ticks, one animal at a time, with a local cap of 12. Hostile attempts occur every 1,200 ticks, one at a time, with a local cap of four. Existing animals/monsters count toward those limits. Vanilla spawning remains enabled and can exceed these supplemental caps. Additions honor `doMobSpawning`, loaded chunks, spawn collision, normal type-specific placement/light checks, and a 24-block player exclusion. Hostiles therefore appear at night or in valid dark locations, not continuously in daylight. Test-spawned undead receive a leather helmet with zero drop chance.

## Automated free-roaming run

The opt-in `soakTest` source set supplies `BioshipWildProbe`. It is compiled by `check`, excluded from the normal mod JAR, and loaded only with `-PbioshipSoak`:

```text
./gradlew runServer -PbioshipSoak
```

Use a dedicated development run directory/world. The probe places two cores and the three starter machines on existing land, without leveling terrain; fills their mouth inventories with pork up to four times; and observes ordinary behavior. It does not select goals, grant construction materials directly, force daylight, accelerate digestion or add special chunk tickets. The normal ecosystem setup supplies scattered resource caches.

The default run lasts 3,600 ticks. It records digestive connectivity, actual recipe progress, growth, materials and behavior. It rejects a run with no functional digestive system, no repeated feeding, no resource production, or a stationary nonprogressing configuration. It stops its dedicated server when done. The preset's playerless simulation uses the same living-creature leases as ordinary gameplay.

Headless runs exercise physics and behavior. Foot-motion appearance, graphics and sound still need a graphical client check. A single successful soak is evidence for its tested seed and duration, not exhaustive behavior coverage.
