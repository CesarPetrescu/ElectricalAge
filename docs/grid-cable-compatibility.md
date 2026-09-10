# Grid cables: one compatibility rule

New overhead links on the Grid DC-DC Converter, Utility Pole, Utility Pole w/DC-DC Converter,
Transmission Tower, Direct Utility Pole and Grid Switch accept intact power cables rated **at
least 1,000 V**. The threshold includes exactly 1,000 V. It is not a minimum operating voltage.

Insulated utility cables use their insulation-voltage rating. Bare utility conductors and legacy
power cables use their nominal gameplay voltage; bare conductors do not acquire insulation.
Signal cables, melted cables and invalid/nonfinite ratings are rejected. A 600 V cable does not
qualify because of its old `poleEligible` flag, and an 800 V cable does not qualify because its
failure threshold is above 1,000 V. Gauge, material and number of conductors are not compatibility
filters. The existing single-active-conductor span model is unchanged.

The old checks compared the held cable with one hard-coded descriptor, or required a
`poleEligible` flag absent from the ten newer 1/5/20/40/150 kV spool variants. Both now use
`GridCablePolicy`. The historical flag remains in acquisition/tab categorization; registration
order, item IDs and existing cable specifications are untouched.

## Connecting

Hold an eligible cable and click a grid terminal on the first device, then a grid terminal on the
second. Both endpoints use the same rule, independent of click order. The transformer and switch
body faces are not terminals. Overhead-only poles still use overhead links; this does not add
surface-mounted terminals or connect the switch's motor/control inputs to its grid circuit.

Survival spools pay the rounded-up endpoint distance in meters. The actual held cable type and
its metadata are retained in the link. Invalid faces, insufficient/invalid spool length,
incompatible cables, duplicate links and unavailable endpoints must not consume cable. Both
the click handler and the direct new-link API validate compatibility. Existing range limits
remain; this is not an unlimited-distance connection feature.

Saved links are restored without applying a new installation filter: an already-saved 600 V
span is not silently deleted. Its saved identity, paid length and thermal state remain readable.
The new rule applies when creating a link, including replacements after removal.

## Ratings and simulation

Connection compatibility does not upgrade a 1 kV cable to a 120 kV cable or upgrade a pole's
voltage rating. Existing device watchdogs, transformer ratios, utility-span resistance,
temperature-dependent loss, thermal integration and conductor failure behavior are not relaxed.
This patch does not add a new dielectric-breakdown simulation to overhead spans. Their existing
thermal damage model must not be mistaken for comprehensive insulation-voltage protection.

## Verification

`GridCableCompatibilityTest` runs through the normal FML JUnit launcher. It checks all registered
cable/grid-descriptor combinations, the inclusive threshold, both link directions and all
horizontal orientations, invalid terminals, paid-length identity/copying, saved 600 V links,
and loaded MNA voltage drop/power balance at multiple span lengths.

`GridCableSmokeChecks`, called by the existing HV placement acceptance suite, places all six
registered device types and uses the real server click handler with survival spools for each
of the ten new HV cable variants in both click orders (120 combinations). It checks exact
consumption, both endpoint link lists, the recorded cable/length, NBT round-trip and disconnect
refunds. It also rejects old 600 V flagged cables and damaged spools. This is a dedicated-server
integration test, not native client mouse targeting or visual evidence.

Local checks used the production policy with descriptor stand-ins (218 cases), plus Kotlin
syntax parsing. Those do not establish a full build or FML/native success. Consult the exact
PR-head workflows. `grid-cable-compatibility.yml` runs the focused FML tests and the existing
translation generator, and publishes their reports/generated translations even on failure.
