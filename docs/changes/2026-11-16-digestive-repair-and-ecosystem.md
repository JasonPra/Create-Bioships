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
