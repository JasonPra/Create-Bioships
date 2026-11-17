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
