# Path and behavior debugger

Press **F3+P** in the world to toggle bioship path/behavior debugging. It works without goggles and independently of F3+B. In this mod F3+P controls this debugger instead of vanilla's pause-on-focus-loss shortcut. F3+B still controls hitboxes and the goggles' sight cones/rays.

The nearest living bioship within 64 blocks receives a holographic panel above its actual bounding box. The panel identifies the ship, its current behavior and reason, named target and coordinates, measured speed, remaining waypoint count, and its top five evaluated priorities. The floating text also shows the movement goal and route status. There is no goggles-style or screen-space debug panel. Other priorities are not invented when an exclusive command or safety branch has taken control.

Priorities show the controller's real band, action and score from its latest evaluation. A need outranks a goal; urgency dominates distance. Scores include the same distance and existing-target preference used when the controller chose. The debugger reads stored observations; it does not call the selector, create targets or request a new route.

| Marker | Meaning |
| --- | --- |
| White | Current physical body anchor |
| Solid gold lines | Actual remaining ground-route segments |
| Green cross | Next waypoint |
| Cyan cross / dashed line | Current movement goal / direction to it |
| Magenta cross / dashed line | Selected behavior target / direction to it |
| Gray route | Stored route while the creature is not walking |

**Dashed lines are directions, not successful paths.** Ground-route statuses include following, partial route, no route, blocked waypoint, unsupported footing, blocked lift, lifting/lowering, blocked first step and finished route. Flying ships are labeled as flight steering rather than displaying a fictitious ground plan. A target can be selected while movement waits for a component transfer or uses an arm within reach.

The hologram faces the camera and route geometry remains visible through terrain for diagnosis. Floating text wraps into a bounded Minecraft-font hologram. Server observations refresh every five ticks while enabled. Stale observations disappear after one second; toggling off stops requests, and leaving a world/dimension resets the toggle. A snapshot contains at most 256 route points and eight ranked proposals.

The integration checks exercise actual route snapshots, candidate rankings, packet round trips, no-goggles access, request throttling, observation without state changes, and the distinction between an unreachable sealed-glass target and a real route.

Zero-waypoint plans now report `in_action_range` or `at_goal` only when the actual physics position satisfies completion; snapping to a hypothetical tile center cannot complete a blocked grasp. Remembered assimilation targets retain their observed machine name, with a last-seen age when old, rather than revealing unseen changes as “Air.” Darkness and the requirement to visually recheck a changed source remain unchanged.
