# Somatic Resonator

A craftable player diagnostic instrument with a narrow bone handle, chitin-tipped
resonance fork and fleshy sensing core. Its 3D item model uses the project's bone,
tissue and chitin palette. It is separate from the development-only toolkit and
is available in the normal Bioship Core creative tab.

Right-click opens a two-sector radial menu. The left sector is labelled “Show sublevel facing direction”;
the right is “Show digestion network connections,” including recognized inventories.
States read Visible/Hidden, making clear that these toggle displays rather than
the creature’s underlying mechanics. Both may
be on together or switched off independently. Click the centre or press Escape to
close the menu. Closing the wheel does not disable selected overlays.

The instrument must remain in the player's inventory to retain diagnostic access.
The server checks ownership/invitations for each sublevel before sending data;
revocation removes the corresponding snapshots on the next update. Players cannot
inspect strangers' creatures by crafting the instrument or sending toggle requests.
The overlays are local to each viewer, and enabling them does not reveal data to
other players. The normal instrument does not unlock path, vision, statistics or
other development controls, and cannot edit a creature's front direction.

Crafting pattern: bone in the top centre, chitin armor on each side of redstone in
the middle row, packed flesh in the bottom centre. The recipe yields one instrument.

The same server graph and facing implementations serve the development toolkit and
the normal instrument; no duplicate digestion simulation, inventory or path search
runs for the diagnostic wheel.
