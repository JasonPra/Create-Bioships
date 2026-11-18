# Modular-leg batch

Both compact legs and larger player-built legs are controlled through the brain. The large variant must reuse Let It Walk's native joints, segment assembly and gait rather than replacing them with a cosmetic rig.

Dependency order and working estimates:

1. Shared creature identity and data across linked limb sublevels: 15–20 minutes.
2. Native-compatible joint, segment and foot components: 10–15 minutes.
3. Brain requests driving native gait and joint control: 15–20 minutes.
4. Our own modular-leg setup tool: 5–10 minutes.
5. Visible START/RESET development fixture: 5–10 minutes.
6. Integration regressions, cleanup and final fixes: 10–15 minutes.

The user approved associating limb sublevels with the main creature to share brain, health, armor, trust and death state. Component registration stays in ComponentRegistry; native hooks stay in compat.letitwalk. The setup tool has its own item class under equipment and delegates native actions to compatibility code.

Trust loss remains future work; remind the user when the overall batch is finished.

The current priority is to finish the compact-leg collision/ragdoll and glass/texture repairs, then add the first Wander behavior card with a shared ground/water pathfinder and grid-owned hasLegs/hasFins/hasWallClimbers capability flags. The locomotion exhibit will start with that card installed. The six modular-leg tasks resume after that.
