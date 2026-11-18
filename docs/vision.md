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
