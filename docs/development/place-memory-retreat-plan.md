# Place memory and threat-aware retreat

The user approved separate responsibilities for persistent place memory, escape planning and
close-range retreat combat. Pillagers and other ranged attackers share cover-seeking preferences.
Additional approved threats include witches/trident attackers, flying attackers, aquatic pursuers,
climbers/jumpers and nearby primed TNT. Terrain hazards and breathing capability constrain escape.

Prioritized checkpoints and estimates:
1. Audit and boundaries — 5–10 minutes.
2. Bounded persistent place memory — 10–15 minutes.
3. Observed biome/water/cover/cliff sampling — 10–20 minutes.
4. Prey and danger observations with aging — 5–15 minutes.
5. Threat classification and weighted destination scores — 10–20 minutes.
6. Route integration for ground, climbing and swimming retreat — 15–20 minutes.
7. Collision-based defensive swipes while retreating — 10–15 minutes.
8. Focused behavior and integration fixtures — 10–20 minutes.
9. Broader regressions, fixes, documentation and final push — 5–15 minutes.
Push each checkpoint, report remaining tasks, and continue automatically.

Responsibilities and constraints:
- Core-independent PlaceMemory holds bounded observed locations, biome/resource evidence and ages.
  It does not reveal unseen terrain or guarantee prey remains at a remembered location.
- Observation adapters sample loaded, perceived terrain/entities with a fixed work budget.
- Threat profiles describe capabilities, not one hand-written AI per mob species.
- Escape scoring considers separation, verified cover, elevation, water suitability and mixed threats.
  Actual routes and breathing still decide feasibility; water is not explosion immunity.
- Existing navigation owns routes, and existing TentacleStrike owns impact hitboxes and damage.
  Retreat combat selects close pursuers without replacing the FLEE movement goal or attacking allies.
- Stable records persist; short-lived target memory retains its existing separate three-second policy.

Checkpoint 1 complete: current retreat tries five grounded directions away from one danger point.
It remembers no terrain and rejects all attacks while FLEE is selected. Existing strikes already
apply damage through moving tip hitboxes. Underwater routing and climbing can be reused. Existing
panic/trust/health gates remain the decision for entering retreat; TNT adds an explosive hazard.

Checkpoint 2 complete: up to 128 dimension-specific observed places persist with the core.
Terrain knowledge survives while prey and danger confidence age independently. Species lists and
loaded NBT records are bounded; malformed entries are discarded. Six new unit tests pass; all
93 unit tests and GameTest source compilation passed.

Checkpoint 3 complete: terrain observations run every 40 ticks, sampling the occupied position
and two rotating loaded columns. Distant terrain must pass actual vision checks. Water/biome,
forest, nearby cover and elevation features are remembered without generating chunks. Compilation
passed; live observer coverage is part of checkpoint 8.

Checkpoint 4 complete: visible prey species are recorded even when not hungry. Visible actual
pursuers, nearby creepers and primed TNT leave aging danger evidence. At most 16 entity visibility
checks occur per 40-tick observation, with a rotating start to avoid dense-herd bias. Species data
is not treated as a live target. Compilation passed.

Checkpoint 5 complete: reusable capability profiles cover explosions, ranged weapons, climbing,
flying, aquatic pursuit and jumping. Weighted scoring considers mixed-threat separation, verified
cover, height, water, recent danger and prey evidence. Explosive retreat rejects destinations that
approach the blast. Tactical sightings expire after three seconds and are bounded to eight.
All 99 unit tests pass, including six scoring tests.

Checkpoint 6 complete: retreat ranks local directions and remembered places, then validates at most
four routes every 20 ticks using existing ground/climbing or 3D water navigation. Surface breathing
remains a higher-priority need. Ground routes approach water from safe support rather than assuming
walking can enter deep water; submerged creatures can choose underwater escape routes. Nearby TNT
triggers explosive panic. Fire, lava and other hazardous destinations/routes are rejected. All 99
unit tests and three existing live navigation/escape checks passed. F3+P data includes KnownPlaces.

Checkpoint 7 complete: FLEE movement can select a close actual pursuer for the existing telegraphed
swipe. A valid active victim is retained during the swipe. Allies, uninvolved prey and unsafe creeper
fights are excluded. Range only gates attack selection; damage still requires the existing swept
tip hitbox to collide, with no new radius damage. All 99 unit tests and live-test compilation pass.

Checkpoint 8 complete: nine focused live checks passed. Eight new fixtures cover core-save memory
round trips/malformed records, prey evidence without hunger, occluded-water privacy, concrete mob
profiles, remembered shore preference, submerged retreat/low-air rejection, TNT interruption and
FLEE swipe selection. The existing player-dodge collision test also passed. The TNT fixture exposed
that reloading autonomy retained its old observation cooldown; reload now clears transient tactical
and route caches. Existing F3+P holograms display the remembered-place count. No commands were added.
