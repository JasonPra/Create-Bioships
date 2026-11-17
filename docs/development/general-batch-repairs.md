# Gill and attachment batch migration

Approved scope: extend the existing anatomy-plan / assimilation-stock / component-transport split
so blocked gills and invalid component placements use coordinated repair. Remove competing legacy
repair mutations, and test automatic adoption on a malformed saved creature with no batch state.
Three checkpoints: shared triggers/planning (10–15m); migration and legacy adoption (10–15m);
regressions/documentation/final push (5–10m). Push each completed checkpoint and report what remains.

Checkpoint 1 pushed (`f720d07`): automatic detection covers blocked mouth/gill apertures,
invalid eye/leg/flipper mounting faces and misaligned sac connectors. A malformed saved core
without any LayoutRepair state enters repair through normal behavior selection. The three initial
live regressions passed; no test calls beginRepair to force that legacy adoption.

Checkpoint 2 implementation: remove the obsolete individual hatchling-mouth mutator and the
individual leg-repair path. Disconnected sac relocation now enters the same anatomy plan and
assimilation cursor. Valid manual wrench placements remain respected; blocked faces and invalid
mounts still require repair. A primary hatchling mouth has a stable front target rather than
chasing a continually changing outermost organ. Recalled organs may reuse another recalled slot:
all recalls finish before deployment, and host-before-appendage ordering still applies.

Gill safety: respiratory organs stay deployed. A validated exposed intake rotation can apply at
batch start; a fully encased gill instead recalls its blocking tissue. Underwater plans check the
air budget until the intake can open. Gills are never swallowed merely to rearrange their neighbors.

Ownership remains unchanged: anatomy validates the layout, assimilation owns progress and actual
inventory stock, and transport owns cargo and merge animations. Ordinary growth and intentional
perception moves remain separate from invalid-layout repair. No new classes or commands.

Regression updates follow recall + deployment timing rather than the old one-way move timing.
The malformed-save test checks every tick for 80 ticks after repair for a restarted batch or
individual move. Additional coverage includes a completely encased gill, disconnected storage,
legacy diagonal/buried legs, manual wrench placement and blocked front-mouth rearrangement.
