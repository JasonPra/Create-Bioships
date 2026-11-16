# Ecosystem Lab

Choose **Bioship Ecosystem Lab** when creating a world. It uses normal Overworld noise terrain with Savanna vegetation, caves, elevations, daylight and weather. A barrier perimeter encloses a 64-block-radius land arena. The separate glass swimming enclosure includes deep water, a breathable air section and an observation platform.

The preset itself creates **two land cores and one aquatic core**, with a Bio-Forge, Decomposer and Digester near each. This works without the optional automated observer. On first joining, the player also receives essentials, goggles, a wrench and pork. Repeatedly fill the resulting mouths with pork to observe growth. Scattered Storage Sacs contain real inventories of pork, flesh, fibers and bone; candidate positions search real tree canopies, tall grass and sheltered cave features.

## Population controller

An always-active repeating command block above the land center runs `/bioship_ecosystem pulse`. The shared controller attempts one fish every 200 ticks (cap 16), one land prey every 600 ticks (cap 12), and one hostile every 1,200 ticks (cap 4). Six prey, two hostiles and eight fish are initially offered, subject to safe placement. Counts include existing animals of the corresponding classes. Ordinary natural spawning remains enabled and may exceed these controller caps.

These are explicit test-scenario spawns, so replenishment does not depend on darkness, natural spawn checks, or a nearby player. It still requires valid water for fish and collision-free, supported land positions for land mobs. Controller-spawned mobs persist; undead get a non-dropping leather helmet for sunlight protection. On dedicated servers with command blocks disabled, a server-tick fallback invokes the exact same capped controller. A per-tick gate prevents double execution. Independently, every 6,000 game ticks (five in-game minutes), the server attempts a full refill of fish, land prey and hostiles up to the same caps. The deadline persists across saves and does not depend on command blocks. `/bioship_ecosystem replenish` performs that full refill immediately; safe placement can leave fewer mobs than the cap.

## Commands

All commands require operator permission and are restricted to the Ecosystem Lab preset.

- `/bioship_ecosystem status` — actual living core, fish, prey and hostile counts; cache count and controller position.
- `/bioship_ecosystem water` — visit the aquatic observation platform.
- `/bioship_ecosystem reset` — remove fixture-owned and in-arena bioship grids/cargo, restore resource caches and starter components, rebuild the water enclosure at its original elevation, and reseed the three cores and scenario populations. It retains natural land terrain, including terrain changes from the previous run. Use a fresh world with the same seed when a pristine terrain comparison is required.
- `/bioship_ecosystem replenish` — immediately refill fish and both land populations up to their caps, subject to valid placement.
- `/bioship_ecosystem pulse` — internal repeating-controller endpoint for the gradual 200/600/1,200-tick cadence. Full automatic refills use the separate five-minute server timer.

Older worlds migrate their controller and aquatic isolation on load without resetting subjects or the tank. Use `reset` for a fresh fixture run; it restarts the five-minute refill timer. `/bioship_lab reset` belongs to the separate Navigation Lab preset.

F3+B toggles sight and contact-hitbox diagnostics. F3+P toggles navigation paths and behavior/target/priority holograms. Graphical animation QA remains separate from headless tests.

## Automated observation

The optional `soakTest` source set compiles with `check` but is excluded from the normal runtime JAR. To load it:

```text
./gradlew runServer -PbioshipSoak
```

In a dedicated development run, enable the JVM property `bioships.wildProbe=true`. The observer uses the preset's three starters, fills their mouth inventories with pork up to four times, and records ordinary simulation. It does not choose goals, grant construction resources, change daylight, speed digestion or use special force-loading tickets. The default duration is 3,600 ticks (`bioships.wildTicks` overrides it), after which it stops the dedicated server.

Every expected creature must be observed, develop digestion, receive repeated feeding, survive and produce resources. It reports failures instead of allowing one successful creature to mask another. Telemetry includes creature identity, position, health, air state, component movement, digestion progress, materials and behavior. Underwater samples are more frequent.

A successful run covers its seed, scenarios and duration; it does not replace repeat navigation tests or graphical animation checks.

## Aquatic isolation and adaptation history

The 41×41 glass tank has a three-block-wide bottom-slab apron and a solid outer perimeter wall (49×49 outer footprint). The wall reaches above the glass roof and has a bottom-slab cap; the apron is cleared of vegetation. This blocks outside ground-prey sight lines and prevents adjacent spawning. Existing land mobs caught in the new apron are moved outside onto supported dry terrain. The tank water, inner floor, glass, observation platform and aquatic subjects are retained during migration.

Water exposure samples partial body contact and extended legs independently of mouth air access. Fractional scores survive saving; uninterrupted exposure can display 100%, and dry decay is three times slower than water accumulation. Brief hops no longer replace already evolved paddles while use remains strongly aquatic; reverting to walking legs requires sustained land use. Paid costs, emergency reserves and minimum exposure duration remain.
