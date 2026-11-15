# Create Bioships

Living airships for Minecraft 1.21.1, NeoForge 21.1.266, and Java 21.

## Make and raise a ship

Craft a **Living Ship Core** in **Biomancy’s Bio-Forge → Machines** tab: **1 Living Flesh, 16 Flesh Bits, 8 Elastic Fibers, 4 Exotic Dust, and 1 Eye of Ender**, plus **4 nutrients**. Place it above ground with approximately **9 blocks of width, 21 of length, and 8 above the core** clear. Placement creates a Sable physics object and records the placing player as its creator. The unassembled eye cannot eat. Manual disassembly is blocked; ordinary mining and damage remain possible.

Put an exposed **Bio-Forge**, **Decomposer**, and **Digester** near the eye. A small tentacle grabs each eligible block and pulls its actual Sable body aboard, preserving block state, block-entity data, and contents. All tangible Biomancy block types can be assimilated within range. External targets must have an unobstructed view and open sky: walls, roofs, and underground cover prevent collection. The initial scan reaches three blocks around the core and immediate neighbors of attached organs.

The **first Bio-Forge** becomes a **Bioship Feeding Maw**. One **Decomposer and Digester must both be attached** before they combine into a **Bioship Stomach**: separated machines first move together through the ship’s tissue. Neither machine alone can form it. Both machines’ inputs, products, fuel items, and stored nutrient fuel are retained. Additional Bio-Forges, Decomposers, and Digesters keep their normal crafting/processing roles. Existing completed stomachs remain valid. Existing Bio-Forge biomass is transferred as construction reserves, without restoring hunger or trust. Stored materials and existing decomposition products are retained.

The mouth and stomach grow a physical **fleshy intestine** connection. Tube arms appear only toward neighboring tubes, feeding mouths, stomachs, and storage sacs. Visual connections update when neighbors change and are also checked by food transport. Tubes can be encased in living flesh manually; growing floors automatically hide tubes in their tissue while preserving the network. Empty-hand right-click the mouth to load stacks, or offer individual items by hand. Food remains buffered if the stomach or its connection is missing. The eye provides status rather than eating.

The stomach runs Biomancy’s native decomposition recipes and their output rolls. It regenerates **stomach acid** instead of using nutrient fuel. Right-click it to inspect its acid bar, digestion progress, food capacity, and tissue reserves. Products stay inside the stomach: neither its display nor item capabilities expose extractable outputs.

The first body requires:

| Material | Role | Initial requirement |
| --- | --- | --- |
| Flesh Bits / incorporated flesh | Regular flesh and hull growth | 64 biomass |
| Bone Fragments | Frame and complex structures | 8 |
| Elastic Fibers | Moving appendages and bindings | 12 |

Fresh food supplies nutrition after digestion; its decomposition products supply building material. Incorporating tissue and stored construction materials supplies **no immediate nutrition or trust reward**. Emergency metabolism can later burn stored reserves when food is exhausted. The ship never harvests its attached body for sustenance. Growth forms a tapered oval hull, a forward cockpit, and a separate aft engine/workshop room. Obstructions pause construction rather than being overwritten. Tentacle arms brace the growing hull above nearby ground and adjust its stance for clearance; this supplies ground support rather than levitite flight.

Feed **8 Levitite Blend Buckets** through the connected mouth to incorporate fluid amalgam; empty buckets are returned. Visible, veined buoyancy organs develop under the finished hull. Flight requires the mature body and all eight organs. Solid levitite blocks are not flight food. Aeronautics 1.3.2 calls its amalgam fluid “Levitite Blend.” Use positive vertical trim to lift off.

## Organs, workshop, and food storage

The stomach, crafting machines, lab equipment, and Storage Sacs settle into engine-room bays. Normal Biomancy machines keep their own inventories and behavior. A full room leaves additional components attached until there is space; it does not overwrite occupied bays.

The feeding mouth normally sits inside the workshop. Hunting moves it to an accessible exterior position near the tentacles, turning its opening toward prey. Switching hunting off brings it back inside. Alternate destinations are checked if its usual location is obstructed. Inventory and reserves travel with relocated components.

External collection always uses a tentacle pull. Internal components can descend through the ship’s fleshy floor and emerge elsewhere. This movement follows the construct’s local floor, rather than phasing through world terrain. Moving organs are real temporary Sable objects, retaining their complete data.

Storage Sacs serve as food stores beside the stomach and elsewhere in the workshop. Floor intestines connect each sac to the digestive network. The stomach accepts up to **nine stacks** of raw input. When its intake fills, additional food from the mouth goes into a connected sac; when needed later, the stomach draws food back through the tubes. If every store is full, food stays in the mouth or the player’s hand. Stored fresh food counts as feeding when digested. Sacs retain their normal player-accessible storage inventory; stomach products remain internal.

After the first body stage, **eight further digested meals**, with **8 Flesh Bits, 2 Bone Fragments, and 4 Elastic Fibers** in reserve, develop Biomancy’s **Modular Larynx** in the workshop ceiling. Its future growling/agitation behavior is not wired up yet.

## Physical controls and equipment

- **Thrust tendon:** hold right-click and drag vertically. Positive is ahead, negative is reverse. Shift centers it. No screen opens.
- **Buoyancy tendon:** the same interaction combines ascent, descent, and neutral altitude hold.
- **Helm:** Simulated’s actual steering wheel bundled with Aeronautics, using its native hold interaction and overlay, with crimson fungal wood.
- **Hunting nerve:** a physical latching pad enables hunting; redstone also activates it.
- **Rest:** empty-hand clicking the core rests or wakes a cooperative ship. Sneak-click inspects needs. An aggressive ship can refuse to rest.

Wear **Symbiotic Goggles** for the small signed bioship-control readout. These bone-and-chitin goggles also support the native Create/Aeronautics helm checks. Bioship tendon readouts now require this equipment.

The **Bioship Wrench** is a separate tool. Use an organ once to select it, then use a destination inside the workshop to relocate it. Sneak-use cycles orientation; mouth and stomach support all six directions. Sneak-use in air clears the selection. Relocation and orientation are restricted to the creator. Supported Biomancy machines retain their native facing options. Manual placements are remembered.

Both items are crafted in **Bio-Forge → Tools**:

| Item | Ingredients | Nutrients |
| --- | --- | --- |
| Bioship Wrench | 1 Living Flesh, 6 Bone Fragments, 3 Elastic Fibers, 2 Flesh Bits | 1 |
| Symbiotic Goggles | 1 Living Flesh, 4 Gem Fragments, 4 Elastic Fibers, 4 Flesh Bits | 2 |

## Injury, repair, and the living blueprint

The ship has a persistent internal health pool and a memory of the blocks attached to it. There is no health bar or health number in the HUD, goggles, or menus. At zero health the organism dies permanently; later biomass cannot revive it. Removing a remembered block by ordinary mining injures it; damage caused by its creator also reduces trust, including attributed explosions. Missing tissue from explosions or other removal is detected as well. The ship regrows missing blocks from its remembered layout, spending one stored Flesh Bit per repaired block and restoring four health. Surviving machine inventories are preserved; a destroyed machine regrows empty. Healing an injury without a missing block also spends biomass. Repairs wait for clear space and do not grow through entities or terrain. No stored flesh means no automatic healing.

Wrench relocation updates the block memory without causing injury. The wrench cannot remove hull tissue or biometric doorway membranes. Ordinary mining and damage remain possible.

A **Blueprint Nerve Panel** grows on the engine-room wall. Only the creator can open its editor. The map shows the existing cockpit and engine room. Select a room and click the map to move it, or choose **Add room**, **Add corridor**, **Clear opening**, **Add wall**, or **Add door**. The **R** button rotates a newly added room or corridor. The floor plan supports up to twelve rooms within a 49-by-49 area; every room must remain joined to the core through connected floors and tissue. Existing machines cannot be overwritten by a wall edit. Add membrane doors at shared walls to connect room interiors.

The panel displays available stored flesh biomass and the required cost. Each new or changed block costs one Flesh Bit; removing tissue through the approved plan gives no item drops or biomass refund. The green **checkmark** validates and approves the new plan. The red **X**, closing the menu, or walking away discards the draft and leaves the live ship unchanged. The current flight organs and sufficient stored biomass are required before approval.

After approval the ship searches for dry, level ground with clearance for both the existing and proposed body. It travels to a suitable site, lands, uses a tentacle to carry passengers and animals to safe ground, locks its membranes against entry, and reconfigures one block at a time. The membrane lock also excludes the creator until construction completes. Tissue opens temporarily along an evacuation path and seals during rebuilding. Unexpected obstructions pause the job. The blueprint, reserved cost, construction phase, and moving machine contents persist across saves. Machine inventories move with their rooms; approved reconstruction does not damage health or trust. Temporary construction gaps are excluded from Sable fragmentation until the final connected structure is restored.

Growing ships can also brace and walk on small tentacle arms. Arms push against nearby dry ground to raise the hull for construction and adjust its stance over uneven support. If the future body is still blocked, it looks for a nearby clear growth site and walks there. Leg support has a limited reach and does not replace levitite flight. Cliffs, water, and impassable paths pause ground movement rather than receiving levitation or terrain removal.

## Entrance and visibility

Biomancy **Biometric Membranes** replace the doorway and separate cockpit from workshop. The creator can pass by default. The cockpit’s **Biometric Access Panel** allows the creator to authorize online players and nearby named animals, and revoke existing permissions. Animals are authorized by UUID, so another animal with the same name does not gain access. Permissions persist and synchronize to client collision checks.

Look at the eye, mouth, stomach, nerve panel, or intestines while wearing **Symbiotic Goggles** to see a compact tooltip. Long instructions wrap and remain inside the screen; no internal health is shown.

One-way dermal windows have an opaque, flesh-textured outer face and a clear view from inside. Four hanging Biomancy Bio-Lanterns provide **steady** cockpit/workshop light. The core retains its original detailed texture and blinks independently with an eyelid overlay. Legacy prototype lamps also remain steady.

## Appetite, trust, and hunting

Actual structure size determines appetite: each loaded second consumes `1 + floor((body blocks - 1) / 64)` satiety units. Fresh meat restores 70 after digestion, up to 1200. Starvation lowers trust from +100 through zero to −100. Below zero, the ship becomes aggressive and refuses captain commands. Feeding through the digestive system rebuilds trust; striking the eye lowers it.

Permitted hunting follows visible food locally. Six functional flagella grab and attack nearby prey; a limb holding prey is unavailable for swimming, and missing limbs reduce propulsion. Swallowed prey enters digestion rather than granting instant nutrition. Hunting supplies less nutrition than two hand-fed meat items and gives no trust reward. Farm animals, villagers, and other players may become prey. Cooperative ships exclude authorized identities and tame pets. Passengers, creative/spectator players, and invulnerable entities are excluded. Aggression also threatens former allies.

Skeletons are never food. Zombies and rotten flesh poison the creature, draining satiety, lowering trust, and weakening its response until toxins wear off. Poison and identity persist. Position feedback maintains developed buoyancy while a resting ship pauses propulsion. There are no mechanical propellers.

## Starvation, death, and reclamation

When normal satiety runs out, the ship consumes its stomach’s nutrient reserve, then stored nutrients/paste/bars, Flesh Bits, Elastic Fibers, Bone Fragments, and finally other stored materials. Emergency energy gives no trust reward; the reserve cost scales with the ship’s appetite. If the available energy does not cover that demand, starvation damages the hidden health pool. Attached hulls are never eaten for sustenance. Digestion and construction reserves are separate: newly incorporated materials do not immediately reward feeding trust.

Death is permanent and saved. Lift, propulsion, walking, growth, assimilation, feeding, and self-repair stop; the wreck falls under Sable gravity. Biometric membranes release their reconstruction lock so the creator can leave a failed job. After a ten-second grace period, the loaded wreck loses blocks every two seconds. Decay rate scales with remaining size, and the core is left until other blocks have rotted. Detached wreck pieces inherit the death marker and decay schedule, even without a core. Remaining machine contents may be salvaged before their blocks decay.

Living ships can pull useful parts from **nearby dead bioships** using their assimilation arms. They do not steal from living ships. Recovery only targets needed organs or reserves: a missing mouth or stomach, missing precursor machines, a limited number of storage sacs, a missing larynx, or needed construction tissue. Wreck components must still be exposed, above ground, and visible; collection reaches twelve blocks. Recovered components keep their inventories.

Breaking down wreck tissue loses material: **two hull/tissue blocks yield one flesh biomass**, **four carapace blocks yield one Bone Fragment**, and **four flagella/intestine blocks yield one Elastic Fiber**, alongside their reduced flesh yield. Fractional progress persists across saves. Reclaimed tissue supplies growth and stored construction material without immediate food or trust rewards; those reserves may be burned later during starvation.

## Development

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
```

Unit tests cover needs, material thresholds, hunger scaling, negative trust, hunting efficiency, poison, and flagellum strokes. Development-only GameTests cover native recipes, automatic placement, protected assembly, real moving cargo and inventory conservation, visibility restrictions, digestion/acid, private products, storage overflow, an oval two-room body, flight and steering, membranes/permissions, equipment, steady lighting, core blinking, prey digestion, later larynx growth, damage/repair persistence, tube connections, room editing, relocation with inventory conservation, evacuation, grounded hatchling movement, paired-machine fusion, emergency metabolism, permanent death, falling wrecks, fragment decay, and lossy scavenging.

GameTests use a fresh world under `build/gametest-run`, separate from normal client/server worlds. Ships are tested at separate locations near the origin because native physics loses precision at the runner’s randomized multi-million-block coordinates. Fixtures are excluded from the shipped JAR.

| Dependency | Version | CurseForge file |
| --- | --- | --- |
| Sable | 2.0.6 | 9043865 |
| Create Aeronautics | 1.3.2 | 8763471 |
| Create | 6.0.10 | 7963363 |
| Biomancy Reforged | 2.9.11.0 | 9429985 |
| GeckoLib | 4.9.3 | 8893490 |

Use a **fresh core** for layout version 5; completed older bodies are not rebuilt automatically. Further body stages and full obstacle navigation remain future work; ground-site selection and landing use bounded local searches.
