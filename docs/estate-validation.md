# Estate validation

Validated the estate and furniture changes through commit `b0d4e9c` on `codex/amoeba-fresh-start`.

The complete required GameTest suite passed in both configurations: **144/144 without Epic Fight**, and **144/144 with Epic Fight 21.17.3.1**. Epic Fight was loaded from the isolated test instance's `mods` directory; it remains an optional dependency.

The automated checks cover connected table updates and waterlogging, native chair seating without dye replacement, reading-stand book persistence, archive/cabinet inventory persistence, native bed placement/sleep/respawn and water preservation, inventory-preserving library migration, fifteen valid enchanting sources, supported kitchen appliances, and bounded terrain generation. The staircase regression now includes the support frame that previously erased upper vault treads.

In the generated developer-world copy, a normal player movement simulation traversed all **676 outdoor route steps without jumping**. Both furnished staircases passed **upward and downward** traversal. The copied exhibit Junior retained its root identity, the creator's ownership UUID, no invite entries, and **450/450 health**. Temporary QA probes were removed and are absent from the release JAR.

Close-range client inspections covered furniture, the library/enchanting arrangement, supported lighting, the entrance display, gardens, water features, ruins and the observation vault. The old street-lamp hook obstructing the entrance was removed, and generation now excludes street lamps from the entrance hall.

Wide overhead captures on the software-rendered QA client showed incomplete distant chunk rendering, including after a graphics compatibility retry. Saved tower block data was checked independently and close-range views were used for inspection. Full overhead appearance should also be checked on the normal game client; the software captures are not evidence of a complete visual pass at every distance.

The task checklist and progress log are kept in the chat, not this repository.
