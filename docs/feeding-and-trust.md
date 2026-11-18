# Feeding animation and trust

An assembled living mouth breathes subtly while idle. Accepted food starts the existing chewing cycle and eat sound, with item particles representing the most recently added food. Dead and unassembled mouths do not animate.

Player hand-feeding and actual GUI deposits earn 0.1 trust per accepted food item, capped at 100. The `create_bioships:trust_food` item tag includes meat and fish and can be extended by data packs. The sublevel stores the fractional score; core and readout displays show it to one decimal place.

Connected Biomancy sacs count only when their intestine network reaches a stomach. Disconnected sacs, automatic transfers and simulated inserts do not earn feeding trust. GUI hooks compare before/after inventory contents so rejected items and withdrawals do not count.

A successful gain queues at most six happy-villager particles across the body. Gains close together are combined into one burst. Trust loss is deliberately not implemented yet.
