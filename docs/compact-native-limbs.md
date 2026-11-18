# Compact legs: native limb integration

A player still places one `crab_leg` socket. On a creature, that socket owns three real Sable sublevels containing internal `compact_limb` collision tissue. The internal block has no item, loot, or visible block model. It cannot accept player block attachments. The existing compact renderer draws its exoskeleton from the physical segment endpoints.

## Responsibilities

- `SubLevelLegs`: grid discovery, lifecycle observation and pose packets.
- `CompactLimbController`: creates/restores native `BuiltLeg`/`LegRuntime` descriptions, translates brain requests, and connects physical segment hinges through Sable's constraint API.
- Let It Walk `WalkerBrain.tick`: gait, foothold planning, terrain and support-polygon evaluation.
- Let It Walk `BuiltLegDriver.solve`: joint-chain IK; `updateActualFeet` supplies physical endpoints.
- Let It Walk `WalkerPhysics.substep`: body suspension, gravity compensation and balance.
- Sable: collision geometry, physical joints, mass and saved segment poses.

The previous custom root-lift impulses, equal-contact force division and independent pitch/roll impulses are removed. Ordinary legs have no constraints to the world. Brainless and dead creatures retain articulated physical segments but receive no powered suspension. A living creature with a valid brain and land legs maintains its idle standing posture without a behavior card or movement request. Stopping movement or allowing a movement lease to expire returns to that posture.

The adapter supplies a stance preference through native lean controls when socket lengths differ. It does not apply its own body forces. Physical hinges use finite native motors and the existing Positioner stiffness/damping settings. This avoids driving tiny segments with the large modular limbs' motor settings.

## Saved state and editing

`create_bioships:compact_limbs` on the root saves each socket's location, height, current extension and native runtime/segment identities. Each physical segment records its root/socket/bone identity. Creature membership shares health, armor, owner, trust, hunger and brain state. Internal collision tissue adds no health or armor capacity; hits resolve armor through the player-facing socket.

Height changes grow geometry in place using native IK to retain both socket and distal contact continuity. Geometry edits update segment poses; routine locomotion does not teleport the hull or use a world anchor. Loading saved limbs reuses their IDs and waits for temporarily unloaded segments instead of allocating duplicates. Sable removal observers distinguish unloading from permanent deletion.

Our wrench and Positioner resolve hits on generated tissue to the owning socket. Removing a socket deletes all three generated sublevels and uses the normal Create-based component drop path. Removing a compact socket or modular joint clears only that component's saved tuning. A replacement receives fresh native segment identities and default settings, including same-tick replacements. Old streamed-in segments are rejected by their saved generation identity.

The Positioner Reset button restores the selected compact socket to height 1, rest angle 0, stiffness 1 and damping 1, or resets the selected modular joint's rest angle and tuning. Reach and held-tool validation also apply to Reset. Shared creature health, armor and trust are unaffected.

Modular feet include a full-height ankle and hinge models include connectors on each attachment axis. Their collision shape includes the ankle. Native pivot separation is checked during standing and walking; the original visible gaps were missing model geometry.

## Regression coverage

- Four-leg layout matching the uploaded DevWorld creature, tested across three startup phases.
- Sustained standing with an empty brain and no movement requests, plus walking, explicit stop and request expiry; repeated clearance, uprightness and terrain-overlap checks.
- Controller reconstruction retaining all segment IDs, creature membership and health capacity.
- Armor routing and wrench removal through a generated segment.
- Six-leg movement, extension to three-block clearance and unequal-length tilt.
- Uncommanded falling/ragdoll, immediate socket removal and native segment middle contact against a narrow post.

Client verification uses a disposable copy of the supplied world. The original uploaded archive is unchanged.

Validation: all 82 required GameTests passed in vanilla and with Epic Fight 21.17.3.1. A fresh copy of the supplied DevWorld was checked in the client with Epic Fight installed. Earlier client save/reload verification preserved the same twelve segment IDs for the saved four-leg creature.
