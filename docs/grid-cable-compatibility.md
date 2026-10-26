# Grid cables: all intact power cables

All seven grid devices (Grid DC-DC Converter, Utility Pole, Utility Pole w/DC-DC Converter,
Transmission Tower, Direct Utility Pole, Grid Switch and Utility Pole Street Light) accept
**every intact power cable** on HV/legacy grid terminals. New 120/240 V secondary and service-drop terminals (L1, L2, 240 V and the complete bundle) require **insulated** power cable. This is checked at both ends, including the direct connection API, before cable is consumed. Original ELN power cables have jackets and remain eligible. Existing bare secondary spans are retained rather than deleted or silently insulated.
There is no minimum voltage: low-voltage legacy cables, 300/600 V spools, all HV spools and
bare conductors qualify for HV/legacy terminals. Copper/aluminum, gauge, conductor count and the historical
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
same terminal-specific rule in either click order. Transformer/switch body faces remain invalid terminals.
Overhead-only poles remain overhead devices; motor/control inputs are not grid terminals.

Survival spools pay the rounded-up endpoint distance in meters; legacy cable items pay their
item count. Cable identity, metadata and paid length stay on the link. Invalid faces, unusable
or insufficient spool lengths, duplicate links and unavailable endpoints must not consume
cable. Existing distance limits remain in place.

Previously saved links are not deleted or upgraded. Their cable identity, paid length and
thermal state remain readable. Lower-rated cables qualify for replacement HV links; replacement secondary links must be insulated.
Item registrations and save IDs are unchanged.

## Verification

`GridCableCompatibilityTest` runs through the normal FML JUnit launcher. Nine named tests cover
all registered cable/device combinations, explicit low-voltage/bare acceptance, old pole flags,
unchanged voltage/insulation ratings, both endpoint orders and horizontal rotations, rejection
of signal/damaged cables and invalid spans, paid-length identity/copying, saved 600 V links,
and loaded MNA voltage drop/power balance. The focused CI gate requires every named result and
rejects failed, missing, duplicate or skipped tests.

`GridCableSmokeChecks` places each of the seven registered grid devices and exercises the real
server click handler in survival mode for **every registered intact power-cable descriptor**
in both click orders. It checks exact consumption, both endpoint lists, identity, paid length,
NBT round-trip and refunds, and separately rejects all signal/damaged descriptors. It logs
the actual matrix size. The existing HV acceptance workflow requires its named result before
checking the saved-world restart. This is server integration, not native mouse/visual proof.

The focused workflow invokes the existing translation generator and publishes generated
translations and test reports. Full build/runtime success must be established on the exact
PR head; policy-only local checks are not FML or native-client verification.

## Pole appearance (3.0.55)

Overhead utility wire uses the installed descriptor's conductor area: diameter varies with
its square root. Copper is copper-colored and aluminum is silver. Jackets are dark, with
exposed metal at individual-wire terminations. Legacy power cable lacks area/material metadata
and uses its existing render dimensions and a copper conductor fallback.

Insulated three-circuit secondary bundles render as triplex: two wrapped jacketed conductors
and a metallic messenger. These are visual equivalents of ELN's existing L1/L2/240 V buses;
this does not introduce an AC neutral or change the electrical model. Individual selected
secondary circuits remain jacketed single-conductor branches. Existing bare bundles retain
three separate bare conductors.

The main secondary hangs on a compact suspension point. Weatherhead service drops and
individual secondary taps branch from real main-line strand positions, with short splice
sleeves/bands. Removing the drop removes its splice geometry. A pole with no main secondary
span uses its ordinary attachment point, without an invented main-line splice.

Gauge, color, jacket and splice data are included in server span publications and DH snapshots.
Complete nearby spans bake into normal terrain meshes; identical publications reuse those
meshes. Spans crossing the normal render boundary retain their whole-span fallback to avoid
a gap before the distant handoff. Both client and server must update to 3.0.55 because the span packet changed.
See [verification](release-3.0.55-verification.md).

## Pole and transformer clearance (3.0.56)

LV suspension points sit to the side of the span rather than on its path through the wood.
Their reach accounts for the installed wire diameter and transformer case. An installed can
also selects the equivalent LV support orientation facing away from it. Spans that would
intersect a pole/case envelope take a short catenary detour around it. Service splices remain
on the actual routed main strands, with space for their connector sleeves. Secondary jumpers
leave the transformer case outward before routing around the pole/case to the support.

The five physical transformer ratings now have different can diameters and heights. The
bushing height stays fixed, and leads follow the new locations. Swapping a transformer item
publishes the geometry to its neighbours too, including the node that owns an incoming span.

Clearance geometry and support reach travel in the updated span packet. Both client and
server must update to 3.0.56. Saved IDs, cable lengths, circuit assignments and electrical
ratings are unchanged. See [verification](release-3.0.56-verification.md).
