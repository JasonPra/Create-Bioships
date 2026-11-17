# Storage, swimming and gill-mount fixes

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
