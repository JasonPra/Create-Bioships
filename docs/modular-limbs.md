# Modular limbs

Physical Sable limb sublevels belong to one creature through `SubLevelCreatureParts`.
The root owns health, spent armor, trust, hunger, brain programs, ownership and motor requests.
Physical bodies retain local block censuses, collision geometry and wound locations. Cached
limb capacity and appendage flags contribute to the root without rescanning every block each tick.

Linking preserves existing damage. Severance removes capacity and can kill the creature;
a severed limb is dead material. Unloaded owners cannot be changed through a limb. Permanent
removal of a loaded root severs its loaded limbs. Linked bodies share a corpse decay family,
so protected components wait for ordinary material across physical bodies.

Factory behavior and hunger tick once on the root. Navigation includes linked physical bounds.
Vanilla sweep and Epic Fight collisions select at most one damage target per creature.

## Components

- Armored Limb Joint: native hinge, facing toward its child segment.
- Armored Swivel Joint: native swivel, for another articulation axis.
- Chitin Limb Segment: build the bone between joints using connected blocks.
- Armored Foot: ends a leg; normal terrain collision, without climbing anchors.

Each modular component contributes 10 armor points and uses the moderately heavy armored
material. Shell models tile the core's lower plum skin pixels at normal block density.
Brain blocks expose passive Sable loading dependencies for their linked limb bodies.

## Motor control

`ModularLimbController` uses Let It Walk's assembler, gait, IK and suspension through a
transient API context. It saves limb references on the creature, without placing a hidden
walker core or attaching a kinetic network. Sable hinge constraints connect the actual
block-built segment bodies. Finite joint motors follow IK angles; native forced-pose
teleports are not used. Uncommanded and dead creatures retain bounded, damped joints and
can tumble. Normal feet never receive world anchors. Jumping remains disabled.

Topology edits trigger rebuilding; state-only blinking and wounds do not. The stable
control origin is the creature's core, while brain blocks expose loading dependencies.
Deletion records prevent unloaded limbs from becoming living orphans after their owner
is permanently removed.

## Positioner

The Limb Positioner is a separate item, crafted from a native Let It Walk Positioner and
Chitin Armor. It accepts our modular joints and compact Crab Legs only. It does not alter
native Let It Walk joints or unrelated contraption blocks.

Right-click to open controls. Modular joints retain native scroll adjustment (5 degrees,
or 1 degree with Ctrl). Compact controls set height from 1 to 3 and leg sweep/rest pose.
Both types expose bounded stiffness and damping multipliers. Suspension tuning requires
an assembled sublevel. The Ossified Wrench retains its quick compact-height interaction.

## Development exhibit

The development navigator has a second row with the Modular Limbs exhibit. It is built
at x=1024, z=0, using the preset floor height, bedrock flooring and development glass.
`/modular_limb_test start` assembles the subject, links its native limb segments, pauses
hunger and inserts Wander. `/modular_limb_test reset` removes the entire group and restores
unassembled blocks. Buttons and a holographic explanation are in front of the enclosure.
