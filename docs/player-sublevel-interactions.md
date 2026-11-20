# Player interaction interception

`core.access.SubLevelInteractionInterceptor` checks player actions through NeoForge events.
It does not run a ticker or maintain a block blacklist. `RightClickBlock` checks every
`BlockItem`, including modded subclasses, before block/item use callbacks execute.
Both the clicked support and adjacent destination must permit the player. World terrain
outside Sable plots is unaffected. Owners and invited players can build; unassigned
sublevels have no authorized builders. Permissions are checked on the server using player
UUIDs and the creature root's current access list, including articulated child bodies.

`UseItemOnBlockEvent` applies that policy before direct `ItemStack.useOn` and item-use-first
callbacks as well. Multi-block items use the same placement context; no state is placed first
and rolled back by this interceptor. Broad post-placement snapshots are deliberately not used:
chunk loading during placement can capture an unrelated core's blink or other world updates.
The permission check is generic; existing organic-component placement restrictions remain
an independent rule for authorized builders.

`BreakEvent` applies the same permission policy before native block destruction. Living
tissue continues to route punches and weapon clicks through `CombatAdapter`; the permission
interceptor does not cancel attacks. A living creature's components are extracted with the
wrench rather than vanilla mining. Wrench extraction remains owner-only, as before; its
policy now lives in the shared interceptor. Shift-click pickup with native Create or
common-tagged wrenches is also checked before block use. Native corpse mining requires
building access.

## Core separation

`CoreAssemblyRules.changed` queues the exact core cell supplied by placement, native move,
or block-entity load callbacks. At the end of the server tick, that cell is compared with
the persisted primary-core position. Extra cores are handed directly to Sable's single-block
assembly helper. The host's other blocks are never enumerated or assembled as part of this
operation. Sable carries the transform and velocity; permissions are inherited by the new
independent creature. Failed native assembly is retried after 20 ticks.

The queue is transient work, not authoritative layout data. Loading a core schedules its
validation again. Core blinking no longer runs assembly reconciliation. The diagnostic
`cores` enumeration is retained for regression checks, but production placement does not
call it. Healthy core block entities skip recovered-dead-core block counting entirely.

Existing public setup methods and persisted creature identity/primary-position keys remain
compatible. The obsolete `check_cores` flag in older saves is ignored.
