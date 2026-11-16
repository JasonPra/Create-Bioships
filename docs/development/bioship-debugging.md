# In-game debugging

## All worlds

- **F3+B** — sight cones/rays, entity hitboxes, green procedural foot contact boxes, and red active lash-tip collision boxes. Hidden when disabled.
- **F3+P** — server-observed navigation route and a native floating behavior/target/priority display.
- **`/bioship_debug list`** — operator-only list of loaded core UUIDs, physical positions and current actions in the command's dimension.
- **`/bioship_debug inspect <UUID-or-prefix>`** — operator-only snapshot of one uniquely identified core. Use the UUID or a unique leading substring from `list`.

Inspection reports logical coordinates for the core and mouths, physical position, health/food/air, actual chunk-lease ranges, requested tick rate, game time, body height and core elevation, coverage, mass center and support, invalid socket mounts, stomach connectivity, and pending component/core movement. `FaceConnectedComponents` counts grid-face connectivity. `StructuralComponents` excludes leg sockets and surface eyes: an attachment must not act as the only bridge holding body sections together. Output is SNBT text, usable through chat, the server console or RCON; no additional Create goggles panel is displayed.

These commands observe state. They do not choose goals, pay resources, move components or change simulation speed.

## Development presets

Operator commands restricted to their corresponding preset:

- `/bioship_lab reset` — reset the Navigation Lab courses.
- `/bioship_ecosystem status` — living core and population counts, cache count and controller position.
- `/bioship_ecosystem water` — visit the aquatic observation platform.
- `/bioship_ecosystem reset` — reset fixture creatures/cargo, caches, starter components and aquatic enclosure. It retains natural land terrain.
- `/bioship_ecosystem replenish` — immediate capped fish/prey/hostile refill; also scheduled every five in-game minutes.
- `/bioship_ecosystem pulse` — internal gradual population-controller endpoint.

See [Ecosystem Lab](ecosystem-lab.md) for the runtime command-block controller and disabled-command-block fallback. The optional `soakTest` observer is excluded from the released runtime JAR.

## Tick-rate comparisons

Vanilla `/tick rate 20` restores the ordinary target. `/tick rate 10000` requests the maximum target, but actual throughput depends on the machine. Record `/tick query` or elapsed real time and game-time advancement; do not label a run as achieving 10,000 TPS merely because it requested that rate. `/tick freeze` and `/tick step <ticks>` can define comparable intervals without using `/tick sprint`.

For layout regressions, capture intermediate states as well as the end state: a temporarily disconnected body may reconnect by the final snapshot. Use a fresh same-seed world or a copied saved checkpoint for a terrain-controlled comparison. Graphical animation appearance requires separate client observation.

The F3+P hologram also displays the independent body-work selection and queue count.
`/bioship_debug inspect <id>` includes a `BodyWork` compound with `Action`, `Reason`,
`Target` (when selected), and ranked `Priorities`. A transaction wait means another
conserved operation owns the layout; combat alone does not block body adaptation.

For lifecycle investigations, `/bioship_debug inspect <id>` reports `BodyDead`,
`ShipDead`, and `Lineage`. A healthy core on a grid marked dead is an inconsistency
worth capturing; inspection does not clear death records.

Optional dev-launch JVM option: `-Dbioships.traceLifecycle=true`. It logs each
first decay mark with game time, ship/lineage, live-core count and caller stack.
The opt-in ecosystem observer additionally logs layout changes, nearby blasts,
observed deaths and missing creatures. These logs do not modify world rules or AI.
No new in-game command is required.

Deaths now emit one `BIOSHIP_DEATH` server-log line with the fatal cause, game tick,
ship/core identity, food, air, and gill/lung status. Inspecting a retained dead core
also shows `DeathCause` and `DeathAt`. The core's `BodyMemory` stores these fields;
the wreck grid stores `BioshipDeathCause` and `BioshipDeathAt`, and the saved lineage
registry retains the metadata when the core disappears.

Causes: `STARVATION`, `DROWNING`, `ASPHYXIATION` (air available but inadequate
breathing organs), `CORE_REMOVED`, `COMBAT`, `EXPLOSION`, `INJURY`, and `UNKNOWN`.
Living bodies use `NONE`; old deaths without evidence remain `UNKNOWN` with time
`-1`. The first fatal event is retained rather than overwritten by later cleanup.
No new command is required for death logging.

### Container handoff tracing

Optional JVM argument `-Dbioships.traceContainers=true` logs mouth capture/load records and missing destination block entities during physical handoffs. Pair it with `/bioship_debug inspect <id>` and F3+P. It is disabled by default. `Water.WaterContact` in inspection data describes habitat contact; `Water.Wet` remains the stricter swimming-physics classification. No new in-game commands. See [container persistence](container-persistence.md) for native item-component restoration and resource ownership.
