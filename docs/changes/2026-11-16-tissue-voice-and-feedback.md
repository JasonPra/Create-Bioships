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

Final counts and checks are recorded after the verification run. Automated tests validate gameplay state, priority eligibility, per-mouth ownership, resource conservation, native capsule preservation, synchronization, feedback lifetime, frame budgets and alphabet input boundaries. A graphical client and listening session are still needed to assess visual appearance and sound quality; headless GameTests cannot certify those subjective details.

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
