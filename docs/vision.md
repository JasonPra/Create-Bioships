# Grid vision

`core.vision.SubLevelVision` owns a transient cache per loaded, flagged, living Sable grid.
There are no vision tickers on world-placed eye blocks. Each core contributes six face
sensors; land and aquatic eye components contribute their outward-facing sensor.
Injured eyes are excluded, and death clears the controller. This supplies sensory data;
it does not add pathfinding or AI behavior.

`EyeVisionProfile` defines the initial tuning: preferred environment 24 blocks, other
environment 12 blocks, reduced by ambient physical-world light (including sky darkening).
Aquatic eyes retain more range underwater in darkness. Core eyes use the land profile.

A circular 90-degree cone uses 13 rays. At most four eyes per grid and 80 eyes globally
are refreshed every five ticks, in rotation. Cached samples expire after 40 ticks;
very large populations therefore expose only recently sampled eyes. Results are not
saved. Rays use native Sable block collision clipping, including the creature's tissue,
and stop at unloaded physical chunks without requesting them. Water does not occlude.

## Development tools

- **F3+V** toggles server ray samples near the player (96 blocks, at most 16 grids,
  32 recent cones per grid). Cyan means clear to the sampled range, red means a
  collision, yellow means an unloaded-chunk boundary. Small boxes mark endpoints.
  Snapshots refresh twice per second; the overlay shows sampled world positions.
- **`/vision_test start`**, **`/vision_test reset`**, **`/vision_test status`** control
  the visible air/water comparison fixture at x=512 in the development world.
  START and RESET also have command-block buttons and an explanation hologram.
- The development navigator includes the fixture. Existing development saves get
  it on their next startup; RESET recreates it. Use `/time set day` or
  `/time set night` for light comparisons. Hunger is paused on these test subjects.

The overlay uses the same see-through development line renderer as F3+I. Debug
packets contain only bounded ray samples, never inventories or component state.

## Validation

Regression coverage includes all six core faces, both eye profiles, bounded sampling,
self-occlusion and world obstacles, grid rotation, eye reorientation, injured/dead
sensors, day/night light changes, unloaded chunk boundaries, the debug wire codec,
and the visible fixture's reset/start cycle. Native client QA also exercises the
actual F3+V shortcut and renders both air/water subjects. The shortcut covers Veil's
extra development chunk-debug handler as well as Minecraft's ordinary debug handler.

Release check: all 46 required GameTests passed with Epic Fight installed and with
it absent. The native client captured two grids through the actual F3+V input path.
