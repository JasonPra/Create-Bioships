# Underwater navigation

Approved responsibilities: a reusable 3D route planner finds obstacle-free paths, respiration
owns finite air and the decision to seek air, and the swimming controller follows routes.
Working gills permit underwater breathing. Lungs still require air; small organisms retain
existing mouth/core breathing. Seek air when supplies fall, not on an arbitrary fixed timer.

Prioritized checkpoints:
1. Audit swimming/breathing and tests — 5–10 minutes.
2. Reusable bounded 3D route planner — 10–20 minutes.
3. Full-body water clearance and swimming integration — 15–20 minutes.
4. Routes to breathable air and recovery hysteresis — 10–20 minutes.
5. Submerged obstacle/surface/breathing integration fixtures — 10–20 minutes.
6. Broader regressions, fixes, documentation and final push — 10–15 minutes.
Push each completed checkpoint and report how many remain.

Checkpoint 1 complete: the current aquatic controller already tracks 300 air units, seeks air
below 200, and waits for full recovery before diving. Its movement is local steering (target,
up, four rising sideways options), not obstacle-aware route search. The brain already ranks
SURFACE before ordinary goals. Existing tests cover trapped drowning, exposed mouths, active
gills and waterline adaptation. Preserve those rules while replacing local steering with routes.
The new planner will use loaded cells only, bounded search, collision-checked diagonals,
a straight-route fast path, cached waypoints and throttled retries. Air routes must be able to
move sideways under roofs before ascending. No flight permission or underwater breathing cheat.

Checkpoint 2 complete: reusable bounded A* searches 26 directions with corner checks, a direct-path fast path and collision-checked waypoint simplification. Five new unit cases cover direct travel, 3D detours, roof openings and bounded failure; all 79 unit tests passed. No gameplay integration is claimed at this checkpoint.

Checkpoint 3 complete: loaded-world clearance checks the rotated whole body, rejects terrain/lava/unloaded cells and caches block samples per query. The swimming controller follows cached routes, checks the next travel segment, throttles retries and exposes water waypoints through existing F3+P diagnostics. Four live tests and all 79 unit tests passed, including a collision-checked submerged detour. Air-seeking still uses the previous controller until checkpoint 4.

Checkpoint 4 complete: reusable AirSupply owns finite air and low-air/full-recovery hysteresis. Swimming follows collision-checked routes to a position where an actual mouth (or lone core) reaches air, including sideways movement under roofs. Working gills cancel unnecessary air-seeking. Existing Air/SeekingAir NBT keys are preserved; routes rebuild after reload. All 83 unit tests and six focused live breathing/navigation tests passed.
