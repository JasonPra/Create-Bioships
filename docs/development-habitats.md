# Development habitats

The homunculus aquarium is installed once in development worlds at x=1536, z=256,
using the existing exhibit floor height. The compass navigator has a dedicated
entry. It has a 47-by-35-block interior, 18 blocks of source water, a framed glass
shell, a sand/gravel bed, perimeter kelp and seagrass, coral/rock gardens and
submerged lighting. The central swimming volume is open. A dry ladder on the left
reaches an observation deck and swimming entrance above the waterline. No creature
is spawned and there are no start/reset controls to erase a player's future build.
Installation is persisted in the development fixture saved data.

Junior's supplied save also receives a garden pen around his existing location.
His one lung replaces the redundant packed-flesh block under/behind his core;
the temporary roof lung is removed. The pen has a moss floor, corner trees, low
plants, a shallow pond, viewing glass and a two-door entrance. His creature identity,
owner, inventories and native limbs are retained. Hunger and oxygen stay enabled.
The habitat builder does not spawn, clone, heal or grant immortality to its subject.

The Ossified Wrench now checks the root creature's current owner before safe
shift-removal. Unassigned living sublevels, strangers and inspection-only invitees
cannot remove components. Ownership transfers take effect immediately, including
when a generated compact segment redirects a hit to its socket. Existing normal
rotation/extension and non-creature Create interactions retain their rules.

## The den and Junior's developer baseline

The authored den stands at x=-1024, z=-1024, about 2,900 blocks from the combat
arena. Junior's garden is the centerpiece of a large underground vault. Ancient
columns, a Biomancy workshop, an inert unfinished creation and three furnished
villager cells surround it. The surface tower contains a throne hall, kitchen and
dining room, guest drawing room, bookshelf-lined study and master bedroom.
Its entrance hall has crenellations; cornices, lancet windows, buttresses and
climbing vegetation age the exterior. Uneven mossy ground, varied plants, small
cultivation patches, a pond, boulders and ruined foundations form the grounds.
The antechamber is world spawn. Protected dirt-path roads connect the exhibits.

The craftable **Throne of Az'kerash** is two-block furniture with bone and chitin
framing, a fleshy cushion and Create's native sitting entity. Both halves waterlog;
breaking either removes the pair and yields one item. Dye cannot replace it with
a generic seat. Recipe: four bone blocks, two chitin armor and packed flesh.

A configured development caretaker checks Junior every 6,000 game ticks. It needs
roughly five minutes of paste, with a minimum reserve of 16, and adds a stack to
available mouths if the stomach is low. Paste already waiting in mouths prevents
repeated overfilling. Existing food is not replaced, no feeding trust is granted,
and the caretaker cannot revive a corpse. Timers persist; loading does not trigger
a loop of missed feedings. This service is restricted to the development preset.

The reset command block runs `/bioship_dev junior reset`. The operator-only
`/bioship_dev junior capture` updates the saved baseline after design changes.
This is a developer baseline for the main body and generated compact limbs, not
a player schematic system. Unsupported modular assemblies are rejected at capture.
Restoration builds a fresh Sable body, preserves creator ownership and saved organ
inventories, and updates the caretaker's reference. Its Sable body UUID changes;
external constructs would need to register that replacement identity again.
The former subject is removed only after replacement assembly succeeds.
