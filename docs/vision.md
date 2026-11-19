# Grid vision

`core.vision.SubLevelVision` owns a transient cache per loaded, flagged, living Sable grid.
There are no vision tickers on world-placed eye blocks. Each core contributes six face
sensors; land and aquatic eye components contribute their outward-facing sensor.
Injured eyes are excluded, and death clears the controller. This supplies sensory data;
it does not add pathfinding or AI behavior.

`EyeVisionProfile` follows player-scale visibility: full-light preferred range is at
least 128 blocks, increasing with the server view distance (16 blocks per chunk), capped
at 512. The non-preferred environment gets half that distance. Physical-world light,
including sky darkening, reduces range; aquatic eyes retain more underwater range in
darkness. Core eyes use the land profile. Unloaded chunks still stop every ray.

A circular 90-degree cone uses 13 visualization samples. Living entities throughout
the cone are selected by range/angle, then checked with target-specific native raycasts;
a target between sample rays is still visible. Up to 16 candidates per eye are checked
per refresh, in fair rotation. Closest-body, eye and center points allow partial exposure
without seeing through walls. Own combat proxies are excluded; proxies for one other
linked creature are deduplicated. `SubLevelVision.detected(root)` exposes the latest
observations across that creature's physical bodies, including target IDs, observed
world point and timestamp. Injury and death invalidate affected observations.

A long ray's self-occlusion check is clipped to the occupied logical plot bounds in the
current pose. It never traces the full player-scale distance into unallocated plot chunks.

At most four eyes per grid and 80 eyes globally
are refreshed every five ticks, in rotation. Cached samples expire after 40 ticks;
very large populations therefore expose only recently sampled eyes. Results are not
saved. Rays use native Sable block collision clipping, including the creature's tissue,
and stop at unloaded physical chunks without requesting them. Water does not occlude.

## Development tools

- **Development Toolkit → Vision rays** toggles server ray samples near the player (96 blocks, at most 16 grids,
  32 recent cones per grid). Cyan means clear to the sampled range, red means a
  collision, yellow means an unloaded-chunk boundary. Small boxes mark endpoints;
  green boxes mark actual detected targets.
  Snapshots refresh twice per second; the overlay shows sampled world positions.
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

Final branch QA: all 103 required GameTests passed without Epic Fight and with Epic
Fight 21.17.3.1. The integrated client also received actual detection markers, rendered
them with the statistics hologram, and verified the closed-eye 3D inventory preview.
