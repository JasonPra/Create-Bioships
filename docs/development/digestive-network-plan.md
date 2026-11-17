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

Revised checkpoint 5 complete: an amoeba-only layout planner runs every 20 ticks and moves
isolated stomachs beside an existing functioning group. It uses existing placement/support
validation and inventory-preserving sinking transport; it does not move manually pinned organs.
Joining a functioning direct-contact group does not reserve a redundant dedicated intestine route.
Four focused live tests and all unit tests passed, including food-preserving relocation.

Revised checkpoint 6 complete: solid 27-block cubes are derived from actual stomach owners,
with deterministic non-overlapping membership. Removing the center dissolves formation. The
amoeba planner preferentially fills nearly completed valid cube footprints from existing organs;
formed cubes are protected from single-member transport. Unit tests cover missing centers and
overlap, and three live tests passed including completing a 26-block cube by relocation.
Whole-cube movement follows in checkpoint 7; formed members currently reject individual moves.

Revised checkpoint 7 complete: formed organs use one real 27-block cargo body with the shared
sinking animation, all-member placement preflight, support checks and paid external intake-route
reservation. Current cargo inventories are captured at commit. An obstructed destination rolls
the entire organ back; if both footprints are obstructed, real cargo is retained until restoration
is possible. The move and group offsets persist in existing movement/cargo NBT. Three live tests
passed, including all 27 queues/progress surviving relocation and obstructed-destination rollback.

Revised checkpoint 8 complete: formed cubes display a distinct raised intake ring, a separate
outward player panel, and plain member faces. Roles are derived and reset after breakage, without
changing block IDs or inventories. An existing suitable intake endpoint is preferred for the port.
Three focused live tests passed, including outward-facing roles and reset after center removal.

Revised checkpoint 9 complete: the panel shows 27 real inventories as nine read-only pages of
27 slots (243 total), aggregate food/acid/progress and non-duplicated shared product totals.
32-bit menu values avoid short-packet truncation. Formed members perform five work units every
four ticks: 25% faster with unchanged recipe costs/yields. Live tests filled all 15,552 food item
capacity, inspected all nine pages, checked display isolation/shared resource totals and exercised
the work bonus. Three focused live tests and all unit tests passed. Graphical layout QA is not yet
performed; server behavior and all client source compilation passed.

Intestine optimization clarification: the user reaffirmed direct-contact-only queued-food sharing.
Intestines behave as the physical intake graph for all mouths and connected stomach destinations.
Topology revisions invalidate cached routes immediately on block or connection-face changes.

## Final regression milestone: six smaller checkpoints

Prioritized at the user's request:
1. Fixture isolation — 3–5 minutes.
2. Digestive formation during defense — 5–15 minutes if repair is needed.
3. Wreck-versus-hunting eligibility/priority — 5–15 minutes if repair is needed.
4. Wall climbing — 10–20 minutes if repair is needed.
5. Stepped glass maze — 10–20 minutes if repair is needed.
6. Full validation, changelog and final push — 5–10 minutes.

Mini-checkpoint 1 complete: ordinary regression coordinates are retained; plots intersecting the
fixed End habitat are moved outside its bounds. A blanket relocation had introduced coordinate
sensitivity into unrelated physics checks and was replaced with this narrow overlap guard.
The recovery fixture also establishes daylight and restores the previous shared clock.

This integration push includes the cache/storage and route-reuse fixes tested with isolation:
- Revision-cached physical intake and storage endpoints, including manual sack connections.
- Explicit invalidation on Sable handoffs and block/property edits.
- Group storage deduplication and immediate disconnection; no network-owned inventory.
- Intake-specific tube reuse, additional-mouth route reservation and valid route-hint adoption.
- Replacement-route validation when moving the only intake anchor.
- Whole-organ health conservation and read-only 32-bit aggregate menu values.
- Obsolete exclusive-ownership/empty-GUI assertions updated to the approved shared/read-only model.

The complete working tree passed `check runGameTestServer`: 74 unit tests and all 184 live tests.
All source sets compile. Package/Mixin checks cover 190 types and all 13 hooks. Visual client QA
of the new panel/models remains unperformed and is documented in the changelog.

Mini-checkpoint 2 complete: `defensive_action_and_owned_digestive_conversion_run_independently` passed in the full 184-test run at the established regression coordinates. No further gameplay change was needed. Four checkpoints remain.

Mini-checkpoint 3 complete: `needs_tree_prefers_wreck_over_hunting_and_interrupts_for_escape` and wreck-organ recovery passed in the full run. Eligibility and priority assertions were retained; no priority-weight change was needed. Three checkpoints remain.

Mini-checkpoint 4 complete: `hatchling_legs_climb_a_real_wall_and_fall_when_the_grip_is_removed` passed in the full run. Both climbing and grip-loss behavior assertions remain intact. No locomotion change was needed after restoring the established physics coordinates. Two checkpoints remain.
