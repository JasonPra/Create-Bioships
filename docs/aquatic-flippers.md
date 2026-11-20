# Aquatic flippers

The Flipper is a horizontal, side-mounted component. Place it against a sturdy
body face. It has a block-model inventory item, a fleshy mounting pad and a broad,
segmented chitin-edged paddle using the core's base palette. It waterlogs normally.
The socket touches its supporting block; the moving tissue stays connected at its
joints. At rest it extends two blocks from the supporting face. The socket remains
one placed component; the paddle does not require another block or a second placement.

The creature's appendage census recognizes it through `create_bioships:fins`.
Adding or removing one updates `hasFins`, invalidates routes and changes the
controller's cached positions. The block does not carry a separate movement ticker
or persistent behavior configuration. The sublevel remains authoritative.

A brain enables swimming support whenever the body and at least one flipper are
submerged; a behavior card is not required to hold depth. Wander can request the
existing three-dimensional water routes. The same `BrainMotorControls` interface
serves future aquatic behaviors. A flipper alone does not unlock ground movement.
Dry, brainless and dead creatures receive no active swimming support.

`core.flippers.FlipperGeometry` provides a dedicated paddle stroke rather than
walking-leg inverse kinematics. Three fixed-length sections give a broad power /
recovery stroke with restrained tip flex. Mirrored attachments row together, and
idle strokes are small. There is no leg tuck, knee bend, or IK seed switching.
Network interpolation blends directions while preserving each section's length.
The renderer fixes roll against the creature's local up on every mounting side.

`compat.letitwalk.FlipperController` retains Let It Walk's `WalkerPhysics.substep`
for mass-aware suspension and movement. Native support descriptors are used only
by that physics adapter; their joint solvers do not animate the paddles. The
adapter supplies the brain's desired depth, allowing underwater waypoints.
Ground-leg posture is disabled while aquatic support is active.

The paddle meshes receive disposable stroke samples. Block entity rendering
uses the opaque/cutout stage, including behind glass and before assembly, with
bounds that include the extended paddle. Samples are not saved as a blueprint or
a second simulation authority. Collision uses a static two-block footprint in the
attachment direction, rather than separately simulated paddle sublevels; it does
not track every lateral part of the moving stroke. Leave space around flippers.

Recipe: packed flesh around a bone and chitin armor, yielding two flippers.
