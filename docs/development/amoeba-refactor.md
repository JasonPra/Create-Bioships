# Amoeba component refactor

The user approved this focused refactor and the responsibility split below. No new creature
features or growth/behavior changes are included. The earlier feature backlog stays cancelled.

## Boundaries

- `creature.CreatureBodyAccess`: world, physical body and controller position. No growth or AI rules.
- `creature.ResourceAccount`: material counts, spending and credits. No inventory layout assumptions.
- Amoeba component mechanics: moving actual cargo, merging, absorption and attachment remain
  amoeba-specific. They operate through an amoeba component-host contract, not a concrete core.
- `AmoebaCoreAdapter`: the only component-module adapter that knows `LivingCoreBlockEntity`;
  translates anatomy rules and lifecycle notifications into the existing implementation.
- Rendering remains client-only; physics and serialized movement data remain authoritative.

The amoeba host contract covers only policies/state needed by the existing component lifecycle.
It is not a general creature superclass. Other creatures can use body/resource contracts without
supporting component movement. Existing placement/support/digestion decisions stay in the adapter;
merging/transport do not acquire health, hunting or growth responsibilities.

## Ordered checkpoints

1. Record responsibility boundaries (this document).
2. Introduce body/resource and amoeba host contracts; compile.
3. Adapt merging, transport and attachment callers; compile.
4. Move amoeba implementation classes into a dedicated package; compile.
5. Remove superseded entry points and update references/documentation.
6. Run focused regression tests and fix any failures.

Push each completed checkpoint and report the number remaining. Preserve save/network keys,
component inventories, merge timing, physics behavior and the current world preset.

Checkpoint 2: `CreatureBodyAccess` and `ResourceAccount` are public core-independent contracts.
Material transactions and storage now use `ResourceAccount`; the old nested account interface
was removed rather than retained as a duplicate. Java compilation passed. Body binding follows
in checkpoint 3, where the amoeba adapter and component lifecycle contract are introduced.

Checkpoint 3: merging, transport and attachment accept `AmoebaComponentHost`, not
`LivingCoreBlockEntity`. The cached `amoebaComponents()` adapter projects the existing movement
state, shared resource account, placement rules and lifecycle notifications. No second inventory
or movement state is introduced. All production and fixture callers migrated; Java and GameTest
compilation passed. The next checkpoint changes package ownership, not behavior.

Checkpoint 4: engines now live in `amoeba`; the client pose adapter lives in `amoeba.client`.
`AmoebaComponentProvider` lets rendering discover the capability without identifying a concrete
core class. The concrete-core bridge remains beside the legacy core and its package-private
policies. Both production and GameTest sources compile. Save keys and movement records retain
exactly the same serialized fields; no old-package forwarding classes were introduced.

Checkpoint 5: removed the old nested account contract and all old-package entry points; no
forwarding facades remain. Restricted cargo-finalization helpers to the amoeba package and
exposed recipe definitions as the intended extension point. Removed stale locals/imports,
formatted touched Java sources and regenerated the class index/reference. Production and
GameTest compilation passed. Focused behavioral checks follow in checkpoint 6.
