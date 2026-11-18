# Armor and locomotion

Chitin Armor contributes 20 armor points; armored Crab Legs contribute 10. A single grid-owned spent-armor ledger is persisted in `create_bioships:armor`. Only hits on an armored component consume this pool. Remaining damage reaches health normally. Vanilla armor-bypassing damage still bypasses it. There is no automatic armor repair.

Removal and replacement preserve spent points, including when capacity falls below spent points. Adding new capacity does not erase prior armor damage. Core and eye weak points remain unarmored.

Sable's native block-property resource sets armored material mass to 1.5, relative to its default block mass of 1.0. This affects the actual body's mass and inertia, rather than a display-only statistic.
