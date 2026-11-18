# Trust loss

Trust remains a saved, fractional value from 0 to 100 on the creature root. Linked limb
sublevels share the same score and owner-attack history.

## Owner attacks and accidental-hit grace

Normal penalty = 50 × (health actually lost / maximum health before the hit).
Within a rolling 600-game-tick window (30 seconds at 20 TPS), consecutive accepted owner
hits apply 5%, 15%, 35%, 65%, then 100% of that penalty. An isolated hit therefore costs
little; frequent hits escalate. Old hits stop contributing to frequency after the window.

The eight most recent accepted owner hits are saved in SubLevelData.STATE as ownerHits,
including game tick, actual health loss, and player UUID. This history survives save/load
and remains bounded. Armor-only hits record frequency but have no health-based trust cost.
Owner-attributed projectiles count; strangers and mobs do not spend the owner's grace.
Overkill is limited to health actually lost. Wrench removal and capacity bookkeeping do not
enter this attack hook.

For a 20-HP creature, a 2-HP first owner hit costs 0.25 trust; a fifth hit within the window
costs 5 trust for the same health damage.

## Starvation

Every successful starvation damage tick applies:

20 × (health lost / maximum health) × (1 + 2 × missing-health fraction after damage).

The multiplier rises from 1× at full health toward 3× at death. This runs on damage events,
not a separate polling timer, and is independent of the owner-attack grace window. Pausing
hunger, restoring food, blocked damage, and death stop further starvation trust loss.
