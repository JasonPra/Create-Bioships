# Place memory and threat-aware retreat

## Gameplay changes

- Each living core saves up to 128 observed places, separated by dimension and spatial region.
  Places remember biome IDs, water/river/ocean/forest/cover/elevation evidence and observed prey
  species. This is evidence, not an omniscient map or a guarantee that prey remains there.
- Terrain observations run every 40 game ticks, with two rotating distant columns plus the current
  location. Distant columns must be loaded and visible. Up to 16 entity vision tests per observation
  record prey even when the creature is not hungry and record actual pursuers/explosive hazards.
- Prey confidence decays over 6,000 game ticks; danger confidence over 1,200. Historical species
  remain known. Terrain confidence declines more slowly. These timers follow simulation ticks,
  including accelerated `/tick rate` tests; existing real-time target expiry is unchanged.
- Skeletons, pillagers, witches and ranged weapon users favor verified cover. Capability profiles
  also cover trident drowned, guardians, blazes/ghasts, spiders/cave spiders and jumping threats.
  Height offers less protection against climbers and flyers; water is less attractive when an
  aquatic pursuer is involved.
- Unsafe creepers keep the existing explosive-retreat rule. Nearby observed primed TNT now triggers
  the same panic. Water is preferred only while opening blast distance, and is never treated as
  explosion immunity. Existing injury thresholds and trusted pilot behavior remain in effect.
- Retreat combines remembered opportunities with local escape directions. It ranks separation from
  all currently observed threats, verified cover, elevation, water, recent danger and weak prey
  evidence. Up to four actual routes are checked per 20 ticks. Ground creatures approach supported
  shores; submerged creatures use the existing 3D water planner. Air-seeking still takes precedence.
- Lava, fire, magma, cactus, campfires, powder snow and unloaded/out-of-border destinations are
  rejected. Existing whole-body clearance/support checks remain in the navigators. Retreat does
  not introduce flight, teleportation, unlimited sight or new chunk loading.
- Close pursuers can receive defensive swipes during FLEE. Movement remains FLEE. Existing windup,
  world-locked aim, swept tip collision, dodge behavior and velocity-based impact damage are reused.
  Allies, uninvolved prey and unsafe creeper fights are excluded.

## Responsibilities

- `creature.memory.PlaceMemory`: bounded immutable evidence and confidence aging.
- `creature.memory.PlaceMemoryCodec`: NBT persistence and malformed-entry handling.
- `PlaceObservations` / `EncounterObservations`: terrain and encounter perception adapters.
- `creature.survival.ThreatProfile` / `EscapeScoring`: reusable pure threat traits and preferences.
- `ThreatObservations`: bounded three-second tactical sightings, refreshed every 10 ticks.
- `RetreatPlanning`: candidate ranking, hazards and existing navigator integration.
- `RetreatCombat`: nearby pursuer selection only. `TentacleStrike` retains collision damage ownership.
- `BioshipAutonomy`: existing retreat activation policy and orchestration.

## Developer tools

No commands were added. Existing **F3+P** route/hologram debugging now reports remembered-place count.
Existing developer world types, lab reset commands and test tools are preserved.

## Validation

Pending final checkpoint. No graphical animation QA is claimed; live tests use the headless Sable
server and existing actual collision/movement code.
