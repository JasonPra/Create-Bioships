# Combat timeout verification

Native dedicated-server run of the visible development fixture, using the supplied
`epic-fight-21.17.3.1-mc1.21.1-neoforge.jar`, a 60-second limit per enemy, and accelerated
server ticking. Each trial starts at 1 HP and completes only after attack/contact damage;
there is no timeout-to-pass fallback.

**42 / 42 passed.** The non-attacking ordinary Biomancy flesh blob is excluded; its hungry
variant remains. This checks attack delivery, not a complete animation/visual quality review.

| Previous failure | Correction |
| --- | --- |
| Ordinary flesh blob | Excluded: its native goals do not attack. |
| Wither skeleton and Epic Fight minion | Lower surface target anchor permits native animated melee selection. |
| Drowned | Water trial at sea level prevents native swim-up navigation from displacing pursuit. |
| Phantom | Airborne spawn and taller enclosure permit native flight and swooping. |
| Epic Fight wither ghost | Native summon positioning and verified swept-contact visibility through its struck tissue. |
| Ender dragon | Creature-directed native charging, optional phase acceptance, and actual head/neck/wing contact against tissue. |

All damage still enters the normal adapter, including resistance and damage cooldowns.
World obstacles still block swept contact; a weak point covered by other tissue is not
accepted by the additional visibility check.

## Additional validation

- Without Epic Fight: **40 / 40 native combat trials passed**, at the same 60-second limit.
- `build runGameTestServer`: **24 / 24 required tests passed** without Epic Fight.
- The same regression suite with the supplied Epic Fight jar: **24 / 24 passed**.
- Explicit checks cover distant dragon parts doing no damage, actual head contact using
  native damage/cooldown, and swept contact respecting an intervening world block.

## Recorded Epic Fight outcomes

```text
biomancy:primordial_hungry_flesh_blob: PASS (mob); target acquired
epicfight:wither_ghost: PASS (mob); target acquired
epicfight:wither_skeleton_minion: PASS (mob); target acquired
minecraft:blaze: PASS (fireball); target acquired
minecraft:bogged: PASS (arrow); target acquired
minecraft:breeze: PASS (mob); target acquired
minecraft:cave_spider: PASS (mob); target acquired
minecraft:creeper: PASS (explosion.player); target acquired
minecraft:drowned: PASS (mob); target acquired
minecraft:elder_guardian: PASS (indirectMagic); target acquired
minecraft:ender_dragon: PASS (mob); target acquired
minecraft:enderman: PASS (mob); target acquired
minecraft:endermite: PASS (mob); target acquired
minecraft:evoker: PASS (mob); target acquired
minecraft:ghast: PASS (fireball); target acquired
minecraft:giant: PASS (mob); target acquired
minecraft:guardian: PASS (indirectMagic); target acquired
minecraft:hoglin: PASS (mob); target acquired
minecraft:husk: PASS (mob); target acquired
minecraft:illusioner: PASS (arrow); target acquired
minecraft:magma_cube: PASS (mob); target acquired
minecraft:phantom: PASS (mob); target acquired
minecraft:piglin: PASS (mob); target acquired
minecraft:piglin_brute: PASS (mob); target acquired
minecraft:pillager: PASS (arrow); target acquired
minecraft:ravager: PASS (mob); target acquired
minecraft:shulker: PASS (mob); target acquired
minecraft:silverfish: PASS (mob); target acquired
minecraft:skeleton: PASS (arrow); target acquired
minecraft:slime: PASS (mob); target acquired
minecraft:spider: PASS (mob); target acquired
minecraft:stray: PASS (arrow); target acquired
minecraft:vex: PASS (mob); target acquired
minecraft:vindicator: PASS (mob); target acquired
minecraft:warden: PASS (sonic_boom); target acquired
minecraft:witch: PASS (indirectMagic); target acquired
minecraft:wither: PASS (witherSkull); target acquired
minecraft:wither_skeleton: PASS (mob); target acquired
minecraft:zoglin: PASS (mob); target acquired
minecraft:zombie: PASS (mob); target acquired
minecraft:zombie_villager: PASS (mob); target acquired
minecraft:zombified_piglin: PASS (mob); target acquired
```
