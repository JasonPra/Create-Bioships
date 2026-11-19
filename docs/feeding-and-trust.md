# Feeding animation and trust

An assembled living mouth breathes subtly while idle. Accepted food starts the existing chewing cycle and eat sound, with item particles representing the most recently added food. Dead and unassembled mouths do not animate.

Player hand-feeding and actual GUI deposits earn 0.1 trust per accepted meat/fish item, capped at 100. Rotten flesh is excluded from positive feeding rewards. The `create_bioships:trust_food` item tag includes meat and fish and can be extended by data packs. The sublevel stores the fractional score; core and readout displays show it to one decimal place.

Connected Biomancy sacs count only when their intestine network reaches a stomach. Disconnected sacs, automatic transfers and simulated inserts do not earn feeding trust. GUI hooks compare before/after inventory contents so only actual player inventory changes count.

A successful gain queues at most six happy-villager particles across the body. Gains close together are combined into one burst. Damage and starvation penalties are documented in `trust-loss.md`.

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


Nutrient paste earns 0.05 trust per item accepted through hand-feeding, mouth GUI,
connected storage sacs, or the stomach's reserved paste cell. Taking paste back
through those inventories removes exactly the corresponding trust amount. Paste
entering a mouth routes directly to stomach paste storage, rather than entering a
decomposition recipe. Internal transfers and simulated insertion never award trust.

Player deposits of rotten flesh into a mouth or connected sac cost 2 trust per item.
Taking rotten flesh back does not refund the penalty or grant trust. Once a stomach
actually consumes rotten flesh, the creature becomes sick for 30 seconds; later
rotten meals refresh this duration rather than stacking extra simultaneous effects.

One creature-wide vomiting episode can occur every five seconds while sick. It
emits eight slime-block-textured fragments from a mouth and removes one nutrient
paste from an available stomach, preferring its reserved cell. Empty paste storage
never becomes negative. The retching cue reuses the existing short, low-pitched
`biomancy:block.digester.crafting_random` sound event. There are no copied audio
assets or independently ticking mouth sickness controllers.

Sickness timing is saved in the root sublevel's state and displayed in the goggles
readout. Unloaded time never creates a burst of overdue episodes on reload. Death
immediately stops further vomiting, sound, and paste loss.
