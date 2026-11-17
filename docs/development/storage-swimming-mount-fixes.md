# Storage, swimming and gill-mount fixes

All seven checkpoints are complete. Final validation: **99 unit tests and 203 live GameTests
passed**, all source sets compile, and package/Mixin/whitespace checks pass. No graphical client
animation review is claimed.

Final behavior:
- Hunting pauses for usable buffered food or full delivery queues; disconnected-mouth food is
  excluded. Defensive attacks remain independent.
- Growing beyond land-leg capacity reduces swim speed instead of disabling propulsion.
- All gill faces and storage sacs reject mounted legs/flippers; eyes reject partial/sac hosts.
- Sacs align their opposite-facing connector with adjacent intestines without replacing inventory.
- Valid mounted flippers take priority over intrinsic starter limbs during swimming, including
  partially deployed sets. Intrinsic core support remains available for early organ assembly.
- All 115 registered biological blocks and 9093 states were audited. Packed flesh and hull use
  ordinary mass/displacement; no global buoyancy/mass overrides were introduced.
- Existing classes were retained as requested; no attachment-policy class or tags were added.

Five checkpoints: hunting capacity (10–15 min), all-face gill mount exclusion (5–10 min),
registered block physics audit (10–15 min), grown-body diving fix (10–20 min), final regressions
(10–15 min). Continue automatically and push each completed checkpoint.

Checkpoint 1: food counts now include meat buffered in mouths. Hunting checks simulated delivery
capacity in connected digestion queues/storage before selecting prey. Defensive attacks remain
independent. All 99 unit tests passed and two live tests passed: buffered/full delivery queues and
existing well-fed growth hunting. Capacity simulation leaves inventory unchanged.

Checkpoint 2: shared mounting validation rejects every gill face, in addition to cores and lungs.
Growth, manual placement, reconfiguration and mount repair use this rule. The live full-face socket
regression passed for all six directions, including diagonal/pipe/orphaning exclusions.

Checkpoint 3: enumerated every registered Biomancy/bioship block and every block-state variant.
The live audit passed; complete mass ranges and maximum displaced volume are recorded in
`biological-block-physics-audit.txt`. No special high-buoyancy override exists for packed flesh or
hull. Grown-body swimming still uses a land-load cutoff, which is addressed in checkpoint 4.

Checkpoint 4: reproduced the cutoff with load=46, land capacity=32, wet=true, swimming=false.
Swimming now continues above land capacity with reduced speed as load grows. The grown-body
fixture, real roof-detour air recovery and working-gills idle-submersion tests all passed. No global
block mass/displacement changes were made: packed flesh and hull already have ordinary values.

Additional requested repairs: keep existing classes, fix sac orientation and exclude sac hosts for
eyes/legs/flippers (10–15 min); prefer mounted flippers over core fallback limbs (5–10 min).
These precede the final regression checkpoint.

Checkpoint 5: retained existing classes as requested. Full-block geometry and explicit protected
organ/sac checks reject partial hosts for legs and eyes. Automatic eye placement and amoeba eye
transport now validate their host. Sacs orient their opposite-facing connector toward adjacent
intestines on restoration and digestive reconnection, preserving native inventories. The six-
direction sac/inventory test and existing eye/full-face regressions passed. No new tags/policy class.

Checkpoint 6: swimming uses valid mounted flippers even while fewer than four limbs have been
deployed. Their actual sockets feed the existing procedural renderer; fallback roots also respect
protected-organ mounting rules. Intrinsic core starter limbs remain available during early organ
assembly; this is separate from placing leg blocks on a core. The focused flipper/socket test passed.
Graphical animation review remains unperformed.

Final regression follow-up: the initial strict fallback rule excluded intrinsic starter limbs once
the first component was collected, preventing support validation and deployment. Restored that
early-assembly fallback while retaining sac/gill/lung exclusions and mounted-flipper priority. All
six focused pickup, starter-support and reconnection checks passed after the correction.

Final food-accounting review: only mouths with a functional intake route contribute buffered food.
A disconnected-mouth assertion verifies that inaccessible meat does not suppress hunting. The
fixture uses an in-plot placement; an earlier eight-block offset lay outside its Sable test plot.

Checkpoint 7 complete: final clean `check runGameTestServer` passed all 99 unit tests and all 203
live GameTests after the support and connected-mouth corrections. Package/Mixin checks and
whitespace checks passed. Updated architecture references and PR #2. Zero tasks remain.
