# Armor and locomotion

Chitin Armor contributes 20 armor points; armored Crab Legs contribute 10. A single grid-owned spent-armor ledger is persisted in `create_bioships:armor`. Only hits on an armored component consume this pool. Remaining damage reaches health normally. Vanilla armor-bypassing damage still bypasses it. There is no automatic armor repair.

Removal and replacement preserve spent points, including when capacity falls below spent points. Adding new capacity does not erase prior armor damage. Core and eye weak points remain unarmored.

Sable's native block-property resource sets armored material mass to 1.5, relative to its default block mass of 1.0. This affects the actual body's mass and inertia, rather than a display-only statistic.

## Compact legs

Armored Crab Legs are separate horizontal attachment blocks. They need a full solid backing block; the living core and other leg sockets cannot support them. The owning grid caches sockets, calls Let It Walk's `JointChain` IK and `TerrainProbe`, and applies native Sable contact impulses. No hidden Let It Walk core or per-leg block ticker is used.

A brain exposes `BrainMotorControls.move(grid, worldVelocity, yawRate, leaseTicks)` and `stop(grid)`. Requests expire and are rejected after death or without an active brain. A zero-velocity request explicitly requests standing; simply adding a brain does not activate the legs. Uncommanded, airborne, and dead creatures have passive articulated legs and ragdoll normally.

`CompactLegCollisions` owns three native oriented box bodies per limb, connected to the creature and each other with bounded articulated joints. Joint motors require an explicit brain request and terrain contact by the hull or limbs, allowing recovery from a collapsed pose. Suspension lift additionally requires multiple actual supporting limbs. No joints attach these crab legs to the world. Terrain anchoring is reserved for future climbing appendages. Step clearance rises for higher landing surfaces; jumping is not implemented.

The renderer follows refreshed native body poses and F3+B outlines the entire segments. Length changes grow gradually and rebuild collision shapes from their previous physical poses, preserving the lower surface during growth. Toes target the terrain surface; they are never driven below it to generate lift. Suspension accounts for bone mass and turning inertia. Native joints and objects are removed with their sockets or owner. Core-shell pixels tile at their original density rather than stretching along the bones.

Normal right-click with the **Ossified Wrench** extends a compact leg through heights 1, 2, and 3. Facing stays unchanged. Thickness and actual segment lengths increase together. The third setting provides approximately three blocks of clearance when sockets are attached on the lowest body row. Further clicks do nothing. Create's standard wrench does not extend the leg; shift-wrench still removes it.

The visible development fixture has `/leg_test start`, `/leg_test reset`, and `/leg_test status`. START assembles six legs and installs a Wander card in the grid-owned brain inventory. RESET restores the unassembled subject.

## Native water behavior and development access

All 84 installed Biomancy block IDs and the current components are included in the `body_materials` data-pack tag. Their baseline Sable mass is 1.05, slightly above the native unit mass; armored components override this to 1.5. Extended compact legs use 2.0 and 2.5 at settings two and three. Sable's native fluid collider provides buoyancy and water drag; there is no duplicate water-force controller.

The native water regression compares equal-size core/tissue and core/armor subjects. Both must sink, with tissue sinking more slowly. Physics, collider creation and ray casting are exercised rather than inferred from a mass constant.

The development navigator now includes the locomotion enclosure at x=896, and the development world initializes it automatically. Existing saves get the missing fixture without replacing their other fixtures.

## Removal safety and test tuning

Physics prunes removed sockets before applying limb forces, and uses the facing cached from
validated socket states. Removing a leg between the scheduled gait update and physics no
longer reads block properties from air or leaves that leg's collision bodies active.

The compact-leg development exhibit starts at 1.5× damping with unchanged stiffness. Existing
exhibit subjects receive this adjustment once; higher user-selected damping is preserved.
Subsequent Positioner adjustments are respected. Global limb tuning defaults are unchanged.
