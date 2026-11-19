# Wander and shared navigation

Craft a Wander Behavior Card from one Blank Behavior Card and a compass, or take it from the mod creative tab. Put it in an active brain slot. It warms up into a standing request, follows short local routes, and pauses between trips. Removing the card, removing the brain, or death stops its requests. The first supported factory card in the active slots is selected; blank graph cards do not execute yet.

The card identifies a registered program. `BrainBehaviors.register(id, constructor)` creates one runtime per creature; `WanderBehavior` owns that program's decisions and the grid stores its card inventory and behavior status. Motor requests have short leases and an explicit ground/water mode. The existing four-argument movement call remains a ground request.

`SubLevelPathfinder` is shared by all behaviors. It provides bounded A* ground routes with one-block steps and six-direction 3D water routes. Searches memoize terrain checks, expand at most 256 nodes by default, and reject unloaded terrain instead of generating it. Obstacle clearance includes the creature and appendages. Physics controllers register their bounds through `SubLevelLocomotion.registerBounds`; the planner does not know individual leg implementations.

The grid saves `hasLegs`, `hasFins`, and `hasWallClimbers` in SubLevelData. One appendage census is built on load; block-position changes update it incrementally. State-only edits such as extending a leg do not rescan the grid. Data-pack block tags `land_legs`, `fins`, and `wall_climbers` classify types. Climbers also count as ground legs. The fins and wall-climber tags start empty because those appendages have not been added.

Ground routes and motor requests require ground legs; water routes and vertical motor requests require fins. Wander selects water routes only when the creature has fins and is underwater. No swimming actuator or fin block is introduced in this batch. No ordinary crab leg anchors to terrain, and jumping is not implemented.

The locomotion exhibit's START installs Wander, RESET returns the subject to its unassembled state, and hunger stays paused. Existing assembled exhibit subjects receive the card once on loading. Regression coverage includes ground detours/steps, 3D water volumes, search budgets, capability gating, actual Wander movement, card removal, and START/RESET.

The shared planner supports eight horizontal ground directions (including bounded
up/down step variants) and all 26 neighboring directions in water. Edge costs use
actual diagonal distance; the heuristic uses octile distance on land and the
corresponding 3D diagonal distance in water. Neighbor offsets are precomputed and
passability results are reused within each search.

A diagonal is legal only when every crossed orthogonal side/corner cell is clear;
water-space diagonals involving three axes check all six intermediate corner cells.
The native world adapter samples the swept creature envelope between nodes rather
than checking only destination occupancy. Existing distance, expansion, loaded-chunk
and appendage-mode restrictions remain in force. Diagonals do not grant swimming
capability to land legs or permit movement through a blocked corner.
