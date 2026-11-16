# Airship prototype: preserved, not implemented

The user is redesigning the airship stage and its progression. `AirshipPrototype.available()` therefore returns false. This is an implementation boundary, not a supported setting that promises the old prototype can simply be switched back on.

## Preserved

- `DinghyGrowth`, blueprint reconstruction, engine-room geometry/quotes, cockpit controls, menus, screens, seat models and flight/propulsion prototype source.
- Block and item registry IDs, models, textures and loot definitions. Old saves can resolve those existing components; they do not become flight-ready.
- Historical stage-specific GameTest source, marked `@DeferredAirshipTest` so unavailable-stage scenarios are not registered in the active suite.

## Disconnected from gameplay

- Automatic staged hull construction, engine-room expansion, buoyancy incorporation and flight activation.
- Blueprint reconstruction, interactive prototype controls, remote throttle updates and pilot authority.
- Dormant hull/frame/material reservations, anatomical-growth consumption for that hull, and eye-placement exclusions for the abandoned future layout.
- Airship progression hints in the core HUD. Existing prototype controls instead identify themselves as unimplemented.
- The prototype door entry in the creative tab. Core, goggles and wrench remain available. The current bio-forging recipes are for those active items; no airship-control recipe is introduced.

Existing placed prototype blocks are retained. No world is wiped or rebuilt. Prototype state remains serialized for future migration; loading its stage number cannot activate flight or staged construction. Pending prototype blueprint state remains saved and suspended.

## Still active

Hatchling perception, navigation, hunting/defense, organic component assimilation, digestive networks, shared storage, emergency recovery, supported righting, swimming, drowning, paid legs, repair, death/rot, and developer diagnostics remain active. Building materials stay available to these systems.

All-mouth hunting and new permanent grasper growth remain pending. The next controller refactor should use one decision snapshot and explicit multi-step investment reservations before adding those features. See [architecture review](architecture-review.md).

## Verified checkpoint

Java 21 build, 36 unit tests and all 95 required active engine tests pass. The 23 deferred airship scenarios remain source-only tests. Resource JSON validation covers 205 files, and the runtime JAR excludes development fixtures and uploaded saves.
