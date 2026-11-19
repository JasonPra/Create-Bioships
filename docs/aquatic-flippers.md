# Aquatic flippers

The Flipper is a horizontal, side-mounted component. Place it against a sturdy
body face. It has a block-model inventory item, a fleshy mounting pad and a broad,
segmented chitin-edged paddle using the core's base palette. It waterlogs normally.
The socket touches its supporting block; the moving tissue stays connected at its
joints. Its conservative collision envelope includes the whole paddle sweep.

The creature's appendage census recognizes it through `create_bioships:fins`.
Adding or removing one updates `hasFins`, invalidates routes and changes the
controller's cached positions. The block does not carry a separate movement ticker
or persistent behavior configuration. The sublevel remains authoritative.

A brain enables swimming support whenever the body and at least one flipper are
submerged; a behavior card is not required to hold depth. Wander can request the
existing three-dimensional water routes. The same `BrainMotorControls` interface
serves future aquatic behaviors. A flipper alone does not unlock ground movement.
Dry, brainless and dead creatures receive no active swimming support.

`compat.letitwalk.FlipperController` uses Let It Walk's native `WalkerSwim.row`,
`BuiltLeg`/`JointChain` inverse kinematics, and `WalkerPhysics.substep`. A narrowly
scoped invoker exposes the upstream private rowing method. There is no copied
rowing oscillator or pose teleport driver. The upstream swimming policy targets
the surface; this adapter supplies the brain's desired depth instead, allowing
underwater waypoints. The native suspension still applies mass-aware impulses.
Ground-leg posture is disabled while aquatic support is active.

The paddle meshes receive transient native IK samples. Block entity rendering
uses the opaque/cutout stage, including behind glass and before assembly. Samples
are not stored as a second blueprint or simulation authority. This first flipper
uses a conservative static collision envelope rather than separately simulated
paddle sublevels, so tight passages require clearance for the full sweep.

Recipe: packed flesh around a bone and chitin armor, yielding two flippers.
