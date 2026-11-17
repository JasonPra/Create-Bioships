# Portable creature containers

Creature-owned inventories travel with their physical block or packed item. There is no inventory database. Minecraft 1.21.1 uses both block-entity NBT and item data components; vanilla block-item placement restores both.

| Container | Item persistence | Extra data |
| --- | --- | --- |
| Mouth (ordinary, voiced, aquatic graspers) | Vanilla container component plus block-entity data | Larynx capsule, grasper upgrades, inherited input metadata |
| Stomach | Block-entity data | Input, digestion progress, nutrient paste, local materials, tube stock/bootstrap accounting |
| Living core | No break drop | Broken cores are permanently lost; core reserves remain persistent during living internal movement, not as revival items |
| Biomancy storage sac | Existing native item components | Existing native inventory behavior retained |
| Other transported native machines | Complete current block-entity state during physical handoff | Native recipes, buffers and other machine state; native world mining rules retained |

`BioshipContainerState.packDrops` is the shared hook for new creature container blocks. Do not also scatter the same contents from `onRemove`. A nonempty creative break uses the same packing path. Stomach portable items discard old world route endpoints while retaining paid/free tube accounting; new placement reconnects in its new body. Core breaking never packs a reusable item. Creative breaks follow the same permanent-loss rule. Existing death/abandon handling releases real carried components separately; replacing a core in a registered wreck does not revive that lineage.

`OrganMotion` captures the source immediately before assembly and the current cargo immediately before commit. Each synchronous handoff resolves the block entity through its owning Sable plot holder and restores onto the matching destination block-entity type. Generic world chunk lookups could miss the newly assembled cargo actor and silently skip hydration. It preserves complete current state, including inventory represented only by numbers. It never keeps an old pre-transit snapshot to replay later: digestion or other legitimate cargo changes must survive commit. Destination coordinates are rewritten, and normal topology/host updates run afterward.

The regression suite covers mouth variants, native sac components, stomach reserves/progress, permanent core loss, creative drops, repeated transfers and an in-transit inventory reduction. Tracing reproduced a populated source followed by missing initial cargo hydration and an empty later cargo. The owning-plot correction passed eight repeated transfers in the full 155-case suite; extended endurance remains queued.
