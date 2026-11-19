# Development toolkit

The development preset supplies a **Development Toolkit** in hotbar slot 8 and keeps
the existing exhibit navigator in slot 9. Right-click the toolkit for independent
path, vision-ray, intestine-connection and vanilla hitbox toggles, or open the test
navigator from the same menu. Custom F3+I and F3+V interception has been removed.
The Limb Positioner remains a normal gameplay tool for adjusting limb joints.

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
