# Name Tag Displayer

The craftable panel accepts one named name tag. Mount it against any sturdy face, including the underside of a hull. Its three sliders move the hologram in local X/Y/Z coordinates, up to sixteen blocks in either direction. The text remains upright as the construct tilts. Removing the panel drops its tag.

On an assembled object, the first nonempty panel in block-position order supplies the native Sable name. Removing all labels clears a panel-managed name; objects without panels retain manually assigned native names. Living constructs restrict label editing to their owner.

The Nerve Panel's Constructs tab lists nearby named assemblies. Registering one permits that assembly through the host's biometric membranes. This uses Sable's per-block collision callback, preserving collisions against the surrounding solid frame. Living passengers inherit passage only while their feet are inside a registered assembly's oriented bounds. Leaving those bounds or revoking the construct removes that passage. This does not invite passengers to the host or grant diagnostic/inventory access.

Registration follows the assembly UUID. Rebuilding an object as a new assembly, including the developer Junior reset, requires registering its replacement UUID again.

Name Tag Displayers, Nerve Panels and Vital Readouts use the `massless_panels`
block tag. Their native Sable mass and displaced volume are zero. They add no
hunger or oxygen demand and provide no buoyancy. The tag can include future
panels without adding special cases to metabolism or physics controllers.
