# Oxygen and respiratory organs

A living sublevel has one saved oxygen reserve, shared with its linked physical
bodies. A new or older creature without oxygen data starts with 1,200 air units:
60 seconds at the baseline demand of 20 units per second. Loaded simulation time
advances the reserve; unloading does not create catch-up suffocation damage.

Demand is `20 * max(1, mass / 100)` per second,
summing Sable's maintained mass across creature members. Each functioning lung or
gill supplies 40 air units per second. Supply and demand are combined once per
creature. The reserve is capped at 1,200; adding or replacing an organ never grants
instant air. Large creatures need enough functioning organs to match demand.

Lungs breathe anywhere outside water, including behind other blocks. A waterlogged
lung cannot breathe. The controller samples the lung's transformed physical-world
position as well as its own waterlogging state. The paired lobes have connective
tissue extending to all six block boundaries, so they attach to neighboring blocks
rather than floating. Lungs have no block entity or independent simulation ticker.

Gills are directional and must have water immediately outside their intake face.
They do not breathe air. Waterlogging the gill itself is not enough when its intake
faces air. A solid neighbor, including a waterlogged solid, obstructs the intake.
The intake check follows the body's pose and checks native world/sublevel collision
before accepting water at the face. The mod wrench rotates the intake.

Both organs support vanilla waterlogging, bucket pickup/placement and scheduled
water updates, and use the slightly dense biological material mass of 1.05.
They appear in the shared component creative tab and the automatically generated
development waterlogging catalogue.

An empty reserve causes two health damage every 20 ticks (one second), matching
player drowning pulses after the reserve is exhausted. The saved partial damage
timer survives reloads and resets when oxygen starts recovering. This bypasses armor
and does not count as an owner attack or starvation trust penalty. All health healing requires positive oxygen, including natural regeneration,
regeneration potions and instant healing. Armor repair also stops at zero air.
Effects may remain active, but cannot heal until the creature has air again.
Death stops oxygen simulation. No artificial world-block breathing tickers run.

The Symbiotic Goggles core/readout tooltip draws the player's vanilla air bubbles,
including the partial bursting bubble. The inspection snapshot contains oxygen;
the authority remains on the root sublevel. The readout also shows supply and
consumption per second, and the functioning/total count of lungs and gills. A
valid organ contributes supply even if a larger creature still has a net deficit. The display indicates a paused reserve
for development subjects.

Unrelated developer exhibits and regression fixtures explicitly pause oxygen and
hunger through `DevelopmentLifeSupport.pause`, so their existing measurements are
not confounded by missing respiratory organs. Normal player creatures are not
exempt: existing builds need respiratory organs before their initial reserve runs
out. Oxygen tests leave oxygen running and cover dry/submerged lungs, six gill
orientations, obstructed intakes, physical world water, saved timers, depletion,
refill, removal, death, linked bodies, mass demand and native waterlogging.

Gill lamellae animate from one shared phase across the whole creature, including
linked bodies. Wet intakes breathe calmly; dry intakes use wider gasping poses. If
any gill is dry and no lung can breathe air, the shared cycle speeds up, keeping wet and dry gills synchronized
rather than assigning per-block offsets. The existing oxygen controller updates
frames every three ticks; oxygen consumption remains once per second. Death resets
the animation to rest. Cosmetic animation never consumes or refills oxygen.

When a lung can breathe air, dry gills seal their plates and stay still rather than
gasping. Wet gills continue breathing on the shared calm cycle. Waterlogged or
submerged lungs cannot suppress dry-gill gasping. This choice spans linked bodies.

Armored Gills are a cosmetic alternative: identical model geometry, intake rules,
supply, mass and animation to fleshy Gills. Only intake-facing textures change to
the core's chitin palette. Neither variant supplies armor points or protects hits;
both remain exposed weak spots even when the creature has armor elsewhere.

Oxygen has its own reference mass of 100, independent of hunger’s reference of 10.
Junior’s measured mass of 88.55 consumes 20 air per second, so one working lung
provides 40 and refills his reserve at 20 per second. Larger creatures still scale. Either a working lung
or a working gill contributes oxygen; both contribute together. Recovery occurs
when their combined supply exceeds demand. No special lung-and-gill pairing is
required. Paused developer subjects explicitly retain their paused reserve.
