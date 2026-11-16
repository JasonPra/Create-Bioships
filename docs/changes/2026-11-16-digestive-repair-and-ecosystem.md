# Digestive repair, anatomy and ecosystem follow-up

## Digestive integrity

Every stomach is checked by the existing 20-tick connection service. The router retains its preferred layout but can search bounded three-dimensional detours when the old horizontal layer is blocked. It counts retained/retired intestine stock before buying additional tubes at one Flesh Bit plus one Elastic Fiber each; initial stomach bootstrap connections retain their existing one-time exemption.

Other stomachs' intact tubes are protected, but invalid recorded routes are never grown through an occupied component by another stomach's repair. Destination states are validated before editing and new tube purchases use the atomic material transaction. Reserved gut/storage routes and in-transit destinations cannot be occupied by ordinary automatic placements. Repair shortages feed the normal material-needs calculation. Where a physical detour is impossible, the goal queue can propose moving an obstructing nonessential component through conserved transport, preserving its inventory and state.

## Anatomy and protective growth

A read-only anatomy sensor audits every 20 ticks, including while other work is committed. Buried, unsupported or unbalanced mounted legs propose repositioning through the existing priority queue. Safe relocation recomputes the socket's outward face. A leg socket now retains a small physical root collision volume, so moving it as a standalone Sable body is not massless.

Vital mouths and stomachs prefer compact positions before discretionary covering. Covering tissue and tier-one armor can be relocated to free space for internal organs or to reopen a regrowth port. Existing movement safety, support, inventory preservation, tube-budget and world-clearance checks remain in force.

Exposed vital parts are covered with paid Packed Flesh before tier-one `living_hull` armor is offered. Mouth openings remain accessible. Necessary leg-strength improvements are funded before accepting extra weight; after covering and armor are complete, an affordable discretionary first strength upgrade can be proposed. These are eligibility/priority rules, not a hard-coded sequence that overrides safety or food. Tier-two armor remains a preserved future asset, and the airship stage stays dormant.

## Behavior liveness and playerless ticking

Optional mouth configuration is offered only when an orientation change or a safe funded relocation can execute. An impossible cosmetic rearrangement no longer outranks wandering indefinitely. Brain snapshots restore the saved action as well as its reason, fixing goggles that labeled a configuration reason as `IDLE`.

The normal-world soak exposed vanilla's 300-tick idle-dimension suppression: region tickets loaded chunks, but a dimension with no players or vanilla forced chunks stopped ticking block entities. A living bioship's active loading lease now prevents that suppression; ordinary view/simulation ticket ranges and release-on-removal/death behavior remain unchanged. There is no test-only force-loading workaround.

## Packaged ecosystem and tests

The new **Bioship Ecosystem Lab** preset uses normal noise terrain and Savanna vegetation/carvers, a quarantine perimeter, real cache inventories, sparse capped replenishment of prey/hostiles, and normal spawn/light checks. Undead added by the preset receive a non-dropping leather helmet. See [the preset and soak-run guide](../development/ecosystem-lab.md).

The first free-roaming run found the ticking stall; a diagnostic restart also exposed a native Sable center-of-mass panic. Those runs are failures, not passing evidence. A fresh corrected run completed 3,600 ticks with two functioning repeatedly-fed creatures, continued resource production, growth, cache collection and behavior changes, with zero probe failures. Final regression and updated-preset results are recorded below after completion.

## Incremental verification checkpoints

This work is being published in small batches. A checkpoint is not a final release or a claim that all integration tests or graphical animation QA pass.

### Batch 1 — gill exposure and underwater growth

- Automatic component placement reserves the open face of existing gills, as well as intestinal routes.
- Respiratory organs and intestinal routing may displace replaceable water. Solid terrain and lava remain blocked; ordinary dry-growth checks are unchanged.
- This first checkpoint includes the previously unpublished dependent implementation: anatomy and digestive repair, ecosystem/aquatic lab, swimming adaptations, respiratory organs, and appendage collision debug rendering.
- Prior full-suite baseline: 51 unit tests passed; 114/120 required GameTests passed. Outstanding failures involved maze/wall navigation, respiratory growth/exposure, and digestion/resource accounting. Visual animation QA is pending. Updated batch results follow when verification completes.

### In-game debugging reference

| Control | Purpose |
| --- | --- |
| F3+B | Toggle sight diagnostics and hitboxes. This checkpoint adds green world-space procedural foot-anchor boxes and red active lash-tip collision boxes. |
| F3+P | Toggle server-observed navigation routes and the behavior/target/priority hologram. |
| `/bioship_lab reset` | Reset the existing Navigation Lab fixture; restricted to that preset, operator permission required. |
| `/bioship_ecosystem status` | Report Ecosystem Lab bounds, cache count and supplemental population limits; operator permission required. |
| `/bioship_ecosystem water` | Teleport to the enclosed aquatic lab observation platform; restricted to Ecosystem Lab, operator permission required. |

No new command was added specifically for the gill fix. The checkpoint's new appendage collision visualization uses the existing F3+B toggle and remains hidden otherwise.

Batch 1 verification: 51/51 unit tests passed; 117/120 required GameTests passed. Both respiratory checks passed (exposed/covered gills and sustained-use paid growth). Remaining failures are organ material accounting, parallel stomach product retention, and wall climbing. The elevation maze passed this run, but its earlier intermittent failure remains under investigation. The build's overall integration task remains red because of those three failures. Runtime JAR inspection found no GameTest or wild-probe classes. Graphical animation QA remains pending.

### Batch 2 — digestive regression fixtures

The organ-conversion conservation test now includes flesh invested in lungs, alongside paid legs. The parallel-stomach test supplies the lungs required by its larger body, resolves live stomach block entities after relocation, and checks outputs as soon as both native recipes finish instead of assuming products remain unspent after a 1,000-tick wait. It still asserts separate paste storage, core transfer of non-fuel outputs, and a single paste burn without cross-stomach depletion or trust rewards. This batch changes test fixtures and assertions, not digestion recipes, material prices, or metabolism.

Batch 2 verification: 51/51 unit tests passed; 119/120 required GameTests passed. Both digestive checks and both respiratory checks passed. Wall climbing remains the single integration failure in this run; intermittent maze navigation is still being investigated. Visual animation QA is still pending.

### Batch 3 — retain real wall support between tasks

An established wall/ceiling grip is now checked while idle as well as while moving or reaching. Completing a collection task no longer releases tentacles from a still-present surface. The grip continues to depend on actual surface detection, so removing the wall still causes a fall. Failure diagnostics now report mouth acquisition and current behavior alongside physical state.

Verification: the complete build passed, including 51/51 unit tests and 120/120 required GameTests. The real-wall climb/collection/removal test and stepped glass maze both passed. A single full pass does not resolve the earlier maze intermittency; realistic simulations, repeat navigation checks, and graphical animation QA remain pending.

### Batch 4 — stricter normal-world observation (failed simulation)

The optional development probe now requires all expected starters to be observed, survive, digest, receive repeated feedings, and produce resources. It reports survival/production counts separately and records identity, position, health, air state and conserved component movement, with more frequent underwater samples. This tooling remains excluded from the runtime JAR.

Fresh normal-terrain land/water run, seed 8675309, normal difficulty, player-sized chunk ranges, 3,600 ticks: 3 observed, 3 developed digestion, 3 repeatedly fed, 2 alive at completion, 2 resource-producing, 1,888 pork offered, 2 probe failures. This is a failed simulation, not a pass. One creature repeatedly selected surfacing without progress; the aquatic creature lost digestive parts and was no longer alive at completion. Investigation continues with the expanded telemetry.

Queued lab usability correction requested during this batch: deterministic capped command-block replenishment independent of ordinary spawner light/player restrictions, an ecosystem reset command, and starter cores/components included by the actual preset rather than only the optional observer. Validate the preset with the probe disabled.

### Batch 5 — usable runtime ecosystem fixtures and reset

- Replaced ordinary fish spawners and darkness/player-dependent supplemental spawning with a repeating command-block controller. It attempts one fish per 200 ticks (cap 16), one prey per 600 ticks (cap 12), and one hostile per 1,200 ticks (cap 4). Existing populations count toward caps; natural world spawning remains enabled. Safe collision/support/water checks remain. Controller undead receive sunlight helmets. A shared, gated server fallback is provided when command blocks are disabled; its separate ordinary-server verification follows in the next batch.
- The normal preset now seeds two land cores and an aquatic core, each with nearby Bio-Forge/Decomposer/Digester components. The optional observer no longer creates hidden extra starters.
- Added `/bioship_ecosystem reset` and `/bioship_ecosystem replenish`. Reset replaces in-arena/fixture bioships and their cargo, restores caches and starter components, and rebuilds the swimming enclosure at its saved elevation. Natural land terrain is retained; use a fresh same-seed world for pristine-terrain comparisons. Reset queues native Sable removal instead of destroying physics bodies inline, and releases creature chunk leases.
- `/bioship_ecosystem status` now reports actual living core, fish, prey and hostile counts plus the command controller position. `/bioship_ecosystem water` remains the observation-platform teleport. All ecosystem commands require operator permission and the matching preset. F3+B, F3+P and `/bioship_lab reset` retain their existing functions.

Verification: full build passed with 51 unit tests and all 121 required GameTests. New checks cover replenishment/cap, no spawner dependency, stable basin reset elevation, default starter creation without the observer, and cleanup/reseeding. An ordinary fresh dedicated server without `-PbioshipSoak` or the observer, with command blocks enabled, reported three living starters and 13 fish. After killing the fish, the controller replenished two within the observation interval; reset restored three starters and nine fish (eight initial plus one scheduled addition). Forced save and clean shutdown succeeded without the removed-body exception. Natural animals numbered 27, so the controller correctly added no more prey above its cap. No graphical animation QA or biological-soak readiness claim is implied; the earlier surfacing/organ-loss failures remain open.

### Batch 6 — ordinary-server fallback and restart verification

Restarted the saved ordinary dedicated-server world with command blocks disabled, no players and no optional observer. All three living starters loaded. Fish reached the cap of 16; after removing all 16, the shared server fallback produced two replacements within the observation interval. Three living cores remained, and forced save/clean shutdown succeeded. This verifies the fallback and persisted fixture/chunk-loading behavior, not the unresolved biological soak or animation appearance. No gameplay code or commands changed in this verification batch.

## Additional regressions reported during incremental testing

- Leg sockets/root geometry can appear to attach by corners or thin intestine surfaces. A full-face mounting invariant is under implementation for growth, player placement, relocation, support scoring and legacy repair.
- Successive screenshots show increasing organ height even on dry land, eventually with apparent separation of upper parts. Queue a dedicated reconfiguration-stability investigation: visibility/elevation gain must be balanced against actual support, compactness, connection length and ongoing digestive connectivity; reject repeated marginal-benefit moves and test complete sequences over time. The screenshots alone do not establish a deliberate snorkel-building behavior.

- User stress-test condition: high `/tick rate`, described as maximum speed, rather than `/tick sprint`. Reconfiguration/physics sequencing must be checked at both ordinary 20 TPS and accelerated tick rates, without treating acceleration as proof of the cause.

### Batch 7 — full-face leg attachments

Shared mounting checks now require a directly adjacent full supporting face. Socket growth, manual placement, relocation, hypothetical support layouts and transport commits reject diagonal/corner contacts and thin pipe supports. Transitional procedural roots now select real exposed full faces, and invalid sockets do not contribute mounted support. Body relocation may not orphan additional sockets. The anatomy audit repairs legacy invalid sockets even before digestion and before four legs exist. If a host disappears during transport, the actual cargo remains detached rather than being lost or attached to air.

Verification: complete build passed with 51 unit tests and all 125 required GameTests. Added coverage for all six supporting directions, diagonal/thin-pipe rejection, orphaning body moves, transitional root geometry, conserved relocation of an existing diagonal socket, and host removal during transport without cargo loss. Earlier normal-speed navigation cases still pass. Graphical appearance and accelerated-tick reconfiguration remain pending. No new commands or toggles were added in this batch.

### Batch 8 — layout diagnostics and tick-rate reproduction

Added operator commands usable in ordinary worlds: `/bioship_debug list` and `/bioship_debug inspect <UUID-or-prefix>`. They report actual loaded state without choosing behavior or displaying a new goggles panel. See [the complete debugging reference](../development/bioship-debugging.md). `BodyTopology` supplies shared face-connectivity analysis; this checkpoint uses it for inspection and tests, not yet to change rearrangement eligibility. Structural analysis excludes leg sockets and eyes, which must not act as bridges between body sections.

A copied generated-world checkpoint was used for normal and accelerated-target cases. Each reset starters while frozen, bootstrapped a mouth, filled all nine mouth slots twice with pork, and ran two 600-tick intervals using `/tick rate` plus controlled `/tick step` intervals (not sprint). At 20 TPS the measured intervals averaged 16.0–19.6 TPS, peaked at two blocks tall, and temporarily split when a stomach supporting the raised core was moved. The final grid reconnected through a leg socket; this is not a sound structural bridge. At a requested 10,000 TPS the measured intervals averaged 28.5–41.9 TPS, peaked at three blocks tall and ended with two disconnected grid sections. Neither run reproduced the full tower shown by the user, but both exposed a structural rearrangement failure. [Recorded observations](../development/qa/2026-11-16-layout-rate-observations.json).

Verification: complete build passed with 51 unit tests and all 126 required GameTests, including topology analysis for corners, bridge removal in transit, preserved support, repair of an existing split and rejection of leg sockets as structural bridges. Normal-world command execution was exercised in both rate cases. This is instrumentation/reproduction, not a claim that the rearrangement bug is fixed or that graphical animation QA passed.

Additional queued constraints: grown sockets and transitional body legs must avoid the core, mouth teeth and gill intakes; bare-core temporary legs retain their separate bootstrap behavior. Gills may relocate to make space but their new intakes must remain exposed. Habitat-aware shape objectives should favor compact supported land anatomy and efficient streamlined water anatomy, accounting for sustained use, actual need and resource/support costs rather than continual marginal rebuilding.
