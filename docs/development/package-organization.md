# Package ownership pass

Approved scope: package organization only, with disabled prototypes preserved and disabled.
No new gameplay, shared perception/anatomy extraction or revival of the old feature backlog.

1. Map ownership and gates.
2. Move preserved airship/prototype implementations into `disabled.wip` (client code under
   `disabled.wip.client`). Keep registry IDs and `AirshipPrototype.available() == false`.
3. Group amoeba contracts, core adapter, merge and transport responsibilities in named subpackages.
4. Group registry/bootstrap, networking, integration hooks and developer-world tools.
5. Clean imports/references, regenerate architecture documentation and verify package paths.
6. Run focused regressions and fix failures.

Push each task separately; report the remaining count.

## Mapped boundaries

- Airship controls, hull/room planning, reconstruction, cockpit blocks and flagellum prototypes
  are dormant. Their assets/registry IDs and safe saved-job cleanup must remain available.
- `BioshipBlueprint.physical` and `EngineRoom.storage` are active helpers despite their owners'
  dormant feature roles. Extract them to `integration.sable.BodyCoordinates` and
  `integration.biomancy.NativeStorage` before moving their former owners. No forwarding copies.
- Amoeba capability contracts belong in `amoeba.components`; recipes in `amoeba.merging`;
  cargo and attachment in `amoeba.transport`; the core bridge in `amoeba.core`.
- Concrete core lifecycle and remaining tightly coupled gameplay systems are not wholesale
  moved or made public. Package-private dependencies require deliberate entry points, not
  blanket public visibility. Shared perception/anatomy extraction stays outside this pass.
- Registration/bootstrap belongs in `registry`, diagnostic packets in `network`, development
  worlds/commands in `development`, and external assembly hooks in `integration.sable.mixin`.
- Mixin metadata, source fixtures, serialized names, docs and generated indexes must follow moves.

Task 2 complete: 28 preserved prototype/client classes now live in `disabled.wip` and
`disabled.wip.client`; their pure unit tests follow that ownership. Active coordinate/storage
helpers are separate integration utilities. The availability gate still returns false and
control registration still checks it. Only compiler-identified cross-package entry points were
opened; the intended-cell map remains package-private behind `intendedCells()`.
Production, GameTest and unit-test sources compile. Registry IDs and save tags are unchanged.

Task 3 complete: amoeba contracts live in `amoeba.components`, the core bridge in `amoeba.core`,
recipes in `amoeba.merging`, and cargo/attachment in `amoeba.transport`. The renderer remains
client-only. The existing lifecycle coordinator stays at the legacy boundary; only the adapter's
required cross-package entry points were opened. Production and GameTest compilation passed.
