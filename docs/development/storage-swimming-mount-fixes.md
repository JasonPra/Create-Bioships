# Storage, swimming and gill-mount fixes

Five checkpoints: hunting capacity (10–15 min), all-face gill mount exclusion (5–10 min),
registered block physics audit (10–15 min), grown-body diving fix (10–20 min), final regressions
(10–15 min). Continue automatically and push each completed checkpoint.

Checkpoint 1: food counts now include meat buffered in mouths. Hunting checks simulated delivery
capacity in connected digestion queues/storage before selecting prey. Defensive attacks remain
independent. All 99 unit tests passed and two live tests passed: buffered/full delivery queues and
existing well-fed growth hunting. Capacity simulation leaves inventory unchanged.
