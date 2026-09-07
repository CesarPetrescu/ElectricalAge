# Lighting regression checks

Halogen replacement bulbs (12 V, 120 V and 240 V) fit lamp sockets and floodlights.
Match the bulb voltage to its supply: compatibility does not prevent overvoltage or burnout.
Direct insertion preserves a used bulb's remaining life. Wired sockets still require a cable
in their cable slot and wireless/lamp-supply mode disabled.

Socket wires terminate at the fixture's housing with a metal collar and insulated cable gland.
The connector follows the fixture's rotation and installed cable size. Fluorescent fixtures
only accept connections on the two sides where they can actually render a connector.

Floodlights support floor, ceiling and four wall mountings. Horizontal aim, vertical aim and
beam width use the same mounting frame, including straight-up aim without invalid rays.
Motorized floodlights use their three signal inputs; the manual sliders are intentionally disabled.
Invisible beam lights also work in otherwise empty chunk sections and cave air.

## Automated coverage

- `gradlew test`: registered bulb/slot compatibility, lifetime preservation, all 24 mounting
  frames and beam-cone invariants, negative-coordinate flooring, non-air light states and connector footprint rotation.
- `gradlew runServer -PsmokeTest=all`: named `lighting/<face>` contracts for every supported
  lamp/floodlight mounting, actual right-click insertion, menu acceptance, control packets,
  floodlight ports, electrical-load uniqueness and actual powered light placement on all six mountings.
- `gradlew runClient -PsmokeClient=smoke`: a real stitched-atlas particle check, hit/break
  callbacks with no block entity, and a lamp gallery with connected cables. The gallery
  requires newly placed cable renderers to synchronize and render before capturing them.

Cached model transforms apply to lighting normals as well as positions. Unit checks cover
rotation, non-uniform scaling and degenerate faces; world lighting is not forced to full brightness.

Use the isolated smoke setup described in [headless testing](../tools/port/headless.md).
Do not point the smoke runner at a gameplay world. GitHub CI already invokes these suites.
Reports are under `build/smoke-artifacts/contracts`; gallery images are named
`smoke-lighting-*.png` in the client screenshots artifact. Screenshots are review evidence,
not an assertion that a design looks good.
