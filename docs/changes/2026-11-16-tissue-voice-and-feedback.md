# Packed Flesh, voice and creature feedback

This checkpoint adds the following gameplay changes to the active creature. Adult airship progression remains disabled; retained controls and armor assets do not reactivate it.

## Ordinary body tissue and future armor

New body mass, fill, repair, encasing, layout templates and reinforcement use **`biomancy:packed_flesh`**. `BioshipTissue` owns this choice rather than aliasing the existing custom registry entry.

The existing **`create_bioships:living_hull`** remains registered with its original assets for future armor. Embedded organs preserve their actual host block state when relocated or converted. A legacy host flag with no recorded material still restores the old custom hull, preserving old saves.

New **`create_bioships:living_hull_tier_2`** is purple future armor, using the core's purple palette without its eye/gold emblem. Eight distinct 16-pixel tiles and four random rotations give 32 model choices. Every rotated edge matches every neighbor. Its hardness/blast resistance are 4/12. It is available through `/give`; automatic armor stages and a progression recipe are intentionally not introduced here.

## One absorbed larynx per vocal mouth

A larynx is an upgrade of an individual feeding mouth, not a permanent extra external machine. Each mouth needs its own capsule. Assimilated native larynges enter the existing swallowed-stock path and combine directly with an unvoiced mouth when digestion works. Already attached larynges are absorbed and their host tissue is restored. Their complete native saved data, including essence/configuration, stays inside the mouth through transfer and reload.

When a suitable native larynx is already attached or observed and reachable, it takes precedence over manufacturing a duplicate. Otherwise the creature can pay **8 Flesh Bits, 4 Elastic Fibers and 2 Bone Fragments** from shared storage, retaining its emergency budget. This does not create a stand-alone larynx block first.

Voice proposals use the existing behavior queue. Mouth/stomach recovery, usable intestinal connections, missing required digestive capacity, immediate danger and critical food take precedence. Once digestion works, voice is a high-priority upgrade ahead of optional component/cargo growth. The material needs calculation includes each mouth's outstanding voice budget. A voiced mouth cannot be charged twice.

## Vocal palette, hurt and mouth motion

Voice plays at the **mouth's world position**, following its moving Sable body. The client samples Biomancy's native larynx/creature sound events, applies bounded pitch, attack/release and mild pitch bends, and stops playback after the utterance. No dependency sound files are copied into this mod.

The A-Z palette provides 26 distinct creature articulations, with longer vowel-like tones and short rough consonant-like attacks. Lowercase maps to the same entry; numbers, punctuation and non-ASCII input are rejected. This is a foundation for future speech, **not recognizable spoken letter names, linguistic phoneme synthesis, or text-to-speech**.

Operator audition: stand within eight blocks of an already vocal mouth and use `/bioship_voice A`, `/bioship_voice growl`, or `/bioship_voice hurt`. The tool does not grant free organs or resources.

Mouths have separately rendered upper/lower jaws and fleshy lips. Accepted food closes the teeth to show swallowing; close-range hunting bites animate the mouth. Growls and hurt utterances open it for the duration of their vocal envelope. Animation and larynx data survive network synchronization and body relocation. Empty-hand interaction still opens the inventory without a chewing sound.

## Trust, poison and damage feedback

Provisioned non-toxic meals trigger a small positive particle cloud distributed over actual exposed body surfaces. Toxic provisioned meals and player mistreatment trigger negative feedback. Hunted prey and passive starvation do not emit trust-reward/penalty bursts.

Positive and negative bursts share a **60-game-tick cooldown** (three seconds at normal tick rate), preserved during save/transfer. Each burst emits at most **16 particles over at most 20 rendered frames**, one per frame, with a one-second stale-burst expiry and bounded client inbox. Negative feedback uses a brief vocal growl when a mouth has a larynx.

Poison continuously emits vanilla green effect particles across exposed body surfaces while poison remains active. It stops emitting immediately on death or when the poison duration expires, independently of trust cooldowns. Existing particles finish their normal visual lifetime.

Actual accepted damage starts an **eight-tick vanilla red hurt overlay** across body models and a short hurt utterance from a vocal mouth. Mouth jaws receive the same overlay. The hurt path does not use the trust cooldown; ordinary combat invulnerability still rejects duplicate hits as before. A player hit does not play both a hurt grunt and a second simultaneous trust growl.

## Readable systems reference

[Text flowcharts and class signatures](../development/systems-reference.md) and the [paginated PDF](../development/systems-reference.pdf) replace the oversized diagram. Every main source class belongs to a subsystem; the generator rejects unmapped or duplicate class assignments. The reference distinguishes active services from dormant airship source, and documents remaining orchestration limitations rather than claiming a full rewrite.

## Validation

The final Java 21 `build runGameTestServer` run passed **45 unit tests and all 109 required GameTests**. **23 dormant airship-only scenarios remain unregistered and were not run.** Resource checks parsed 241 JSON files, verified 47 PNG images, and confirmed the runtime JAR excludes test classes and private uploaded worlds. `git diff --check` passed. Automated tests validate gameplay state, priority eligibility, per-mouth ownership, resource conservation, native capsule preservation, synchronization, feedback lifetime, frame budgets and alphabet input boundaries. A graphical client and listening session are still needed to assess visual appearance and sound quality; headless GameTests cannot certify those subjective details.

## Expanded implementation: drop-based hunting and appendages

At the user's request this work is delivered in two phases: first a compile-checked implementation push, then gameplay tests and follow-up fixes. The earlier 41-unit/100-GameTest result predates the following additions and does not validate them.

- Hunting and defensive combat share `TentacleStrike`. The live-prey pull, swallowing/drop suppression and synthetic killed-prey meals are removed, including the special extra beef resource credit.
- Native drops remain actual world items. Eligible mobs lacking a meat drop receive a raw meat item; Enderman, Villager, Witch, Creeper, Spider, Illager, aquatic and generic variants have inventory sprites and native Biomancy decomposition recipes. Vanilla meat is preserved rather than duplicated. Undead and iron golems are not prey; allied/tamed protection and defensive retaliation remain independent.
- Nearby accessible meat offers outrank hunting in the existing goal queue. Drops from the creature's own kills enter persistent loot memory immediately, bypassing visual range/occlusion and ordinary three-second sight expiry. Collection still navigates and uses ordinary physical reach. Unloaded drops retain their last known position; collected/expired drops retire, and unreachable goals can yield to other work.
- A direct supported corridor bypasses route search. Obstructed paths use diagonal neighbors, swept whole-body checks and straight-segment simplification. Clearance costs favor broader routes; deliberate lateral body weaving is removed. A bounded quick detour search precedes extended surface search.
- Wall and ceiling contacts use leg capabilities with three reachable contacts. Movement profiles are reloadable data, with an adapter extension point for future leg types. This is not flight progression.
- Walking legs are separate from food/component graspers. One level-one mouth supplies one gathering slot; its larynx upgrade supplies two. Concurrent jobs bind to specific mouth slots and preserve those bindings. Additional mouths prefer less occupied outward directions; the primary mouth remains forward-facing. A bare core retains bootstrap uptake.
- Shared recessed sucker cups render on locomotion and feeding tentacles. Future grasper providers can contribute slots without replacing the collector, route search or support system.
- The future legs-to-flight lineage and fixed internal-airship mouth are documented, without enabling the dormant stage.

## Follow-up crash and regression corrections

The user's client log identified `IllegalStateException: Not building!` in `AssimilationArmRenderer.renderLegs`. The sucker material switched the shared buffer source and invalidated the skin consumer cached outside the segment loop. All leg/arm segment drawing now uses one shared material-pass adapter that acquires the current consumer at the instant of drawing. A headless regression models shared-buffer invalidation while alternating skin and sucker passes. This verifies buffer ownership; it is not a graphical client smoke test.

The test pass also corrects final-grasp validation on the direct-route shortcut, preserves actual non-grid node heights on walls/ceilings, stores every route node's position, retains kill-drop memory across unloaded chunks, and marks the creature's own kill meat as autonomous food without changing deliberately supplied floor food. Food offers outrank hunting without displacing optional component collection above the pressing-food threshold.

Storage-sac proposals and their executor now preserve the emergency reserve even when the new leg investment competes for the same resources. Overflow fixtures include that additional investment and account for all paid sockets.

The damage source for a swipe now uses the body surface nearest its victim, rather than the default underside proxy, so native retaliation does not target a point under the body. Tests record damage while it occurs instead of requiring damage to remain after passive healing.

Old multi-arm timing fixtures are adjusted for the single bootstrap uptake slot. Additional tests cover drop/recipe policy, persistent unseen kill loot, per-mouth grasper ownership, real wall/ceiling contacts, direct diagonals and blocked corners. Full gameplay verification continues after the user-requested crash-fix push.

## Additional checkpoint

Lash damage now uses a **0.4-block tip box swept between successive attack ticks**, rather than the whole root-to-tip line. Contact along the shaft cannot damage the victim; wind-up remains harmless, aim remains dodgeable, and a strike damages at most once. Tests exercise between-tick contact and rejection of shaft-only contact.

Floor food intake is separated into `BioshipFoodCollection` and binds intake and swallowing animation to the collecting mouth's own stomach. Whole Packed Flesh growth and block restoration now charge **8 Flesh Bits and 4 Elastic Fibers**. The decomposition maximum is lower than that expenditure, preventing a grow/recycle resource loop.

The last all-green gameplay baseline was **106 required GameTests and 45 unit tests**. The subsequent 107-test run caught a two-mouth test fixture consuming its item before the explicit intake assertion. The fixture is corrected; confirmation is pending. Procedural feet, face-aware leg attachments and the new Amoeba Leg block are subsequent work and are not part of this checkpoint.

## Procedural legs and face-mounted Amoeba Leg components

- `create_bioships:amoeba_leg` is a separate locomotion socket with six attachment orientations. It has a block/item model, creative entry, survival drops and a lossy Biomancy decomposition recipe. Walking legs remain separate from mouth-bound graspers.
- Each grown leg costs **4 Flesh Bits, 2 Elastic Fibers and 1 Bone Fragment**. The goal queue offers four legs at **985**, above the larynx's 970 and below missing digestive essentials and urgent food/safety. The planner checks the full outstanding four-leg budget and carrying capacity before starting. Ordinary replacements preserve the emergency reserve.
- The first conversion of an actual native Decomposer/Digester pair recovers **16 Flesh Bits, 8 Elastic Fibers and 4 Bone Fragments** for four starter legs. This is an explicit one-time startup allowance from converted machine bodies. A saved flag prevents it recurring after reload, stomach loss or further conversions. Individual leg growth still pays the standard resource transaction. A saved four-leg startup budget permits that initial investment without demanding the later emergency stock first; it does not mint another allocation.
- Bare cores retain their separate temporary four-leg arrangement. Body-mounted sockets carry their own outward face, root and resting tip. Four completed sockets replace the temporary arrangement; additional sockets can surround the body. Future leg providers can supply their own face-oriented attachment geometry, up to the bounded 32-leg rendering/contact limit.
- `BioshipLegAnchors` senses real world collision shapes. A **0.24-block contact box** at each tip marks its planted anchor. Feet stay in world space while the body moves; a short lifted step transfers a foot to another valid surface after it exceeds its placement tolerance. Opposing step groups avoid lifting both sides together. Contact normals drive wall/ceiling foot orientation, and planted contacts participate in the support-plane calculation.
- Legs use a procedural cubic curve whose initial direction follows the socket's outward face and whose ending direction follows the surface normal. Unsupported tips retract toward their roots. The walking cycle no longer slides feet through a repeating sine loop; swimming keeps its separate paddling stroke. Animation contacts are synchronized when steps change, rather than waiting for the slower ordinary core update.
- **F3+B** exposes green leg-tip contact boxes and red attacking grasper-tip boxes during the damaging sweep. These are collision/anchor volumes, not separate living mobs. Full graphical assessment of foot planting, retraction and bend appearance remains an in-game check.


## Final verification and delivery

The final rerun after shared-digestion cleanup completed with `BUILD SUCCESSFUL`: 45 unit tests and all 109 required GameTests passed. This includes both real-physics glass mazes, per-mouth floor-food routing, tip hit/dodge checks, world-planted feet, six attachment directions, exact four-leg costs, one-time starter allocation/reload protection, emergency-stock protection, native combat and the existing recovery/scavenging scenarios. The source reference covers 132 classes in 48 subsystems and is also provided as a 73-page vector PDF.

The earlier implementation/crash checkpoints were pushed before this final test pass as requested. Graphical appearance, animation smoothness and listening quality still require an in-game client assessment; headless verification does not certify those subjective properties.
