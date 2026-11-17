# Connected stomachs and the large stomach

Approved feature scope: direct-contact stomach sharing, culminating in a solid 3×3×3 structure
of 27 stomach blocks. The user selected direct face-to-face contact only for stomach sharing.
Intestines may connect a mouth to the group, but do not link separate stomach groups together.

## Responsibilities and invariants

- `creature.digestion.DigestiveNetwork`: direct-contact groups and food routing, independent of a
  concrete core. Each stomach remains the authoritative owner of its queue and digestion progress.
- Existing `DigestiveSystem`: integrate the network with the creature's mouth/intestine and
  storage connections. A group functions when at least one member has a valid external connection.
- Formation detector: recognize solid 27-block cubes, choose non-overlapping membership and
  outward-facing intake/panel positions, and detect breakage. No inventory copies.
- Large-stomach controller: aggregate members, capacity, operational state and efficiency.
  Formation requires the real 27 blocks; only valid formed members receive the bonus.
- Panel/menu: show group digestion and storage state and provide access to its combined inventory.
  Intake: route incoming food into available member queues.

Total food capacity is the sum of the 27 original queues, not a smaller replacement queue.
Stored food, item components, inherited/salvaged provenance and work already in progress survive
formation, dismantling and reload. Transfers conserve items and reserve inputs for active meals.
Initial efficiency setting: 25% faster digestion with unchanged recipe inputs and outputs.
Unformed direct-contact groups share food but receive no formation bonus.

## Prioritized tasks and estimates

1. Record architecture, formation and inventory ownership rules — 5–10 minutes.
2. Direct-contact stomach groups — 10–15 minutes.
3. One valid mouth/intestine connection activates the group — 10–20 minutes.
4. Safe food transfer and load balancing — 15–25 minutes.
5. Solid 27-block formation, breakage and reload detection — 10–20 minutes.
6. Visible intake port and player-access panel — 15–25 minutes.
7. Aggregate status and inventory access UI — 15–25 minutes.
8. Formed structure efficiency bonus — 5–10 minutes.
9. Integration/regression tests and fixes — 20–30 minutes.

Estimated total: 105–180 minutes. Push after each completed task and report remaining tasks.
The old creature-feature backlog stays cancelled. This document authorizes this feature only.

Task 1 complete (`8243b92`): ownership, capacity and formation rules recorded.
Task 2 complete: a Minecraft/core-independent immutable direct-contact graph is implemented.
Four unit cases cover face-only links, inherited group connectivity, a 27-member cube, immutable
snapshots and splitting when a link is removed. All 65 unit tests passed before integration.
Live stomach integration follows in task 3; this checkpoint does not yet change gameplay.
