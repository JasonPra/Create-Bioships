# Development toolkit

The development preset supplies a **Development Toolkit** in hotbar slot 8 and keeps
the existing exhibit navigator in slot 9. Right-click the toolkit for independent
path, vision-ray, intestine-connection and vanilla hitbox toggles. Use the compass
for test-exhibit navigation. Custom F3+I and F3+V interception has been removed.
The Synaptic Calibrator remains a normal gameplay tool for adjusting limb joints.

Neither development item appears in the creative tab. The toolkit has no crafting
recipe or loot source. Server interactions and debug subscriptions require the
development preset and a toolkit in the inventory. A toolkit inserted into a normal
world inventory is removed. Both reserved slots are protected against clicks,
hotbar swaps, creative replacement, dropping, death and inventory clearing. Existing
development players receive the toolkit without resetting their starter-equipment flag.

Path snapshots show the actual route published by the brain's running behavior;
enabling the overlay does not run a second path search or change movement. Cyan
marks ground routes, blue water routes, yellow the next waypoint/connection from
the creature, green the destination, and gray completed sections. Pauses, behavior
removal, locomotion changes, death and stale snapshots clear the route. Sampling
is opt-in, twice per second, within 96 blocks, capped at 16 creature roots and 256
waypoints each. Debug rendering works through exhibit glass.

The planner still uses cardinal horizontal neighbors with supported height changes;
water also has vertical neighbors. Diagonal planning remains future work.

# Creature footsteps

Let It Walk's native foot-contact callback selects the contacted block's own step
sound for our creature legs. Compact legs use player-like volume/pitch; modular
legs use a louder, slightly lower-pitched profile. Foot contacts and timing remain
native. Ordinary Let It Walk walkers retain their original audio.

# Validation

- All 91 required GameTests passed both without Epic Fight and with Epic Fight 21.17.3.1, including live Wander route publication,
  behavior-removal cleanup, bounded ground/water snapshot codecs and rejection in a normal world.
- A fresh development-preset server verified unique pinned items, hotbar swaps,
  `/clear`, dropping, death inventory clearing and respawn, plus existing navigator teleportation.
- Native client checks exercised item-use opening, every toggle, received live path
  and vision snapshots, navigation and through-glass drawing. The menu uses a plain
  neutral backdrop rather than the component screens' flesh framing.
- Native foot-contact events emitted vanilla stone footsteps on the bedrock floor:
  compact volume 0.15 at normal pitch, modular volume 0.35 at a lower pitch.

## Waterlogging catalogue

The navigator opens a single swimmable waterlogging exhibit containing 98 placeable
Bioships/Biomancy block types. Spaced, labelled samples show normal native behavior,
including special support requirements. The hologram explains the display. There
are no start or reset controls: this is a persistent in-world inspection exhibit.

Bioships non-full components support source-water placement, bucket filling/draining
and fluid ticks. Land and aquatic eyes share these rules and survive submerged
assembly and transfer back to world space. Full solid blocks retain ordinary behavior.

Intestine diagnostics show recognized inventory bounds, occupied/total slots and item
counts as well as connections. Sac ports accept only top/bottom connections; stacked
sacs are consumed bottom first and compact downward within a bounded transfer budget.
All line geometry is flushed before billboard labels to avoid invalidating a shared
vertex buffer between graphs.

## Current batch regression coverage

All 99 required GameTests passed without Epic Fight and with Epic Fight 21.17.3.1.
Coverage includes waterlogging and submerged eye transfers, stacked storage, expanded
chambers, native walking pivots, the membrane grant/cross/revoke/return circuit, and
three arrow-wound targets with a full 30-second delay before shooter placement.

Client smoke checks also exercised the native joint meshes while standing/walking,
through-glass rendering, held tool framing, mouth tooth UVs, multi-grid inventory
outlines and readable labels, the membrane return circuit, and living/corpse arrow
wounds. Temporary client probes are excluded from the release sources and jar.

## Exhibit transparency

Our exhibit TextDisplays defer their normal vanilla renderer until opaque block entities
and custom creature surfaces have finished, before translucent glass. Their transparent
backgrounds no longer write depth ahead of limb/brain geometry. A frame-local queue only
handles Bioships-tagged text, retains the vanilla billboard/interpolation behavior, and
clears after replay. Client checks cover both ordinary and Epic Fight rendering.

## Metabolism diagnostics

**Creature statistics** opens read-only holograms of nearby creatures' mass, hunger,
drain formula/progress, healing eligibility, starvation timing, health, armor, trust,
appendages, movement and actual visible-target count. See [metabolism.md](metabolism.md).
The compass remains the sole exhibit navigator; its redundant toolkit menu entry is gone.

Final branch validation passed all 103 required GameTests both without Epic Fight and
with Epic Fight 21.17.3.1. Client checks exercised equipment models/grips, inventory
previews, transparent exhibit panels and the new vision/statistics payloads. Temporary
client QA probes are removed before building the final jar.

The native arrow fixture retries only lanes whose eye wound has not registered yet.
Its regression permits extra native retry arrows while requiring a living and corpse
arrow on every eye; random dispenser spread is not treated as a combat failure.

Creature diagnostics are private per viewer. Intestine, vision, path, and metabolism
subscriptions are filtered on the server through `SubLevelPermissions.canInspect`:
only the creature owner or an explicitly invited UUID receives its snapshots.
Linked limb sublevels use the main creature's permission list. Unassigned creatures
have no inspection viewers; developer/operator status does not bypass ownership.
Permissions are rechecked for every update, and removed snapshots clear the local
overlay after revocation or ownership transfer. Enabling a display never enables it
for another player. This shared disclosure check can also serve a future player tool.

Intestine holograms render at the end of the world pass, with see-through text, so
opaque contraption geometry and entities do not cover their inventory labels.
