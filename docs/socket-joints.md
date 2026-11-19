# Synovial sockets

Powered compact and modular legs continue to use Let It Walk 0.1.5's native gait, IK, suspension and pose driver. A Synovial Socket combines a swivel axis and a bend axis at the same joint. Native IK evaluates both axes, while each limb section remains one real Sable body. Assembly, mass accounting and collision filters use the distinct original physical sections, so additional degrees of freedom do not duplicate mass, health, armor or creature membership.

Compact legs have three independently configurable sockets. The Positioner selects a visible socket with a bounded ray/sphere test, including terrain occlusion. Clicking the compact attachment block instead opens its general leg settings. The mounting hitbox is two pixels shallower so the shoulder socket has its own selectable target; existing joint pivots and segment IDs stay in place. Joint highlights appear while holding the tool. Modular legs use the new `limb_socket` component's own block hitbox. Existing native hinge and swivel components retain their original interactions.

Defaults are a continuous 360-degree swivel and a ±60-degree bend limit. Full swivel wraps its angle across the angular seam without changing physical pose; narrower swivel ranges are bounded around rest. Swivel range adjusts in 15-degree steps from 0 to 360 degrees. Bend adjusts in 5-degree steps from 0 to 150 degrees. Zero locks that axis. **Reset joint** affects only the selected socket; general leg Reset remains separate. Server-side edits validate the held tool, build permission, target identity, joint index and world-space reach.

Limits are saved on the creature root in `create_bioships:socket_joints`. Compact keys identify the attachment and joint number. Modular socket entities retain only a stable component UUID, which survives native assembly moves; the corresponding grid record owns the limits. Native movement is scoped separately from removal, so moving a socket preserves settings while breaking/replacing it clears them. The original native chain and socket identities are saved alongside the expanded solver description, including while a physical section is temporarily unloaded.

The upstream public IK constructor has no per-axis limit setter. `JointChainLimitsAccess` exposes the existing lower/upper arrays only for our decorated chains. The adapter also preserves the original native posture preferences and search seeds on the original axes. The native solver and native pose driver remain responsible for solving and driving the legs. Our adapter maps the two coincident axes onto the same physical child and converts the compact collision bones' rest frames at the existing pose boundary. Ordinary Let It Walk walkers are unaffected.

Unpowered sockets use bounded Sable articulated joints for passive physics; powered operation does not replace native suspension with custom spring motors. Joint limits also constrain these passive joints.

# Route planning

The grid's locomotion profile exposes a cached step-height envelope sampled through native IK. Samples are bounded, recomputed after geometry/limit changes, and reused across route-search nodes. Ground routes reject height transitions beyond that envelope; the current planner still searches one-block steps and does not promise arbitrary cliffs, gaps or cramped passages. Changes invalidate the current Wander route so it can plan again using the new limits. Water and future climbing capabilities remain independently gated.

# Terrain checks

Our socket rigs extend the native solid-block sampling to include every section and finite toe width. Colliding foothold candidates are rejected for these rigs instead of receiving the upstream soft penalty. Native swing interpolation uses a taller, slower arc when crossing a floor-height change. Native gait speed reductions are preserved; movement requests only cap their maximum speed. These changes are scoped to our decorated rigs and leave ordinary Let It Walk walkers untouched.

The physical ledge regression walks a six-legged creature onto a one-block platform while checking upright support and full-section penetration. It supplements the flat-floor standing, walking, resize, reload and removal tests.

# Visible exhibit

New/reset modular-limb exhibits use Synovial Sockets and the existing START/RESET commands. Both leg exhibits have indestructible glass double doors on their front walls. Existing development worlds receive the doors on load without resetting subjects or settings. No existing save is destructively regenerated to replace its older joints.

# Validation

The final vanilla and Epic Fight 21.17.3.1 suites each pass all 90 required GameTests, including physical step clearance, native standing/walking, joint edits/reset, reconstruction, removal, route invalidation, exhibit START/RESET and protected double-door operation. Client checks also cover per-joint versus general selection, modular highlights through glass and saved socket limits after reload.

The final client recheck of the uploaded DevWorld copy preserved all twelve saved physical segment IDs through empty-brain standing, walking and stopping, with the new socket highlights visible. The original upload was untouched.
