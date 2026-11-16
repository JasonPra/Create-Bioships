# Leg capabilities and mouth-bound graspers

Walking legs and food/component graspers have separate responsibilities. Legs provide support, grip and movement; mouth slots own gathering jobs. A level-one feeding mouth provides one grasper slot, while its larynx upgrade is level two and provides two. Jobs save their mouth/slot association. A bare amoeboid core retains one bootstrap uptake slot so it can acquire its first mouth. The existing four walking legs are not consumed by gathering jobs.

`BioshipGraspers.registerProvider` is the extension point for future explicit grasper components. Providers return named mouth/body slots and root positions, without selecting behavior or spending resources. Ordinary mouth slots are anatomical capabilities of the paid mouth, not separately created material blocks. The collector has a bounded 32-job safety limit.

Movement capability data lives at:

```text
data/create_bioships/bioship_locomotion/tentacles.json
```

A datapack can replace that resource and use `/reload`. No Java rebuild is needed for its tuning values:

```json
{
  "walls": true,
  "ceilings": true,
  "grip_reach": 1.7,
  "comfortable_clearance": 0.7,
  "direct_distance": 24,
  "quick_nodes": 220,
  "search_nodes": 1600
}
```

Invalid profiles are rejected with a warning; the built-in bounded profile remains available. Future leg-component modules can use `LocomotionProfiles.registerAdapter(profileId, matchesCreature)` to select their own data. This separates geometry/navigation from appendage-specific capability policy. New mechanics still require implementation and tests; data tuning does not.

Navigation checks a supported direct corridor first. If blocked, it searches diagonally with whole-body swept clearance, preferring room around obstacles. After a bounded quick search, it permits expanded wall/ceiling routes. Clear waypoint stretches collapse into straight segments; there are no spline curves or deliberate lateral weaving. Surface contacts require three reachable terrain contacts and do not grant flight.

`BioshipSurfaceContacts` owns terrain contact sensing. `GroundRoute` owns route planning. `BioshipCrawl` and the physics actuator own stance and motion. `TentacleSuckers` shares cosmetic sucker geometry between the leg and grasper renderers.

## Future airship constraints

The locomotion legs are the intended lineage for future flight appendages. Airship behavior is still disabled. Keep one internal mouth in the future hull, with extra exterior mouths for hunting; an internal mouth does not need to migrate outside merely because hunting starts. This is a recorded design constraint, not enabled airship behavior.
