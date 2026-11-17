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

Task 3 complete: direct-contact members inherit one intact mouth/intestine connection.
Topology updates immediately when a stomach link is removed. Growth counts stomach groups
when deciding how many mouths are needed. Three focused GameTests and all 65 unit tests passed.
The capacity requirement remains all 27 original food queues; intake routing follows in task 4.

## Revised scope: many mouths, shared digestion, coordinated relocation

Approved after task 3: all mouths are intake ports for a digestive network, not exclusive
partners of individual stomachs. Stomachs still share queues by direct face contact only.
An intestine may connect a mouth to a stomach group; it must not share food between otherwise
separate stomach groups. Keep existing saved assignments readable as route hints during migration.

Responsibilities:
- Shared digestive topology discovers physical connectivity and routes food.
- An amoeba digestive-layout planner chooses resource-saving adjacency and cube arrangements.
- Formation detection validates actual, non-overlapping solid 27-block cubes.
- Amoeba transport moves a formed structure together using the sinking animation, with its
  inventories and digestion state intact. A validated move commits or restores as one operation.
- The controller and player UI aggregate existing inventories; they never copy stored food.

Revised remaining checkpoints (supersedes the earlier remaining list):
1. Architecture/migration record — 5–10 minutes.
2. All-mouth discovery over existing physical digestive links — 10–20 minutes.
3. Resource-accounted wiring for additional mouths — 15–25 minutes.
4. Safe queued-food sharing and intake distribution — 15–25 minutes.
5. Resource-saving isolated-stomach adjacency — 15–25 minutes.
6. Solid cube detection and natural arrangement planning — 15–25 minutes.
7. Coordinated whole-structure sinking relocation — 20–30 minutes.
8. Visible intake and player panel — 15–25 minutes.
9. Combined inventory UI and 25% digestion-speed bonus — 20–30 minutes.
10. Integration regressions and fixes — 20–30 minutes.

Checkpoint 1 recorded. Preserve the full capacity of all 27 stomach queues. Never require a
mouth per stomach or grant materials/duplicate inventories during arrangement or relocation.

Revised checkpoint 2 complete: reusable physical intake routing supports many mouths per stomach
and branched intake paths without exclusive assignments. Cached routes validate actual blocks
and tube face flags. Extra mouths no longer increase the stomach-growth quota. Four new pure
routing cases, all unit tests, and two focused live GameTests passed. Automatic construction of
additional intake routes follows in checkpoint 3.

Revised checkpoint 3 complete: extra mouths receive resource-accounted intestine routes to
existing stomachs. Route maintenance preserves all working intake branches; removing one branch
disables that intake without disabling another. The obsolete exclusive-pair regression was
replaced with paid many-mouth routing assertions. All unit tests and three focused live tests
passed. Mouth count no longer dictates stomach count, nor does stomach count dictate mouth count.

Revised checkpoint 4 complete: group intake uses available peer queues; bounded meal balancing
preserves item components, inherited-food provenance and active recipe inputs/progress. Stomachs
can pull from sacks attached to their direct-contact peers. Each real inventory is counted once.
All unit tests and four focused live tests passed, including full-anchor overflow into a peer,
item conservation and inherited food handling. Existing single-stomach capacity remains 576 items.
