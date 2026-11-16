# Create Bioships

Living hatchling creatures for Minecraft 1.21.1, NeoForge 21.1.266, and Java 21.

**The airship stage is dormant pending a redesign.** Automatic hull construction, cockpit and engine-room expansion, flight progression, piloting, and blueprint rebuilding are unavailable. Their source, models, textures and registry IDs are preserved for future reuse and existing saves. They are not enabled by loading an older core or supplying more materials. See [prototype status](docs/development/airship-prototype.md).

## Raise a hatchling

Craft a Living Ship Core in **Biomancy’s Bio-Forge → Machines** tab using 1 Living Flesh, 16 Flesh Bits, 8 Elastic Fibers, 4 Exotic Dust, an Eye of Ender and 4 nutrients. Placement creates a Sable physics body and records its creator. The core cannot be placed on an existing physics grid.

Place a Bio-Forge, Decomposer and Digester where the creature can see and reach them. It assimilates real components and their inventories. A Bio-Forge becomes a feeding mouth; a Decomposer and Digester combine into a stomach. Physical intestines connect mouths, stomachs and storage sacs. Feed through the toothed face, or right-click an empty-handed mouth to open its inventory silently. The stomach runs native Biomancy decomposition recipes, retains Nutrient Paste for energy and deposits construction products into shared storage.

The core keeps emergency material stock. Connected stomachs and Storage Sacs share usable construction resources. New storage, intestinal extensions, leg enhancements, repairs and permitted body adaptations have material costs. Existing suitable components take precedence over fabricating replacements. Scavenging a dead creature provides useful parts and materials at a loss; living cores are never edible supplies. Removing a core marks its body dead, and its attached components gradually rot.

Hatchlings can scuttle, navigate obstacles, climb within their leg capability, forage, hunt for food and needed resources, retaliate against hostile mobs, and flee threats. Hunting uses the same telegraphed swipe as defense; it does not carry live prey or manufacture a meal at the moment of death. The creature collects normal dropped meat, preferring it to another hunt. Its own kill drops remain known after losing sight. Undead and iron golems are excluded from dietary prey; defensive retaliation remains separate. Missing meat drops use new raw meat items, including Enderman, Villager and Witch variants. Legs can be strengthened with paid resources. Supported overturned creatures can right themselves; tentacles allow swimming but do not permit breathing underwater. One exposed mouth supplies air; all mouths submerged eventually causes drowning.

A bare core has no energy upkeep. Finished mouths, stomachs and intestines add none; ordinary attached blocks, including unconverted Decomposers and Digesters, contribute to demand. Resources are retained for the active creature instead of being consumed by the dormant airship progression.

Ordinary body tissue now uses **Biomancy Packed Flesh**. The original hull and new purple tier-two hull remain available as future armor assets. Each feeding mouth can absorb its own larynx after digestion is working, gaining modulated creature vocalizations and animated jaws. Provisioned care gets brief trust particles; poisoning emits green status particles; damage flashes the body red and produces a short hurt voice when possible.

Walking legs are independent of mouth-bound graspers. A level-one mouth offers one grasper slot; its larynx upgrade raises that to two. One primary mouth remains forward-facing while additional mouths can face outward around the body. Legs and graspers share sucker cup rendering, but only legs provide support and locomotion. Leg capabilities can be tuned through a reloadable [movement profile](docs/development/appendage-profiles.md).

## Developer tools

Symbiotic Goggles show behavior, health, food, trust and resources. **F3+B** toggles sight cones and ray results. **F3+P** toggles path visualization and a native floating behavior/priority display; goggles are not required. Debug snapshots do not execute behavior.

The operator command `/bioship_voice A` (or `hurt` / `growl`) auditions the closest already vocal mouth within eight blocks.

The [readable systems flowcharts and class reference](docs/development/systems-reference.md) are also available as a [paginated PDF](docs/development/systems-reference.pdf).

The packaged **Bioship Navigation Lab** world type contains the flat and elevation glass mazes used by the physics tests. It preserves normal terrain interactions, daylight, mobs and chunk loading. See the [lab guide](docs/development/navigation-lab.md), [path debugger](docs/development/path-debug.md), [module boundaries](docs/development/modules.md) and [architecture review](docs/development/architecture-review.md).

## Development

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
```

Java 21 is required. Active GameTests cover hatchling navigation, perception, native mob targeting, digestion, shared resources, paid adaptations, salvage, decay, swimming, righting and disabled-prototype boundaries. Preserved airship-only scenarios use `@DeferredAirshipTest` and are not registered while the stage is unavailable. Development fixtures are excluded from the runtime JAR.

Equipment assets can be regenerated with `python tools/generate_equipment_assets.py` (Pillow). Runtime/dependency warning findings are recorded in [runtime-warnings.md](docs/compat/runtime-warnings.md).

The [earlier checkpoint log](docs/changes/2026-11-15-bioship-checkpoint.md) is historical. The [digestion and recovery log](docs/changes/bioship-digestion-and-recovery.md) and [prototype status](docs/development/airship-prototype.md) describe the current work and its pending features.

The [Packed Flesh, voice and feedback change log](docs/changes/2026-11-16-tissue-voice-and-feedback.md) details this checkpoint and its validation limits.
