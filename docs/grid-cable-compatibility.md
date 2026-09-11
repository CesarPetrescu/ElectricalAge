# Grid cables: all intact power cables

All six grid devices (Grid DC-DC Converter, Utility Pole, Utility Pole w/DC-DC Converter,
Transmission Tower, Direct Utility Pole and Grid Switch) accept **every intact power cable**.
There is no minimum voltage: low-voltage legacy cables, 300/600 V spools, all HV spools and
bare conductors qualify. Copper/aluminum, gauge, conductor count and the historical
`poleEligible` flag do not restrict compatibility.

The `signalWire` flag, not the creative-tab category, distinguishes actual signal circuits.
Thin utility wires listed in a signal category still qualify when they are power conductors.
Actual signal cables/buses and melted cables are rejected. This rule concerns overhead grid
links; it does not turn thermal cables or unrelated circuit types into power cables.

## Ratings and bare wire

Eligibility is separate from electrical safety and capacity. Insulated utility cable retains
its jacket voltage rating; bare and legacy power cable retain their nominal gameplay rating.
Bare wire stays uninsulated, including its zero insulation-voltage field. Connecting a 300 V
cable does not upgrade it to a 120 kV cable. The policy never rewrites either rating.

Existing device watchdogs, transformer ratios, utility-span resistance, temperature-dependent
loss, thermal integration and conductor failure are unchanged. This patch does not add new
dielectric-breakdown simulation to overhead spans. Their thermal model is not comprehensive
insulation-voltage protection, and mere connection compatibility is not proof of safe operation.

## Connecting and saved worlds

Hold an intact power cable and click a grid terminal on each device. Both endpoints use the
same rule in either click order. Transformer/switch body faces remain invalid terminals.
Overhead-only poles remain overhead devices; motor/control inputs are not grid terminals.

Survival spools pay the rounded-up endpoint distance in meters; legacy cable items pay their
item count. Cable identity, metadata and paid length stay on the link. Invalid faces, unusable
or insufficient spool lengths, duplicate links and unavailable endpoints must not consume
cable. Existing distance limits remain in place.

Previously saved links are not deleted or upgraded. Their cable identity, paid length and
thermal state remain readable; lower-rated cables also qualify for new/replacement links now.
Item registrations and save IDs are unchanged.

## Verification

`GridCableCompatibilityTest` runs through the normal FML JUnit launcher. Nine named tests cover
all registered cable/device combinations, explicit low-voltage/bare acceptance, old pole flags,
unchanged voltage/insulation ratings, both endpoint orders and horizontal rotations, rejection
of signal/damaged cables and invalid spans, paid-length identity/copying, saved 600 V links,
and loaded MNA voltage drop/power balance. The focused CI gate requires every named result and
rejects failed, missing, duplicate or skipped tests.

`GridCableSmokeChecks` places each of the six registered grid devices and exercises the real
server click handler in survival mode for **every registered intact power-cable descriptor**
in both click orders. It checks exact consumption, both endpoint lists, identity, paid length,
NBT round-trip and refunds, and separately rejects all signal/damaged descriptors. It logs
the actual matrix size. The existing HV acceptance workflow requires its named result before
checking the saved-world restart. This is server integration, not native mouse/visual proof.

The focused workflow invokes the existing translation generator and publishes generated
translations and test reports. Full build/runtime success must be established on the exact
PR head; policy-only local checks are not FML or native-client verification.
