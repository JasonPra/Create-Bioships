# Grid vision

`core.vision.SubLevelVision` owns a transient cache per loaded, flagged, living Sable grid.
There are no vision tickers on world-placed eye blocks. Each core contributes six face
sensors; land and aquatic eye components contribute their outward-facing sensor.
Injured eyes are excluded, and death clears the controller. This supplies sensory data;
it does not add pathfinding or AI behavior.

`EyeVisionProfile` follows player-scale visibility: the full-light optical budget is at
least 128 blocks, increasing with server view distance (16 blocks per chunk), capped
at 512. Physical-world light, including sky darkening, reduces this budget; aquatic
eyes retain more underwater visibility in darkness. Core sensors use the land profile.

Each creature, including all attached physical bodies, shares **at most two target
line-of-sight queries per five-tick sensing update** (eight per second at 20 TPS).
All eligible eyes contribute viewpoints within their circular 90-degree field of view.
Candidate selection uses position, angle and range before casting; there is no fan of
terrain rays and targets between the old visualization directions remain eligible.
Both chosen eyes track the same target. Existing clear locks receive a small preference;
failed viewpoints temporarily yield to alternatives. Turning, injury, removal and death
invalidate ineligible eyes. No suitable target means no targeting casts. Two viewpoints
improve continuity but cannot see around a wall that obstructs every eye.

`OpticalTargeting` owns this shared budget. `SubLevelVision.detected(root)` exposes
visible target IDs, the observed world point and timestamp across the creature's bodies.
Own combat proxies are excluded; other linked creatures are deduplicated.

`VisionBudgetRay` traverses voxels along one straight line. Each physical metre consumes:

| Medium | Land/core eye | Aquatic eye |
| --- | ---: | ---: |
| Air or clear glass/panes | 1 | 1 |
| Water | 3 | 1 |
| Stained glass/panes (all colors) | 2 | 2 |
| Opaque collision geometry | Stops sight | Stops sight |

Costs apply only to the portion of the ray within each medium; mixed air/water/glass
paths share one remaining optical budget. Overlapping media use the greater cost,
rather than charging twice for the same physical space. Partial solid collision shapes
are clipped geometrically. Tinted glass remains opaque. World terrain and rotated Sable
plots, including the creature's own tissue, participate. Plot traversal is bounded by
occupied local bounds and transformed distances are measured in world metres.
Unloaded physical chunks stop sight without fetching or generating chunks.

At most 256 sensor metadata entries per creature and 4096 globally are refreshed every
five ticks. These are viewpoint/light calculations, not additional line-of-sight casts.
Cached metadata expires after 40 ticks. Results are transient and are not saved.

## Development tools

- **Development Toolkit → Vision rays** toggles selected target rays near the player (96 blocks, at most 16 grids,
  32 recent cones per grid). Cyan means clear to the target/range budget, red means a
  collision, yellow means an unloaded-chunk boundary. Small boxes mark endpoints;
  green boxes mark actual detected targets.
  Snapshots refresh twice per second; the overlay shows sampled world positions.
  Eyes without a selected target ray do not produce a diagnostic fan.
- **`/vision_test start`**, **`/vision_test reset`**, **`/vision_test status`** control
  the visible air/water comparison fixture at x=512 in the development world.
  START and RESET also have command-block buttons and an explanation hologram.
- The development navigator includes the fixture. Existing development saves get
  it on their next startup; RESET recreates it. Use `/time set day` or
  `/time set night` for light comparisons. Hunger is paused on these test subjects.

The overlay uses the same see-through development line renderer as the intestine overlay. Debug
packets contain only bounded ray samples, never inventories or component state.

## Validation

Regression coverage includes all six core faces, both eye profiles, bounded sampling,
self-occlusion and world obstacles, grid rotation, eye reorientation, injured/dead
sensors, day/night light changes, unloaded chunk boundaries, the debug wire codec,
and the visible fixture's reset/start cycle. The toolkit replaces the former debug
keyboard shortcuts. Its toggles are available only in development worlds, and the
server validates toolkit ownership before subscribing a player to debug snapshots.

See [development-toolkit.md](development-toolkit.md) for current tool and client checks.

Long-range regression also places a target 100 blocks away between debug samples,
verifies detection, adds an armor wall to occlude it, and verifies that death clears it.
The statistics hologram includes the actual visible-target count.

Additional regression coverage verifies the shared budget across 100 viewpoints,
blocked-eye handoff, all stained glass colors/panes, mixed underwater range costs,
and physical path lengths through rotated Sable glass.
