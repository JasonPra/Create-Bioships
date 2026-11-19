# Feeding animation and trust

An assembled living mouth breathes subtly while idle. Accepted food starts the existing chewing cycle and eat sound, with item particles representing the most recently added food. Dead and unassembled mouths do not animate.

Player hand-feeding and actual GUI deposits earn 0.1 trust per accepted food item, capped at 100. The `create_bioships:trust_food` item tag includes meat and fish and can be extended by data packs. The sublevel stores the fractional score; core and readout displays show it to one decimal place.

Connected Biomancy sacs count only when their intestine network reaches a stomach. Disconnected sacs, automatic transfers and simulated inserts do not earn feeding trust. GUI hooks compare before/after inventory contents so rejected items and withdrawals do not count.

A successful gain queues at most six happy-villager particles across the body. Gains close together are combined into one burst. Trust loss is deliberately not implemented yet.

Player GUI withdrawals reverse the feeding reward at 0.1 trust per meat item.
Normal pickup, shift-click, throw, and whole-slot removal use the same before/after
inventory accounting; rearranging items inside the inventory has zero net effect.
Taking and replacing a stack cannot accumulate trust. Attached sacs remain subject
to withdrawal penalties after their intestine connection is removed, while only
connected sacs earn deposit rewards. Automatic network transfers do not change trust.

A player's successfully applied Regeneration splash potion earns 0.5 trust only
when the creature is alive, below maximum health, and not already regenerating. The saved creature-wide
cooldown is the greater of one minute and the applied Regeneration effect's remaining
duration. Linked limbs and different throwers share this cooldown. Full-health,
refreshes of ongoing Regeneration, rejected effects, non-Regeneration potions, and non-player sources earn nothing.
The normal trust cap and bounded positive-feedback particles also apply.
