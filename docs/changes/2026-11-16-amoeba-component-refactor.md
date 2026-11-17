# Amoeba component modularization

This is a behavior-preserving refactor of merging, cargo transport and attachment. Amoeba
remodeling is optional and does not become a requirement for other creature/core types.

- Shared `CreatureBodyAccess` exposes the world, physical body and controller position.
- Shared `ResourceAccount` replaces the former nested material-account interface. Existing
  transaction rollback, resource order and emergency reserve behavior are retained.
- `amoeba.AmoebaComponentHost` supplies component-lifecycle state, policies and notifications.
  `AmoebaCoreAdapter` maps the current core to this capability without a second movement state
  or inventory. Other amoeba cores may implement a different adapter.
- Merging, transport and attachment live in `amoeba`; pose adaptation lives in `amoeba.client`.
  These engines do not refer to `LivingCoreBlockEntity`. Client discovery uses the optional
  `AmoebaComponentProvider` capability rather than the living-core class.
- Existing body validation, support checks, digestion routing, costs and lifecycle notifications
  stay in the concrete-core adapter. Serialization keys and packet layouts are unchanged.
- Removed the old account declaration and old-package classes; no compatibility wrappers remain.
  Existing callers explicitly obtain `amoebaComponents()` from the core.

Checkpoints are pushed individually. Production and GameTest compilation passed after each
code migration. Final validation: 61 unit tests and 18 focused GameTests passed.

The regression fixture also verifies a host with independent movement state and unchanged save
format. A timing-dependent inventory assertion now runs at the fusion boundary, before growth
spending; the separate timed checks retain real merge-duration coverage. Notification order
was kept consistent with the original implementation. Full 172-test regression and graphical
QA were outside this focused run. All six tasks are complete and pushed separately.
