# Creature front and waypoint steering

The root living sublevel saves its anatomical front in `create_bioships:state.front`.
The direction is local to the body, not fixed to a world compass direction. Supported
fronts are the four local side faces. Missing/invalid legacy values initialize to
south (+Z), preserving the earlier Wander convention. Linked physical bodies
resolve the root's front rather than keeping separate anatomical headings.

Wander transforms this saved front through the body's current pose, turns it toward
the next waypoint, and reduces travel while turning. Targets behind the creature
cause a turn before forward travel. Progress in heading prevents a legitimate turn
from triggering the stuck timer. Collision clearance remains authoritative: a
blocked turn no longer falls back to translating sideways without turning.
Swimming requests retain vertical velocity; heading control concerns horizontal yaw.

The development toolkit has a Front arrows toggle. Nearby root markers are sent
only to enabled, authorized viewers; ownership/invitation checks and revocation
follow the same server policy as other diagnostics. Arrows use each client's current
render pose and are visible through opaque tissue. Other players do not see the
arrows unless they enable their own authorized view.

Shift-use the development toolkit while looking at a main-body side face (within
eight blocks) to set that face as front. Only the owner may edit it. Clicks on limb
bodies or vertical faces are rejected. The normal inventory tool opens its menu.
Changing front cancels the old movement request and route before replanning.

Junior's uploaded world has one main living body, UUID
`9fec9331-cce1-4166-8dca-b350cf8180cd`. Its mouth and forward eye point local north,
so the corrected copy saves north as front. One lung was added on an empty roof
cell, at local plot position `(20481031,129,20481033)`, without replacing a component.
The export preserves all other creatures' records and Junior's original pose,
velocities, health damage, inventories, trust and ownership. Only the root's saved
record changed; lighting/heightmap caches were invalidated for the added block.
A separate disposable native load verified 450/450 HP, correct front, the roof lung,
and an authorized visible arrow. Its measured mass is 88.55, demanding 177.1 air/s;
the requested single lung supplies 40 air/s, so five working lungs are needed to
sustain that mass under the current respiration balance. Validation-only pauses
were not included in the returned world.
