# Equipment and component previews

The Ossified Wrench uses a predominantly aged-bone mesh and sprite, with several small
flesh bindings and a short tendon. Symbiotic Goggles retain a recognizable twin-rim,
bridge and strap silhouette with the selected amber slit-eye lenses and plum organic
framing. Their inventory and model textures remain 16×16, matching Create's goggles;
the wrench sprite is 32×32. The goggles' head model fits the same head-space footprint.
The Synaptic Calibrator's exposed-surface mesh removes coplanar and internal overlaps.
Equipment interaction, item IDs and Create wrench behavior are unchanged.

Chitin Armor, Living Core, Vital Readout, both eyes and Fleshy Intestine have explicit
3D GUI transforms. Small organs are enlarged and centered; the intestine preview
combines its real hub and pipe arms. The core's inventory model always uses closed eyes.

Optional Epic Fight item skins adjust the two tool grips and Simulated's creative
physics staff through Epic Fight's native item-skin resource API. Ordinary model
transforms remain the vanilla reference, and first-person rendering stays vanilla.
These JSON resources add no Epic Fight class linkage or mandatory dependency.

The client checks load the actual models, exercise inventory, held and worn contexts,
and resolve all three skins through Epic Fight's live renderer. Mesh UV density and
Calibrator coplanar faces are also checked directly.
