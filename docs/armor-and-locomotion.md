# Armor and locomotion

Chitin Armor contributes 20 armor points; armored Crab Legs contribute 10. A single grid-owned spent-armor ledger is persisted in `create_bioships:armor`. Only hits on an armored component consume this pool. Remaining damage reaches health normally. Vanilla armor-bypassing damage still bypasses it. Once health is completely full, natural regeneration repairs one armor point
every four seconds. It requires the same minimum hunger (90%) and positive oxygen
as passive health healing. Health has priority, and one healing pulse cannot
repair health and armor together. Paused metabolism and death stop repair; the
existing saved regeneration timer and root-owned wear ledger survive reloads.

Removal and replacement preserve spent points, including when capacity falls below spent points. Adding new capacity does not erase prior armor damage. Core and eye weak points remain unarmored.

Sable's native block-property resource sets armored material mass to 1.5, relative to its default block mass of 1.0. This affects the actual body's mass and inertia, rather than a display-only statistic.

## Compact legs

Armored Crab Legs are separate horizontal attachment blocks. They need a full solid backing block; the living core and other leg sockets cannot support them. Each socket owns three real Sable collision sublevels. The owning grid runs Let It Walk's native gait, IK, suspension and powered pose driver. No hidden Let It Walk core block or per-leg block ticker is used. See [compact-native-limbs.md](compact-native-limbs.md) for the API boundary and saved-state responsibilities.

A brain exposes `BrainMotorControls.move(grid, worldVelocity, yawRate, leaseTicks)` and `stop(grid)`. Requests expire and are rejected after death or without an active brain. A living creature with a brain maintains standing without a behavior card or movement request. Stopping or expiring movement returns to standing. Brainless and dead creatures have passive articulated legs and ragdoll normally.

The native driver determines support and applies its built-in suspension. `CompactLegCollisions` queries the actual narrow collision bones along the entire leg; it does not implement a second suspension system. Crab legs never anchor to the world. Terrain anchoring remains reserved for future climbing appendages. Jumping is not implemented.

The renderer follows refreshed native body poses and F3+B outlines the entire segments. Length changes grow gradually and preserve socket/foot continuity. Native segment objects are removed with their sockets or owner. Core-shell pixels tile at their original density rather than stretching along the bones.

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

The compact-leg development exhibit uses default native powered stance. It no longer applies a custom 1.5× damping override. Positioner stiffness and damping adjust passive ragdoll articulation only; powered walking and suspension use upstream pose driving. Reset restores component defaults, and removal clears component tuning without clearing shared health, armor or trust ledgers.

The Eldritch Maw (`create_bioships:armored_maw`) is a separate feeding-mouth variant. Its overlapping chitin front
adds 20 points to the shared armor pool and uses the armored material mass of 1.5.
Only a hit whose local block face matches the maw's facing direction can spend
armor; the fleshy sides/back and hits without a known face remain unprotected.
Rotating it rotates the protected face. Removal/replacement preserves spent armor.
Its mouth inventory, chewing/breathing, intestine routing, feeding trust and sickness
behavior use the same classes as the Feeder Mouth. Its model has a recessed throat,
staggered hooked teeth and front-only chitin scutes, with packed flesh on the body.

A living assembled Eldritch Maw occasionally emits one dripping-water particle at
its transformed lower lip. Ordinary Feeder Mouths and dead/unassembled maws do not
salivate. This uses the existing mouth animation schedule, with no extra ticker.
