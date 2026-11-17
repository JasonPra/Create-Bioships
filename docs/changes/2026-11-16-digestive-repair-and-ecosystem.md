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

### Batch 9 — structural continuity checkpoint (broader milestone still open)

Automatic carried-organ moves may not split the remaining structural body during transit or at their destination. Core transfers retain their source until an atomic commit, so they use final-layout validation instead of incorrectly treating them as carried organs. An existing corner-connected core can seek a real connection beside/below its current position, rather than only looking upward; protected mature cores remain constrained to protected positions. Core movement no longer prefers height when sight scores are equal. Scaffolding must have a projected sight benefit, and organ compaction uses the stable structural origin instead of chasing the relocated core.

Verification: complete build passed with 51 unit tests and all 129 required GameTests. New cases cover supporting-organ transport, a legacy corner-connected core rejoining its body, and rejection of an equally occluded higher perch. The perch fixture now supplies a real low obstruction and requires a strict sight improvement, rather than demanding elevation for its own sake. The legacy recovery fixture uses a corner-connected Sable object; an entirely separated block is a different physics grid and was not an appropriate fixture for within-body recovery.

Post-patch normal and accelerated-target replays sampled one structural body section throughout. The normal replay ended with a connected stomach and health 100. The accelerated replay ended fleeing at health 28 with no formed stomach, so it is not a passing full-growth test. Combat currently preempts the unfinished digestive work; the coordinated-tree milestone will address that scheduling dependency. The tower itself and long-term convergence remain open. [Replay observations](../development/qa/2026-11-16-layout-structural-replay.json).

No new commands were added in this checkpoint. The loading failure reported during this batch could not be reproduced on retry by the user or in a fresh graphical client at published `fef1689`; its excerpt shows a registry-rollback swim-speed error without the preceding launch context. It is recorded as intermittent/unreproduced, not fixed, and no speculative startup patch was published.

Further queued requirements: prohibit leg mounts on lungs as well as the core and functional intakes; add paid support-face growth and conserved temporary component stashing/redeployment; permit survival-beneficial adaptation during danger using separate coordinated action and body-management trees.

### Leg socket attachment restrictions

- Paid Amoeba/Swimming Leg sockets require direct full-face support and reject the living core, lungs, mouth teeth faces, and gill intake faces. The same policy governs manual placement, automatic growth, and relocation validation.
- The separate transitional procedural supports retain their existing full-face policy. Extending all organ exclusions to those supports immediately prevented early component collection in regression tests; replacing that transition requires the queued paid support-growth/remodeling work. This checkpoint does not claim that milestone complete.
- Expanded the attachment regression across all six directions. Orphaning and legacy relocation fixtures now include a valid structural host rather than relying on a now-forbidden core socket.
- Validation after the transition correction: 51 unit tests and all 129 required GameTests passed (`build runGameTestServer`). Earlier broader procedural-support exclusions failed collection/pathfinding regressions and were removed before this checkpoint. Graphical animation QA and the larger survival/convergence milestones remain open.

### Separate action and body queues

- Moved adaptation offers and native digestive configuration out of the action selector into `BioshipBodyBehavior`, with its own bounded, ranked `GoalQueue`. Combat and escape no longer automatically suppress funded body work.
- `BioshipAdaptation` executes the selected body proposal; `BioshipConfiguration` handles owned BioForge/Decomposer/Digester conversion and mouth/storage accessibility. External collection remains an action-tree responsibility.
- Body work runs before new external collection at the existing 20-tick cadence. Blueprint reconstruction, rescue apertures, core movement, component cargo, and assimilation finalization keep exclusive layout access. These conservative transaction locks protect organs in transit from duplicate regrowth; this is logical concurrency on the server tick, not worker threads.
- Existing material accounts still debit at execution. Survival-related proposals retain their urgency, affordability, emergency-reserve and hunger checks. This does not grant free growth or enable cosmetic growth while starving.
- F3+P holograms show the selected body work and queue count. The existing `/bioship_debug inspect <id>` and navigation snapshot include `BodyWork` with target, reason and ranked body priorities. No new commands.
- Remaining limitations: conflicts still lock the whole layout rather than individual disjoint parts; some action actuators re-evaluate their selector within a tick. Long-running survival/convergence and graphical QA are separate open milestones.
- Validation: 51 unit tests and all 132 required GameTests passed, including defensive native conversion, actual tick-driven emergency mouth regrowth while fleeing with exact debits, and the carried-organ transaction lock. The soak observer now records body work and checks its CONFIGURE progress rather than looking for configuration in the action tree.
- Scheduling milestone complete: independent action/body queues are active. Seven broader groups remain: structural convergence; attachment/intake transitions; paid support remodeling/stashing; habitat-aware shape; survival/ecosystem validation; graphical animation/collision QA; final regression/packaging/docs.

### Ecosystem follow-up after independent body scheduling

- Ran a fresh 3,600-tick ecosystem preset at ordinary tick rate and normal difficulty (seed 8675309, view 12, simulation 10). The observer filled real mouth inventories up to four times and did not override decisions or terrain.
- Biological result **FAILED**: three creatures observed, all three achieved digestion, repeated feeding and resource production, but only two remained alive/recently observed at the end; 1,910 pork offered; one failure. Successful server shutdown is not a survival pass.
- One land creature stopped appearing in live observations after a periodic FLEE sample. The aquatic creature lost/recovered digestive connectivity and ended with missing legs. This trace does not establish whether the land loss was death/removal/unloading or which operation removed the aquatic components.
- Report: `docs/development/qa/2026-11-16-independent-body-ecosystem.json`. Next diagnostic work needs component-layout changes and death/disappearance events, rather than interpreting a 200-tick summary as an exact removal trace. Seven work groups remain; the survival and structural milestones are still open.

### Lifecycle diagnostic checkpoint

- Added read-only `BodyDead`, `ShipDead` and `Lineage` fields to core inspection, plus opt-in `-Dbioships.traceLifecycle=true` caller traces for first decay registration. The separate soak observer records topology changes, death/disappearance and nearby explosions. No default lifecycle logging or new command.
- A second fresh 3,600-tick run again failed biologically (3 digestive/repeatedly fed/resource-producing, 2 recently alive, 1 failure; 1,919 pork). It caught an aquatic death mark at game tick 1 in the deferred source-core removal validator, followed by a healthy replacement core; the death lineage then propagated into cargo. A land disappearance correlated with a real creeper explosion. Report: `docs/development/qa/2026-11-16-lifecycle-trace.json`.
- Isolated ordinary successful-transfer and permanently failed-transfer tests both passed (134 total GameTests, 51 unit tests). The gameplay correction under development targets nested server-task polling before assembly completion; this diagnostic checkpoint does not yet include that correction. Seven broader work groups remain.

### Core lifecycle boundary and persistent causes of death

- Reproduced the lifecycle bug with a regression that polls server tasks while a recorded handoff has no destination core yet. That test failed against the prior implementation. Ordinary successful and permanently failed transfers had both passed, so they were insufficient coverage of the blocking-chunk-load boundary.
- `BioshipCoreLifecycle` validates pending removals at `ServerTickEvent.Post` rather than in a server task, which can run during assembly's chunk loading. Receipted handoffs defer death until that boundary; real unreceipted removal still registers immediately, and failed handoffs still die. This prevents new false tombstones; it does not clear historical death records or resurrect corpses.
- Added persisted fatal causes and game-tick timestamps to body memory, wreck metadata, and the saved death-lineage registry. Causes distinguish starvation, drowning, land asphyxiation, core removal, combat, explosion, other injury and unknown legacy deaths. Fragment inheritance and save/load retain the information.
- Each actual body death logs one `BIOSHIP_DEATH` line with food, air, gills/active gills and lungs. Core inspection includes `DeathCause`/`DeathAt`. No new commands.
- The aquatic creature in the lifecycle trace never developed gills (maximum sampled gill-use score 205 versus the growth threshold 650) and all recorded health samples stayed at 100. It was not a starvation/drowning death; its body was incorrectly put on the corpse decay schedule. A separate test verifies that a genuinely starving gilled creature underwater is labeled STARVATION, not DROWNING.
- Validation: 51 unit tests and all 139 required GameTests passed, including the previously failing handoff boundary and real tick-driven starvation, drowning, land asphyxiation, and gilled-underwater starvation. The normal-world replay after this fix is the next batch. Seven broader work groups remain.

### Retired-owner liveness correction

- The first replay after the handoff change reached 3/3 survival but still contained an aquatic false death record, so its apparent zero-failure result was rejected. The soak now explicitly fails if a healthy core belongs to a grid marked dead.
- A focused startup trace showed the old Sable owner already `isRemoved()`, its retained plot containing a core, and the world lookup returning `void_air`. This was retirement/unloading of the old body object, not loss of the living replacement's core.
- Removal validation now ignores retired owners and uses the owner's actual plot chunk state. An intact core block is not a core-removal death. `setRemoved()` queues validation rather than treating a disappearing BE lookup as immediate death; real block removal still has its explicit death callback.
- Validation: full build, 51 unit tests and all 140 required GameTests passed. Added a retired-physics-owner regression; actual and failed core removal, handoff, starvation, gilled starvation, drowning and asphyxiation tests still pass. Startup evidence: `docs/development/qa/2026-11-16-retired-owner-lifecycle.json`. A fresh normal-world soak is next; seven broader groups remain.

### Fresh ecosystem replay after retired-owner correction

- Passed the strengthened 3,600-tick normal-world soak: all three survived, digested, received repeated feeds and produced resources; 1,971 pork offered; zero failures. No healthy-core/dead-grid flags, decay registrations or actual death logs occurred. The formerly failing retired-owner callback was observed and ignored correctly.
- The aquatic creature retained connected digestion and four legs through the final periodic sample. Its recorded health stayed at 100; gills were not developed in this waterline-breathing scenario. Dedicated tests separately cover effective gills and correctly classified underwater starvation.
- Report: `docs/development/qa/2026-11-16-retired-owner-fixed-soak.json`. Full build/51 unit/140 GameTests remain passed. This is one successful seeded run, not long-duration/high-tick-rate or graphical QA sign-off. Seven broader groups remain.

### Paid mounting-face growth checkpoint

- Added `BioshipMountGrowth` to the independent body queue. If legs are missing or have illegal mounts and there are no free legal sites, it can encase an existing intestine or grow adjacent native Packed Flesh. Encasing preserves the original tube and its paid/manual flags; it does not refund or create tube stock.
- A bounded projection quotes up to four support additions and all remaining legs, any necessary strength tiers, and lung materials if expansion crosses the breathing-size threshold. It preserves the core emergency reserve. Required leg strength is purchased first; each executed tissue addition pays eight Flesh Bits and four Elastic Fibers and revalidates the plan before payment.
- Proposals prefer compact existing-tube encasement, check projected balance/load and actual world clearance, and preserve mouth/gill openings and digestive routes. Growth through a living entity is rejected. The shared leg-site enumerator also rejects solid terrain and lava while allowing water and replaceable plants.
- Material-seeking now includes the quoted mounting investment even before it is affordable. Lower-priority paid body upgrades cannot spend that pending mobility budget; urgent survival work remains eligible. Pressing food needs defer this investment. Quotes are not escrow: every transaction rechecks available resources.
- This is the paid-host preparation step. Temporarily stashing/redeploying an occupied component, removing remaining temporary-support exceptions, and a universal movable-gill exposure policy are still queued. No new commands; body debug labels the work `GROW_SUPPORT` and reports its material needs through the existing inspection/hologram tools.
- Validation: 51 unit tests and all 143 required GameTests passed. New cases cover paid gut-preserving encasement, compact new flesh hosts, exact debits/no tube refunds, resource revalidation, pressing-food deferral, material-seeking for unaffordable mounting work, and protecting mobility funds from vocal upgrades. The first 143-case run passed before the material-needs integration; the updated suite passed after that addition as well.
- Seven broader work groups remain. This checkpoint adds the paid-host prerequisite within the attachment/remodeling work; it does not complete stashing/redeployment or the temporary-support transition. A fresh ecosystem replay follows.

### Paid-mount ecosystem follow-up

- The fresh 3,600-tick run failed biologically: 3 digestive/repeatedly fed/resource-producing, 2 alive at the end; 1,911 pork offered; one failure. The death log identified a land creature killed by EXPLOSION at game tick 934 with food 827 and air 300. This was not starvation or breathing failure.
- The aquatic creature retained connected digestion and four legs; no healthy-core/dead-grid failures occurred. This preserves the retired-owner fix but does not qualify broader combat survival. Report: `docs/development/qa/2026-11-16-paid-mount-ecosystem.json`.
- The next investigation checks creeper hazard sizing and the fatal encounter's movement evidence. No causal attribution to the mounting change is made from this single stochastic run. Seven broader groups remain.

### Creeper escape planning checkpoint

- Planning now reads native creeper explosion radius, charge and remaining fuse. Entity-damage reach is twice explosion power; core escape includes an additional cell of clearance. Actual damage remains native Minecraft damage.
- A bounded earliest-arrival search checks usable tissue, structural move safety, protected mature-core positions and digestive-route affordability. An escape is only considered viable before the remaining fuse expires. Unsafe core scouting yields to the independently selected ground escape.
- Inspection snapshots expose navigation, creeper panic and nearby primed hazard details. The opt-in ecosystem observer samples active threats every five ticks. No new commands.
- Validation: build and all 147 required GameTests passed, including three new creeper regressions and eight repeated mouth transfers with inventory and custom metadata checks. The preceding 146-case run exposed one intermittent inventory-sentinel loss in the existing front-obstruction mouth-transfer test. It did not reproduce in this run; its cause remains open, and no inventory fix is claimed.
- A fresh ecosystem replay remains necessary. Seven broader work groups remain: structural convergence; attachment/intake transitions; paid remodeling/stashing; habitat-aware shapes; extended survival/tick-rate testing; graphical animation/collision QA; final regression/packaging/docs.

### Creeper-checkpoint ecosystem replay

- Runtime `8a124fa` passed a fresh 3,600-tick normal ecosystem run: 3/3 alive, digestive, repeatedly fed and resource-producing; 1,971 pork offered; zero failures or death logs. Report: `docs/development/qa/2026-11-16-creeper-clearance-ecosystem.json`.
- No active creeper threat was sampled in this replay. It therefore qualifies this seeded survival run, not live primed-creeper escape; dedicated GameTests provide the current evidence for that policy.

### Portable inventory and data-reserve containers

- Mouth item drops now carry their block-entity contents instead of also scattering loose food. The same block entity covers ordinary, voiced and aquatic-grasper mouth variations; their larynx and grasper upgrade data travel with the item. Nonempty creative breaks produce a packed item as well.
- Stomach items preserve queued input, digestion state, nutrient paste, tissue reserves and remaining tube stock. Old mouth/sac/path coordinates are excluded from portable items so new placement reconnects locally. The bootstrap flag is retained to prevent farming a fresh free connection on every placement.
- Core item drops carry emergency material reserves and food/growth resource counters. Old body/death records, captain/ship ownership, pending cargo, coordinates and behavior queues are excluded. Added the previously missing core loot table. Components already released by the existing death/abandon path remain separate physical resources; the core item does not copy them.
- `BioshipContainerState` centralizes packing and synchronous state handoffs. Organ transport captures current complete block-entity state before each physical transfer, including native machines and storage sacs. Commit captures the current cargo state; it does not replay a pre-transit snapshot that could recreate consumed resources.
- Native Biomancy storage-sac item persistence is retained. No database is introduced. No new commands.
- The initial 150-case run caught the missing core loot table. Later runs corrected test-harness assumptions about ticking chunks and the vanilla item-component placement step. The subsequent transfer trace and validation are recorded below. Seven broader groups remain open.

### Waterline adaptation and inventory trace follow-up

- Aquatic-use history now samples partial body contact and extended leg segments at the existing one-second cadence. It no longer reuses the stricter center-submersion ratio used for swimming physics. Gill exposure uses habitat contact too; getting a mouth above water remains a separate respiration decision. Dry ground still decreases exposure, and existing duration, resource and reserve gates remain.
- An underwater mouth with clear space in front is anatomically open. Water alone no longer makes the body queue repeatedly reorient it ahead of aquatic upgrades. Solid obstruction checks remain; finite air and exposed-gill checks still determine whether it can breathe.
- The container stress test reproduced again despite packed items: a source mouth had seven items and its marker before assembly, no cargo hydration occurred, and the later cargo snapshot was empty. Transport now resolves block entities through their owning Sable plot holders instead of the generic world chunk lookup, for source capture, cargo hydration, cargo commit and final actor updates.
- Optional JVM flag `-Dbioships.traceContainers=true` logs mouth capture/load and missing destination hydration. It does not change behavior and adds no in-game commands.

- Validation after the owning-plot correction: build, all 51 unit tests and all 155 required GameTests passed. Eight repeated mouth transfers retained inventory and custom metadata; a separate in-transit reduction retained three items rather than restoring the original seven. Packed mouth variants, empty upgraded creative drops, native sacs, stomach reserves/progress/bootstrap accounting, core reserves, partial-water contact and clear underwater mouth orientation all passed.
- The trace identifies the missing initial cargo hydration; the owning-plot correction targets that observed failure. One passing stress run is not extended endurance sign-off. The fresh ecosystem replay follows. Seven broader groups remain: structural convergence; attachment/intake transitions; paid remodeling/stashing; habitat-aware shapes; extended survival/tick-rate tests; graphical animation/collision QA; final regression/packaging/docs.

### Waterline checkpoint ecosystem result

- Runtime `4eea7f2` passed the fresh 3,600-tick ecosystem replay: 3/3 survived, digested, received repeated feeds and produced resources; 1,971 pork offered; zero failures/death logs. Report: `docs/development/qa/2026-11-16-portable-waterline-ecosystem.json`.
- The aquatic creature reached 961/1000 exposure, retained full health/air and connected digestion, and naturally evolved two of four paid legs into paddles while surfacing to breathe. The shoreline creature also developed two paddles. No gills grew during this run. Early insufficient fiber correctly deferred paid upgrades; no materials or decisions were forced.
- This validates the observed waterline plateau and first aquatic-leg progression in this scenario. It does not complete broader habitat-aware shapes, endurance or graphical QA. A longer save/reload replay is next. Seven broader groups remain unchanged.

### Save/reload continuation result

- The saved `4eea7f2` world passed another 6,000 normal-rate ticks without fixture reset: 3/3 survived and produced resources, 535 additional pork offered through real mouth feeding, zero failures/death logs. Both water-exposed creatures naturally evolved all four legs into paddles. No gills developed. Report: `docs/development/qa/2026-11-16-waterline-save-reload.json`.
- The user's 100% observation revealed a separate numeric issue: integer-truncated exposure stalls at 961/1000 under continuous water use. Correcting precision and slowing dry-hop decay, together with five-minute automatic lab refill, form the next batch. Seven broader groups remain.

### Exposure precision, refill scheduling and aquatic isolation

- Exposure now retains fractional precision instead of truncating every sample. Continuous water use can display 100%; the old update stalled at 96.1%. Exact fractional leg/gill histories persist in new NBT fields; legacy integer-only scores still load. Positive accumulation retains its 40-sample timescale; dry decay uses 120 samples, reducing a one-second dry hop to about 0.8 percentage points.
- Paddle-count hysteresis now retains existing paddles while use is strongly aquatic. The previous formula could downgrade a paddle above 90% water use and later pay to regrow it. Sustained land use still reverses specialization; sample-duration, resource, cooldown and emergency-reserve gates remain.
- `/bioship_ecosystem replenish` now attempts an immediate full capped refill of fish, land prey and hostiles. The server schedules the same refill every 6,000 game ticks (five in-game minutes), saves its deadline and restarts the timer on lab reset. Ordinary gradual capped replenishment remains. The repeating command block now calls the separate internal `/bioship_ecosystem pulse`; existing controllers migrate without reseeding creatures.
- Aquatic fixtures gain a three-block bottom-slab apron, opaque outer wall above the tank roof, and bottom-slab wall cap (49×49 outer footprint). Existing land mobs caught beside the old glass are moved outside onto supported dry terrain. The inner glass, water, floor, platform and subjects are retained during migration. Land arena terrain remains unchanged.
- Operator commands: `/bioship_ecosystem status`, `/bioship_ecosystem water`, `/bioship_ecosystem reset`, `/bioship_ecosystem replenish`, `/bioship_ecosystem pulse` (internal controller), `/bioship_lab reset`, `/bioship_debug list`, `/bioship_debug inspect <id>`. Existing F3+B/F3+P diagnostics and opt-in trace JVM flags remain.
- Validation: build, all 54 unit tests and all 157 required GameTests passed. Added/extended checks cover the 100% ceiling, dry-hop hysteresis and eventual land reversal, fractional save/legacy restore, persisted five-minute scheduling, actual bulk fish/land refill and caps, and opaque/slab isolation geometry. A second full run included the added immediate bulk-refill assertions and reset timer handling.
- Seven broader work groups remain unchanged; a saved-world migration replay beyond the five-minute deadline follows.

### Existing-world migration replay

- Runtime `928c843` completed 6,200 additional normal-rate ticks on the existing saved world. Targeted checks passed: the existing aquatic enclosure acquired its opaque wall and bottom-slab apron without resetting its inner glass/tank, both surviving water-exposed creatures reached 1000/1000 use and retained four paddle legs, and the five-minute refill timer rolled over automatically (game time 15,558: two seconds remaining; 15,658: 297 seconds remaining). Missing-population refill and caps are separately covered by the passing GameTest. Existing natural prey above the cap are not culled.
- The shoreline creature developed effective gills and breathed with no mouth air access. The enclosed aquatic subject still had no gills; this run does not qualify its later gill progression.
- Overall survival FAILED: 3 observed/digestive/resource-producing, 2 repeatedly fed and alive at the end, 800 additional pork offered, one failure. A land creature died to EXPLOSION at game time 12,129 with food 1,192 and air 300. No false corpse flag was observed. This is not a clean overall runtime sign-off; land combat/terrain escape remains open.
- Report: `docs/development/qa/2026-11-16-exposure-refill-migration.json`. The 54-unit/157-GameTest build remains passed. Seven broader groups remain: stable layouts; attachment/intake transitions; paid remodeling/stashing; land/water shapes; extended survival tests; graphical animation/collision QA; final regression/packaging/docs.

### Component health and survival mutations (combined batch)

- Replaced the maximum-of-core-or-body formula with additive component health: core 100; ordinary components and packed flesh 4; combined stomach 8; tier-one hull/carapace 12; tier-two hull 20. Container contents do not add structural health. Encased flesh, voiced-mouth larynx capsules and temporarily stored owned organs retain their contribution.
- Health is cached. Structural edits and ownership changes queue one audit per affected ship per server tick; quiet creatures do not repeatedly rescan health. Blueprint transactions preserve the previous total until completion. Permanent removal reduces maximum health, while growth adds its contribution without erasing existing wounds. Stashing, transit, redeployment and save/reload preserve owned health.
- Added a reusable survival-adaptation module. Actual digestion of each creeper-meat item grants one persistent blast point; surviving an actual damaging explosion grants three, once per explosion. Merely holding meat, harmless explosions and lethal explosions award nothing. Existing creeper-meat drops/recipes are reused.
- Blast points cap at 100. Every point adds 0.25 native explosion resistance to all owned blocks, including the core, and reduces explosion health damage by 0.5%, capped at 50%. Traits persist through core save/reload and portable core items. Actual core destruction remains fatal.
- Full-suite validation exposed the wall-climbing height controller stalling below its next ledge. Increased vertical traction only while a real wall/ceiling grip is present; ordinary ground movement remains unchanged. The real wall-climb and maze regressions passed in the final run.
- Validation: build, all 58 unit tests and all 161 required GameTests passed. New checks cover table values, armor, event caching, permanent loss, wounds through stashing/reload, actual digestion, native core/flesh blast resistance, one reward per survived explosion, and no reward for harmless/lethal blasts. This is automated coverage, not a new ecosystem endurance or graphical sign-off. No new in-game commands.
- Seven broader groups remain: stable layouts; attachment/intake transitions; paid remodeling/stashing; land/water shapes; extended survival tests; graphical animation/collision QA; final regression/packaging/docs.

### Structural progress: compaction chamber planning

- Cover relocation now checks that the waiting organ can move into the cell being cleared with valid structural support and an affordable digestive route. A bridging organ that cannot move no longer causes unrelated protective tissue to shuffle. Relocated cover must leave the requested interior radius.
- Equal anatomy-port and paid-leg choices now use stable coordinate tie breaks; compaction scans organs and cover deterministically. No new material grants, AI overrides or commands.
- Validation: build, all 58 unit tests and all 162 required GameTests passed. The new in-engine fixture checks a movable remote organ and then adds a real branch making it immovable, verifying that cover rearrangement stops.
- Seven broader groups remain open; this resolves a local progress failure rather than qualifying whole-body convergence. See `docs/development/batch-status.md`. A fresh normal-world ecosystem replay follows.

### Fresh health/layout ecosystem replay

- Runtime `fa7c5f3` passed a fresh 3,600-tick normal-world ecosystem replay: 3/3 alive, digestive, repeatedly fed and resource-producing; 1,971 pork offered through real mouths; no probe failures or recorded deaths. Land and water creatures retained the new component-derived health totals.
- Report: `docs/development/qa/2026-11-16-health-layout-ecosystem.json`, including per-creature layout samples, peak height/sections/mount faults and final samples. This short run does not close convergence, endurance or graphical qualification.
- Seven broader groups remain. Next focused batch corrects socket-to-foot association in anatomy audits and adds health capacity/mutation points to existing developer snapshots. No additional command is needed.
