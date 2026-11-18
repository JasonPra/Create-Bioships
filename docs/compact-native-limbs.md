# Compact legs: native limb integration

A player still places one `crab_leg` socket. On a creature, that socket owns three real Sable sublevels containing internal `compact_limb` collision tissue. The internal block has no item, loot, or visible block model. It cannot accept player block attachments. The existing compact renderer draws its exoskeleton from the physical segment endpoints.

## Responsibilities

- `SubLevelLegs`: grid discovery, lifecycle observation and pose packets.
- `CompactLimbController`: creates/restores native `BuiltLeg`/`LegRuntime` descriptions, translates brain requests, and connects physical segment hinges through Sable's constraint API.
- Let It Walk `WalkerBrain.tick`: gait, foothold planning, terrain and support-polygon evaluation.
- Let It Walk `BuiltLegDriver.solve`: joint-chain IK; `updateActualFeet` supplies physical endpoints.
- Let It Walk `WalkerPhysics.substep`: body suspension, gravity compensation and balance.
- Sable: collision geometry, physical joints, mass and saved segment poses.

The previous custom root-lift impulses, equal-contact force division and independent pitch/roll impulses are removed. Ordinary legs have no constraints to the world. Uncommanded and dead creatures retain articulated physical segments but receive no powered suspension.

The adapter supplies a stance preference through native lean controls when socket lengths differ. It does not apply its own body forces. Physical hinges use finite native motors and the existing Positioner stiffness/damping settings. This avoids driving tiny segments with the large modular limbs' motor settings.

## Saved state and editing

`create_bioships:compact_limbs` on the root saves each socket's location, height, current extension and native runtime/segment identities. Each physical segment records its root/socket/bone identity. Creature membership shares health, armor, owner, trust, hunger and brain state. Internal collision tissue adds no health or armor capacity; hits resolve armor through the player-facing socket.

Height changes grow geometry in place using native IK to retain both socket and distal contact continuity. Geometry edits update segment poses; routine locomotion does not teleport the hull or use a world anchor. Loading saved limbs reuses their IDs and waits for temporarily unloaded segments instead of allocating duplicates. Sable removal observers distinguish unloading from permanent deletion.

Our wrench and Positioner resolve hits on generated tissue to the owning socket. Removing a socket deletes all three generated sublevels and uses the normal Create-based component drop path.

## Regression coverage

- Four-leg layout matching the uploaded DevWorld creature, tested across three startup phases.
- Sustained standing and walking with repeated clearance, uprightness and terrain-overlap checks.
- Controller reconstruction retaining all segment IDs, creature membership and health capacity.
- Armor routing and wrench removal through a generated segment.
- Six-leg movement, extension to three-block clearance and unequal-length tilt.
- Uncommanded falling/ragdoll, immediate socket removal and native segment middle contact against a narrow post.

Client verification uses a disposable copy of the supplied world. The original uploaded archive is unchanged.

Validation: all 80 required GameTests passed in vanilla and with Epic Fight 21.17.3.1. A fresh copy of the supplied DevWorld was checked in the client with Epic Fight installed. Earlier client save/reload verification preserved the same twelve segment IDs for the saved four-leg creature.
