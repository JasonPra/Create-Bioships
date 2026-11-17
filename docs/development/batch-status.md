# Creature milestones

Six milestones remain open. Developer tools are now the highest priority, ahead of further creature features. Continue automatically in small tested batches, push each checkpoint, and report changes, evidence and the remaining list. Completing a focused batch does not close a milestone. Replace conflicting growth/placement systems selectively; preserve verified persistence, transactions, networking and physics integrations.

1. **Needs and growth priorities:** mouth rests closed outside biting/chewing/swallowing/vocalization; functional mouth/stomach/gut first; appropriate lungs on land or gills in water before sight and armor; support legs; eyes only for demonstrated sight gaps on useful body mounts, never gratuitously on the core. A material surplus alone is not an organ need. Flesh coverage precedes armor.
2. **Safe body remodeling:** compact connected and balanced anatomy; valid full-face leg sockets; protected core; safe interior flesh replacement and one conserved refund; support throughout stashing/redeployment; shared amoeba merging for harvesting, combination and reconfiguration. A broken core is permanently lost, and replacement never revives its dead lineage.
3. **Water specialization:** full-block gills with exposed outward intake; lungs visibly attached flush to tissue; broad tapered aquatic leg and grasper flippers with face-aware procedural swimming strokes; underwater hunting routes with exposed gills or air-budgeted lung dives and a return route to air.
4. **Food, energy and shrinkage:** food preemption and wreck-before-hunt; hunting for growth; common storage and digestion budgets; starvation-driven reclamation of surplus flesh/armor into fuel while preserving vital coverage, connections and support. Update mass and maximum health when tissue is permanently lost. Continue hunting/expanding as resources and ecosystem permit.
5. **Movement and animation QA:** separate physical hunting-grasper components from locomotion legs/fins, with their own costs/upgrades; short-reach core bootstrap appendages for nearby interaction/defense; larger hunting reach requires additional components; terrain/wall/ceiling routes, swimming, self-righting, stable leg contacts and COM, procedural flippers and all merge transitions in the current graphical client; impact-driven appendage attacks.
6. **Ecosystem regression and packaging:** realistic land/water ecology, isolated pens and capped refill/reset; save/reload and chunk loading; normal and accelerated tick tests with watchdog retained; documented cause of death; complete developer presets and commands, full regression and current docs.

Recent checkpoints: health/blast adaptations d026d1b; compaction fa7c5f3; socket identity 1ba50c9; protected core and initial habitat respiration af40e24. The initial affordable-first-eye policy in af40e24 is superseded by the requested need-only directional eye policy, pending implementation. Normal 3,600-tick replay passed; the 10,000-TPS-target continuation failed its watchdog and remains unqualified.

Next batch (highest priority): persistent prepared regression-world scenarios and visible results; expand the aquatic arena beyond twice its current dimensions with realistic seabed relief, rocks/arches, seagrass, coral, kelp and scaled fish populations. Preserve/reset/refill existing tools and world presets. After that, replace conflicting growth/eye decisions before advanced creature features.

### Developer lab layout update (in progress)

Developer tools take precedence over creature feature work. Consolidate navigation/regression
and ecosystem areas into the superflat developer preset; retain the old preset ID as an alias.
The hotbar Developer Test Navigator uses a vanilla chest selector with separate Regression
and Ecosystem sections. Existing maze and habitat destinations are implemented; full per-feature
regression stations and persistent PASS/FAIL reporting remain pending.

Ocean target: 161-block interior width (about ten chunks), procedural relief, rock arch, reef,
kelp and seagrass; capped fish refill on the existing five-minute ecosystem controller.
Decorative seabed dioramas occupy the four-block outer buffer. The land chamber now
contains generated relief, trees, shaded alcoves, grass and a Forest biome assignment.
The base preset now uses void; regression platforms alone use a bedrock floor.

Implemented a large End-biome floating-island enclosure at X=8192,
with navigator access, real biome assignment, Enderman population control and escape detection.
Do not mark the End habitat ready until teleport containment has been exercised.

Focused habitat checks passed 2/2; developer navigator and the combined-world first-boot
walkthrough remain to be exercised graphically. The successful test process still logged a Sable
`Body has been removed` shutdown exception; track cleanup separately before final signoff.
Six creature milestones remain open; no additional creature behavior work was included here.
