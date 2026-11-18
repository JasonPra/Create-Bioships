# Armor and locomotion

Chitin Armor contributes 20 armor points; armored Crab Legs contribute 10. A single grid-owned spent-armor ledger is persisted in `create_bioships:armor`. Only hits on an armored component consume this pool. Remaining damage reaches health normally. Vanilla armor-bypassing damage still bypasses it. There is no automatic armor repair.

Removal and replacement preserve spent points, including when capacity falls below spent points. Adding new capacity does not erase prior armor damage. Core and eye weak points remain unarmored.

Sable's native block-property resource sets armored material mass to 1.5, relative to its default block mass of 1.0. This affects the actual body's mass and inertia, rather than a display-only statistic.

## Compact legs

Armored Crab Legs are separate horizontal attachment blocks. They need a full solid backing block; the living core and other leg sockets cannot support them. The owning grid caches sockets, calls Let It Walk's `JointChain` IK and `TerrainProbe`, and applies native Sable contact impulses. No hidden Let It Walk core or per-leg block ticker is used.

A brain exposes `BrainMotorControls.move(grid, worldVelocity, yawRate, leaseTicks)` and `stop(grid)`. Requests expire and are rejected after death or without an active brain. This is a control surface for future behavior cards, not a behavior executor.

Normal right-click with the **Ossified Wrench** extends a compact leg through heights 1, 2, and 3. Facing stays unchanged. Thickness and actual segment lengths increase together. The third setting provides approximately three blocks of clearance when sockets are attached on the lowest body row. Further clicks do nothing. Create's standard wrench does not extend the leg; shift-wrench still removes it.

The visible development fixture has `/leg_test start`, `/leg_test reset`, and `/leg_test status`. START assembles six legs and issues a short forward movement request. RESET restores the unassembled subject.

## Native water behavior and development access

All 84 installed Biomancy block IDs and the current components are included in the `body_materials` data-pack tag. Their baseline Sable mass is 1.05, slightly above the native unit mass; armored components override this to 1.5. Extended compact legs use 2.0 and 2.5 at settings two and three. Sable's native fluid collider provides buoyancy and water drag; there is no duplicate water-force controller.

The native water regression compares equal-size core/tissue and core/armor subjects. Both must sink, with tissue sinking more slowly. Physics, collider creation and ray casting are exercised rather than inferred from a mass constant.

The development navigator now includes the locomotion enclosure at x=896, and the development world initializes it automatically. Existing saves get the missing fixture without replacing their other fixtures.
