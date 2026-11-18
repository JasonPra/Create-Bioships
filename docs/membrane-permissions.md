# Biometric membrane access

Biomancy biometric membranes on a creature grid use one grid-owned permission list.
The owner always passes. Other players and individually selected named mobs require
explicit permission. Mob permissions use UUIDs, not names; another mob with the same
name does not gain access, and renaming an authorized mob does not revoke its access.
World-placed membranes and membranes on ordinary non-creature grids retain Biomancy's
own filter behavior. Creature identity remains valid after death, so doors retain
permissions while the corpse exists.

## Ownership

A player placing a new core becomes the owner when its grid assembles. An existing
owner cannot be replaced by adding a core. Extra-core splits inherit the parent's
permissions. Older creatures without recorded ownership remain unassigned.

In the development preset, the first player to join is saved as the test owner.
Existing test subjects receive that owner on login; subsequent test subjects receive
it on creation. Test resets preserve the saved development owner. Player-created
creatures still use the player who placed their core.

## Nerve Panel

Place a Nerve Panel on the creature and interact with its screen face. The panel has
no independent permission data: multiple panels on one grid manage the same list.
Its model has raised flesh, bone braces and a flickering 16-pixel biomesh lock symbol.
It is available in the mod's creative tab and has a bone/redstone/packed-flesh recipe.

- **Allowed** lists guests and lets the owner revoke access.
- **Players** lists online players, with Add and Owner actions.
- **Named mobs** lists nearby named mobs within 32 blocks. Tooltips expose their UUIDs.
- Filtering and paging work across up to 128 guest entries.
- **Owner** asks for confirmation, then transfers ownership to the selected online
  player. Other guest permissions remain. The former owner loses implicit access
  and editing rights; the new owner can add them as a guest if desired.

Only the current owner can edit. Every request validates the player's identity, grid
UUID, panel existence, dimension, and an eight-block interaction distance. Named-mob
selection is checked against a live, loaded, named mob within range. Read-only panels
cannot claim unowned creatures.

## Implementation

`core.access.SubLevelPermissions` owns persisted UUID data in the grid's user tag.
`compat.biomancy` handles native membrane collision permissions and changed nearby
client snapshots. There are no permission tickers or copied lists on individual doors.
The client cache only predicts collision; the server makes the authoritative decision.

Sable's movement collision normally asks for a context-free block shape. The targeted
`SableMembraneCollisionMixin` supplies the moving entity for biometric membrane shapes,
so actual walking uses the same permission rules as native block collision queries.
Other blocks retain Sable's normal collision behavior.

The simple block is registered in `ComponentRegistry`. Its block, server control and
network messages live under `components.logic.nerve`; screens and shared organic
buttons live under `client.components`.

## Development regression

The navigator includes the membrane fixture at x=640. New development worlds include
it, and existing development saves gain it on startup. A bedrock floor, indestructible
glass enclosure and a full flesh partition provide a two-block-high membrane doorway.
The explanation hologram and START/RESET command-block buttons are outside the chamber.
Named guests Juniper and Truffle have AI paused for repeatable manual checks.

Commands:

- `/membrane_test start` assembles the subject and creates named guests.
- `/membrane_test reset` removes that subject and guests, then restores the setup.
- `/membrane_test status` reports assembly, owner UUID and guest count.

Regression coverage checks automatic placement ownership, UUID identity, native and
Sable movement collision shapes, persistence, owner transfer, authorization, stale and
distant panel requests, packet serialization and fixture START/RESET. Native client
QA additionally checks first-join development ownership, actual named-guest selection,
client permission synchronization, and the owner walking through the membrane.
