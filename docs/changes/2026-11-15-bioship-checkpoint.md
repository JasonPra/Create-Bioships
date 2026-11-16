# Bioship checkpoint: modular behavior and bundled gameplay fixes

This log covers the changes after commit `51b19bc`. It separates changes players can observe from the supporting refactor.

## Gameplay changes to check

### Wrecks and removing a living core

- Mining, destroying, directly replacing, or removing the block entity before removing a living core marks its surviving Sable body dead. Legitimate core relocation and Sable assembly transfers do not kill the ship.
- Death records cover the body UUID as well as its lineage, so small fragments can inherit death after the original body has disappeared.
- Older coreless bioship hulls missing their death metadata can be recovered. Recovery requires a recognizable mixed bioship hull with organ/propulsion structure; an arbitrary coreless platform is not treated as a corpse.
- Collection still excludes every other bioship core and never takes components from living ships. Existing slow decay and organs-last ordering remain.

**Look for:** remove the core from a grown ship, then let a living hatchling approach it. The body should become a salvageable, eventually decaying wreck rather than an unrecognized live grid.

### Batch assimilation and whole-grid scavenging

- Up to four appendages can collect reachable components concurrently. The original blocks travel as Sable cargo, preserving block entities and inventories.
- Pickup no longer requires a final mounting position. Parts sink into persistent internal stock first; suitable kept parts grow back out after the reachable collection batch finishes.
- Nearby ordinary-world components can share an approach point. Physical arm clearance still applies: glass permits sight but blocks grabs.
- Selecting a wreck commits collection beyond ordinary resource targets. It continues until the non-core grid is exhausted or the remaining waypoints cannot be reached.
- Cargo counts toward leg load and support planning. A full carrying allowance can require processing a load before resuming the same wreck. Completed collection fits useful components and reclaims surplus with the existing fractional material losses.
- Reclaiming a duplicate stomach transfers its private products, queued input and stored tube credits to the surviving stomach before removing it.
- Healthy collected world components remain functional. A blocked deployment remains stored and permits other needs to compete. Saving/loading preserves both active cargo and swallowed stock; death releases conserved cargo/inventories.
- Adjacent sources no longer block their own attachment clearance. Selected components reachable from an extended body are not discarded by a small core-centered search box.

**Look for:** place several components close together, including filled sacs. Check concurrent pulls, later deployment and unchanged contents. A wreck should keep attracting collection after the creature has enough resources for its immediate growth target.

### Needs, food and queued goals

- Reloading a crawl goal clears its transient route and selection cache together, preventing a restored goal from retaining an unrelated old navigation owner.
- The ordinary goal queue ranks multiple eligible proposals by need, urgency, value and effort. Consumed, expired, unreachable and no-longer-advantageous targets disappear as eligibility is refreshed.
- Useful components outrank routine growth when the food meter is at least 50. The threshold uses actual food/satiety, not Growth biomass or Flesh Bit stock.
- Pressing nutrition considers all supported food-producing work: food stores, loose meat, metabolizable nutrients/material reserves, wreck reclamation, hunting and desperate recycling of spare components. Missing digestive prerequisites remain important.
- Pressing food work can interrupt an unrelated committed component move or ordinary core scouting. Carried parts remain conserved. A move already acquiring food is allowed to continue rather than switching to another food source. Safety and protected reconstruction transactions retain their existing guards.
- Growth does not consume the last emergency food reserves. Spare functional components are eligible for lossy fuel reclamation only in desperation with working digestion; essential digestive organs remain protected.

**Look for:** lower food below 50 during unrelated collection, provide nutrients or a food-filled sac rather than just dropped meat, and watch the selected behavior. A ship already collecting food should keep that job.

### Mass, posture and leg attachments

- Four leg roots attach to actual underside body blocks. Their distribution follows mass and body bounds rather than remaining clustered around the original core location.
- Automatic organ/core relocation must preserve connectivity, reach and a stable support footprint containing the projected center of mass.
- Carried component stock contributes to support planning. Unsupported bodies no longer receive artificial grounded stabilization in midair.
- Front/rear and side contacts define body pitch and roll. Stance height accounts for the body's actual underside after reconfiguration.
- Idle standing support is separate from walking animation. A creature can retain a valid wall grip after collection and falls when that support is removed.

**Look for:** feed it several organs, let the core move to a perch, and inspect roots and stance over a one-block rise. Legs should support the distributed body and the gait should stop cycling when stationary.

### Paid adaptation, storage and emergency regrowth

- When stronger legs cannot be funded, an overloaded grounded ship can reclaim nonessential paid tissue or redundant empty components. It preserves the core, digestive organs, main intestine path and structural connectivity.
- Optional protective tissue/counterweights require surplus materials, stable support and enough leg capacity for the added mass. Needed leg strengthening precedes the addition.
- Construction products can overflow into a private partition of connected native Storage Sacs: 128 units of stomach working storage plus 256 per connected sac. Normal food slots remain player-accessible; disconnected material cannot be spent.
- Storage overflow can fund an additional sac, including any extra intestine costs. Empty automatically grown excess sacs can be reclaimed later. Stored material can be spent or metabolized without being duplicated.
- The core transfers, rather than copies, a limited emergency reserve: up to 32 Flesh Bits, 16 Elastic Fibers and 8 Bone Fragments from surplus connected stores.
- After learning its digestive organs, its emergency core-reserve route can regrow a missing mouth for 8/4/2 flesh/fiber/bone and a stomach for 16/8/4. The stomach price includes initial paid tubing material; emergency regrowth does not grant another free intestine network. Existing ordinary reserve-funded repair remains available when a functioning stomach can perform it.
- Emergency metabolism can burn core reserves after other fuel is exhausted.

**Look for:** let surplus stock accumulate, destroy the mouth/stomach while preserving the core, and check funded regrowth. Inspect storage connectivity and resource totals before/after transfers and spending.

### Developer tools and test conditions

- F3+P holograms include queued-goal, active collecting-arm and swallowed-component counts alongside behavior, target and route information.
- The packaged Navigation Lab keeps normal daylight cycling and mob spawning; its geometry and prizes remain controlled fixtures. Existing F3+B sight visualization is unchanged.
- The supplied world is tested from a separate working copy. Uploaded saves and private runtime probes are not included in the mod or repository.

## Refactor boundaries

- `BioshipBrain`: combines eligible proposals and selects the action; it does not implement resource accounting or cargo transport.
- `GoalQueue`: reusable bounded ordering and target deduplication, independent of Minecraft entities and world mutation.
- `BioshipNutrition`: shared satiety threshold, nutritional value and task-purpose classification.
- `MaterialCost` / `MaterialLedger`: shared affordability, payment, rollback, refund and transfer rules. Accounts can be core reserves or connected stomach/sac storage.
- `BioshipAdaptationPlanner`: read-only proposals using an immutable state snapshot. `BioshipAdaptation` owns the adaptive state and executes selected funded changes.
- `BioshipStructure`: one read-only Sable cell traversal reused by repair, decay, support and block counts, replacing duplicate scanners.
- `BioshipSupport`: mass-weighted layout and support validation. `GroundRoute` and crawling remain the navigation/movement boundary.
- `OrganMotion`: individual physical cargo transport. `BioshipAssimilation` coordinates batches, internal stock and wreck-job progress.
- `NavigationDebug`: read-only observation of actual subsystem state; displaying diagnostics does not request a new decision.

## Validation

- The supplied save was copied to a separate dedicated-server working directory. Its original terrain, night lighting, food, resources and existing creatures were preserved.
- The original coreless wreck had 367 blocks and no death marker. It became marked dead through the legacy recovery path, then decreased to 363 blocks by tick 100 and 355 by tick 200 as the living ship collected parts.
- The 12 removed blocks were collected before the normal 1,200-tick decay grace period, so they were actual scavenging rather than rot. The living ship retained an active component-recovery job.
- The uploaded original save was left unchanged. The temporary observation probe was removed from source before packaging.

- Java 21 Gradle `build runGameTestServer` passed with deprecation/removal diagnostics enabled.
- **33 unit tests and all 103 required NeoForge GameTests passed** on the final code. Coverage includes both real-physics glass mazes, core removal and fragment death, four-arm inventory conservation, whole-grid collection, duplicate-stomach stock conservation, reserve-funded regrowth, pooled storage, food-priority interruption and debug snapshot consistency.
- **205 main resource JSON files parsed**. The runtime JAR excludes development tests, the supplied save and the temporary private-world probe.
- `git diff --check` passed.
