# Creature metabolism and developer statistics

`SubLevelData` owns saved hunger and fractional drain progress. `SubLevelHunger`
advances metabolism once per root each second. It sums Sable's cached mass trackers
for the root and its linked physical bodies, without scanning their blocks.

The initial balance is `max(1, total mass / 10)` hunger per minute. A small creature
retains the previous one-minute rate; 20 native mass units drain 2 hunger/minute.
`SubLevelData.HUNGER_REFERENCE_MASS` defines the reference for future balance changes.
Fractional work survives saving and mass changes. Pausing freezes the timers and
unloaded creatures accrue no catch-up debt. Corpses never metabolize.

At zero hunger, starvation still deals 1 HP every four seconds until death. Passive
regeneration still requires at least 90% hunger. Healing potions remain independent.
These damage/healing timers are separate from the mass-scaled hunger drain.

A stomach consumes one nutrient paste for four hunger in a 32-tick (1.6-second)
eating cycle. Efficiency increases hunger per paste by 20%, 30%, then 35% total:
4.8, 5.2, and 5.4 hunger. Fractional fifths carry between meals and persist with the
sublevel component's state, including portable stomach snapshots. Full hunger never
wastes paste. Efficiency also retains paste production (2/3/4 at tiers 1/2/3) and the
existing extra-component chances.

The Development Toolkit's **Creature statistics** toggle subscribes to read-only
holograms for the nearest four creature roots within 64 blocks. Snapshots refresh
at 2 Hz and expire when the subject leaves range. They show health, armor, hunger,
mass and linked-body count, the drain formula/progress, healing eligibility, starvation
timing, trust, paste conversion, appendage flags and movement requests. Subscriptions
require the development preset and its protected toolkit; the display does not drive
simulation or change hunger.

Regression coverage includes linked mass without double counting, fractional save/load,
pause behavior, rate reduction on detachment, nutrition bonuses at every Efficiency
tier, fractional nutrition save/load, full-hunger preservation and the bounded statistics codec.

Players may place multiple stomachs and mouths on a living creature. Each stomach
keeps its own inventory and digestion state; connected mouths can route into the
same intestine network. Hunger capacity is 5 base plus the sum of every stomach's contribution, including
those on linked body parts. Each stomach adds 5 capacity plus 5 per installed
Longevity upgrade (up to three independently installed upgrades per stomach).
Adding or upgrading a stomach increases capacity without filling hunger; removal
subtracts only that stomach's contribution and clamps current hunger if necessary.

The stomach's existing bottom-right output cell (slot 26) is reserved for nutrient
paste and outlined in green. The inventory remains 27 slots. Player clicks and
shift-clicks can insert/retrieve paste there; food and component cells remain
inspect-only. Routing and digestion prefer the reserved cell for paste and exclude
it from component insertion. Extra paste may still overflow into general output
cells. Component overflow checks count the 17 general output cells independently
of the reserved paste cell.

Older non-paste contents in that cell are preserved and relocated when general
output space becomes available, or sent through normal connected-sac overflow.
No items are discarded to enforce the new restriction.

The Symbiotic Goggles core readout includes fixed-width health, armor, and trust
meters alongside their numeric values and the existing hunger icons. Empty armor
capacity renders an empty meter safely; fractional trust remains visible in the
number and meter fill.
