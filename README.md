# Create Bioships

A living-airship prototype for **Minecraft 1.21.1, NeoForge 21.1.266, and Java 21**. A dormant core awakens when assembled into a Sable structure, eats materials, and grows into a small eldritch dinghy.

## Try the prototype

1. In Creative, take **Living Ship Core** from the **Create Bioships** tab. There is deliberately no crafting recipe.
2. Place it in open space, and assemble **only the core** into a Sable object using the existing assembly tools. Keep other blocks out of its future body. Before assembly, the core is dormant and cannot eat.
3. Right-click the awakened core with levitite first. Its first piece enables lift; the hatchling gently rises clear of terrain. Supply **8 levitite** total and **64 meat or rotten flesh**. Each interaction consumes one item, including in Creative. Aeronautics' pearlescent levitite also works.
4. Leave room for a body **5 blocks wide and 7 long**, with **3 blocks below** the core and **1 above**. Once fed and unobstructed, it grows outward over about **16 seconds**. Blocked growth pauses, preserves the existing blocks, and resumes when clear.
5. Board the dinghy and sit on its fleshy **living perch**, which uses Create’s riding behavior. All controls are physical blocks grown into the cockpit; no custom keys are needed.
6. Use the **living throttle** beside the perch: click its right lobe to increase thrust, or its left lobe to decrease it. There are six settings, **0–100% in 20% steps**. Sneak-click to return to zero. New ships start at zero throttle.
7. Use the **living helm** ahead of the core: click its left/right grip to steer port/starboard. It holds one of seven rudder positions. Click its center or sneak-click to straighten it. The wheel’s appearance follows its position.
8. Five low **nerve pads** provide reverse, port/starboard thrust, and ascent/descent. Empty-hand right-click latches a pad on or off; active pads depress and glow. Adjacent redstone activates them too, allowing Create/redstone machinery to command those organs. Reverse changes the direction of the throttle’s thrust. Sideways and vertical nerves remain usable at zero forward throttle.
9. Touch the core with an empty hand to toggle **rest**: all movement commands and foraging pause, but lift stays active. Sneak-right-click the core to inspect its resources, hunger, mood, and trust without toggling rest.

Movement is relative to the ship; the pointed end is its bow. The helm turns its actual Sable body. The throttle regulates biological forward/reverse thrust rather than a mechanical propeller. Mood affects both thrust and steering response. Throttle, helm, and nerve positions persist as block states.

## Living behavior

Meat and rotten flesh nourish the ship after growth too. Hunger decreases while its Sable object is loaded; unloaded ships do not starve offline. Rotten flesh restores 40 seconds of satiety and other accepted meat restores 70, up to 20 minutes. The `create_bioships:biomass` item tag defines its diet.

Feeding gradually builds trust. Striking the core lowers it. Hungry or wary ships respond more slowly; grumpy ships hesitate periodically. Neglect never deliberately disables lift. An idle ship gently bobs, and a hungry, uncommanded ship can scent nearby dropped meat, move slowly toward visible food, and consume it. Rest mode suspends this foraging too. This is a small behavior system, not full navigation or a companion AI.

Growth progress, reserved biomass, levitite, satiety, trust, and rest state persist with the core. Only one core leads an assembled structure; extra cores must be separated. The grown hull, tendrils, and nerve organs have no item drops or crafting recipes in this prototype.

## Development and validation

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
```

The build runs five unit tests for dietary thresholds, hunger, recovery, mistreatment, and saved-state bounds. Five development-only GameTests check dormant feeding, real Sable assembly and growth, persistence, physical propulsion and lift, control rest state, throttle increments/decrements, helm steering/centering, redstone activation, and obstruction protection. Their classes and template are in `src/gameTest`, outside the shipped mod JAR.

Pinned CurseForge files:

| Dependency | Version | File ID |
| --- | --- | --- |
| Sable | 2.0.6 | 9043865 |
| Create Aeronautics | 1.3.2 | 8763471 |
| Create | 6.0.10 | 7963363 |

Sable provides the actual moving block structure and rigid-body physics. Aeronautics supplies levitite and the bundled Simulated assembly tools. Create supplies the living perch’s riding behavior and can power redstone controls. Biological propulsion currently applies forces through Sable directly; it does not pretend to be an Aeronautics balloon or propeller.

This first body uses block models and static tendrils. Animated tentacles, further growth stages, more detailed cockpit animations, damage repair, and more elaborate personalities remain future work. Flight tuning and the complete riding experience still need a client playtest. Motion integration tests run near the world origin; horizontal motion lost precision in the native physics simulation at the test runner’s randomized multi-million-block coordinates.
