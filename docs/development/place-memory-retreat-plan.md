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
