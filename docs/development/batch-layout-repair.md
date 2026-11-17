# Coordinated mouth/layout repair

All six checkpoints are complete. Final clean validation passed **100 unit tests and 210 live
GameTests**, all source-set compilation, package/Mixin and whitespace checks. Graphical animation
QA was not performed; the uploaded 7.5-second clip showed a paused game menu.

Final behavior:
- Every mouth reserves its tooth-facing opening against growth, relocation and intestinal routing.
- A blocked mouth requests one coordinated layout plan. Affected mouths, ordinary stomachs, sacs,
  eyes, legs and necessary surrounding tissue are recalled before planned deployment begins.
- Core/respiratory organs and geometrically formed stomach multiblocks remain deployed.
- Anatomy checks the prospective layout, mouth-route quotes, apertures, support and connectivity,
  and orders recall/deployment so parts retain physical connections and valid attachment hosts.
- Embedded flesh is preserved. Inventories live in exactly one organ, cargo body or stock entry;
  the saved plan contains states/slots and progress, not duplicate inventory snapshots.
- A blocked deployment returns actual cargo to stock and waits for its reserved destination to
  clear. Unrelated stored cargo can coexist with a repair. Core reload preserves the job.
- No valid plan means no recall. Work is bounded, and completed layouts are checked for stability
  rather than repeatedly starting individual-organ rearrangements.
- Recall/redeployment use shared absorb/grow animation rules and existing Sable transport.

Approved responsibilities: existing anatomy code plans and validates the repair; assimilation owns
recalled inventory and persistent progress; component transport owns animated recall/redeployment.
No new feature coordinator or attachment-policy class was requested.

Checkpoints: 1 audit (5–10m); 2 aperture prevention (5–10m); 3 validated batch plan (10–15m);
4 conserved recall (10–15m); 5 planned deployment/failure retention (10–15m); 6 regression tests
and final push (10–20m). Continue automatically and push each completed checkpoint.

Checkpoint 1: mouth openings have local geometry checks, but the shared placement gate only
protects gills/gut paths. Configuration looks primarily at the first mouth. Existing stock preserves
inventories but opportunistically settles components instead of holding a reserved batch plan.
Recall uses real Sable cargo and existing merge animation. Grouped stomach transport forbids
single-member recall; it must remain intact during a repair. Respiration and support must survive
recall, and no plan may duplicate inventories as both saved snapshots and active stock/cargo.

Checkpoint 2: shared placement now protects every mouth tooth-facing cell, and intestinal route
search avoids those openings. All 99 unit tests and two focused live aperture/water checks passed.

Checkpoint 3: anatomy plans all recalled slots and final orientations before mutation, reserves
mouth/gill faces, checks final support/connectivity and chooses a recall sequence that leaves the
body connected at every step. Grouped stomachs and respiratory organs remain deployed. The live
blocked-mouth planning/inventory non-mutation test passed.

Checkpoint 4: assimilation persists one recall/deploy cursor and slot-linked stock; inventories
exist only in the live organ, its cargo, or stock, never in the saved layout plan. Participating
organs pause inventory processing. Ordinary body edits and harvesting cannot compete with the
repair. The full recall/redeployment test passed, including core-state reload and inventory
conservation. Deployment hardening and obstruction tests are the next checkpoint.

Checkpoint 5: redeployment uses host-before-appendage ordering, reserved cells, real owned cargo
and shared grow/absorb animation curves. A newly obstructed destination returns its actual cargo
to slot-linked stock, then waits to retry the same destination; it never clones an old snapshot.
Three live tests passed, including multi-mouth repair with dependent eyes/flippers and blocked
deployment followed by reload/resume. All 100 unit tests passed. Planner work is bounded to 64
parts, 256 candidates per part, 2048 candidate checks and 64 route quotes per attempt.

Checkpoint 6 complete: all 100 unit tests and 210 live GameTests passed. Added checks cover
secondary-mouth aperture reservation, read-only planning, fully enclosed mouths, reload and
inventory conservation, embedded tissue, ordinary stored cargo, blocked deployment recall/resume,
multiple mouths with dependent eyes/flippers, formed-stomach safety and post-repair stability.
All source sets compiled; package/Mixin and whitespace checks passed. Updated PR #2 and generated
architecture references. Zero tasks remain. No new in-game commands were introduced.
