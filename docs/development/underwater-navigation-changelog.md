# Underwater navigation and surfacing

- Swimming now follows three-dimensional routes through loaded water, including diagonal travel,
  submerged detours, and sideways travel beneath a roof before ascending through an opening.
- A reusable Minecraft-independent planner owns bounded A* search, collision-checked diagonals,
  direct-route shortcuts and waypoint simplification. Long clear routes avoid search; obstructed
  long routes return explicit continuation segments instead of pretending a search limit is arrival.
- The world adapter reserves clearance for every rotated body block, includes ceiling headroom,
  rejects terrain/lava/unloaded cells, and caches block samples during each query. Following a
  cached route checks the next travel segment; blocked routes stop and retries are throttled.
- The swimming controller uses bounded velocity feedback to account for native buoyancy and drag.
  It can hold depth and command downward travel without blindly adding gravity cancellation.
  Block mass/buoyancy and passive corpse physics have not been changed.
- Reusable AirSupply owns finite air and recovery hysteresis. Air starts at 300; a submerged
  non-breathing creature seeks air below 200 and recovers fully before resuming its dive.
  Working gills cancel unnecessary air-seeking. Lungs still need air; existing small-creature
  core/mouth breathing and large-body lung requirements remain in force.
- Air search targets a physically clear position where an actual mouth, or a lone core, reaches
  breathable air. The test uses real fluid height, rather than assuming water fills an entire cube.
  Sealed water does not grant oxygen or permit passing through a ceiling; drowning remains possible.
- Existing Air/SeekingAir save keys are retained. Waypoints rebuild after reload. Existing F3+P
  diagnostics now show swimming/surfacing waypoints, route status, velocity, air and retry state.
  No new commands or external libraries are required.

Responsibilities are separate: WaterRoutePlanner searches; WaterNavigationSpace adapts geometry;
WaterNavigation owns waypoint/retry state; AirSupply owns the breathing reserve; SwimVelocityControl
owns propulsion feedback. Existing anatomy decides whether mouths/lungs/gills can actually breathe.

Validation includes pure 3D detours, roof openings, bounded failures, long routes/continuations,
air recovery, gill cancellation, saved low air, controlled descent against buoyancy, a physical
roof-detour breach and refill, sealed-pool drowning, gilled submersion and whole-body gap rejection.
Movement checks run against real Sable physics in the headless test server; no graphical animation
review is claimed. The broader existing regression suite is included in final validation.

Integration fixture corrections: merge conservation checks observe the last uncommitted animation
step and the commit boundary. The defense/configuration test explicitly exercises both owned
configuration steps: the timed stomach merge, then Bio-Forge mouth conversion, while defense
remains selected. These fixture changes do not alter merge recipes, animation duration or AI rules.

Final automated validation: **87 unit tests and all 189 live GameTests passed**. The `check`
task also compiled every source set. All 195 source types and four Mixin configs/13 hooks validate.
