# Wire heating, overloads and failure

## What changed

Utility cables no longer discard I²R losses through the legacy temperature-rate limiter. Every
electrical step accumulates joules, which the thermal step consumes once. The HUD's **Segment
heating** is that delivered energy divided by the thermal interval. Voltage to ground is not
presented as useful delivered power. At junctions, losses are the sum of the actual branch
currents squared times the endpoint resistance, not the square of an aggregate current.

The legacy `limitTemperatureRate` API remains callable for compatibility but no longer clips
power. Legacy ideal/balanced cable and machine thermal descriptors retain their old constants;
the material/geometry model below applies to AWG/kcmil utility cables and their grid spans.

## Material model and assumptions

- One placed segment represents one metre. Area is mm² **per core**, not the whole bundle.
- Electrical R = rho20 * length / area * (1 + alpha * (T - 20)); copper rho20 .017241,
  alpha .00393; EC aluminum rho20 .028264, alpha .00403. The linear electrical model is an
  approximation at extreme temperatures; it is not an arc/plasma or AC skin-effect solver.
- Mass uses 8960 kg/m³ copper or 2700 kg/m³ aluminum and the actual total conductor volume.
- Specific heat is approximated as cp20 + slope * max(0,T-20): copper 385 J/kg/K and .132,
  aluminum 897 and .506. These are linear approximations of room-temperature to melting-point
  solid heat capacities, not an exact fit to every NIST temperature. Below 20 C cp is constant.
- Sensible energy is integrated analytically, then inverted to temperature. At 1085 C copper
  or 660 C aluminum, net energy fills a fusion reservoir (206700 / 397000 J/kg respectively).
  A completed fusion reservoir interrupts the conductor. Overshoot remains accounted for.
- Axial thermal endpoint resistance is half the segment length / (thermal conductivity * total
  metal area), with conductivity 400 W/m/K copper or 237 aluminum. It is not derived from ampacity.
- Cooling uses equivalent-round exposed bundle area, 8 W/m²/K still-air convection and secant
  radiation conductance (Kelvin). Assumed emissivity is .7 for exposed oxidized conductor and
  .9 for a generic jacket. A .5 mm jacket with conductivity .2 W/m/K adds series thermal resistance.
- Jacket thermal mass, floor-contact conduction, wind, water immersion, oxidation kinetics and
  individual strand hot spots are not modeled. Cores share one bulk temperature; currents and
  electrical losses remain independent. This is a documented lumped game model, not certified ampacity.

The native thermal network still handles signed room/ambient and node-to-node heat transfer.
No second copy of convection is subtracted by the wire heater. The current one-metre geometry's
thermal time constants are long relative to the 50 ms thermal tick; numerical tests refine the
step and compare both energy balance and failure time. There is no heat-discarding stability cap.

## Damage and saved worlds

The existing item name `Melted` is retained for registry/save compatibility. HUD state clarifies
that this initially means **damaged insulation / exposed conductor**. Cooling does not repair it.
Copper can still conduct until the metal fails. Intact-to-damaged replacement preserves temperature
and fusion energy. The final nonconductive pile retains the conductor's mass and thermal energy,
cools down, and no longer glows after it is cold. Its simple cooling geometry is the former
conductor's equivalent surface, not a detailed puddle geometry.

Existing ambient-relative temperature NBT remains readable. Phase energy and scrap conductor area
are additive fields. Item IDs and sub-IDs are unchanged. Old overloaded installations can now fail:
back up important worlds before updating and inspect wire gauge, load and protection.

Pole spans use the paid cable length, temperature-dependent resistance, material heat capacity,
cooling and persistent insulation/fusion state. The existing grid transport has **one active core**;
unused multicore area does not grant extra ampacity. Outdoor ambient is approximated by the average
at the loaded endpoints. A fused span disconnects and is consumed without an intact-spool refund;
its residual heat/material leave this lumped simulation as destroyed debris, unlike the placed
wire's retained pile. A damaged span removed before fusion returns its damaged descriptor and
original paid length. No unloaded chunk is force-loaded to sample ambient.

## Verification on GitHub

- Unit cases cover full screenshot heating, pulses, junction accounting, physical mass/capacity,
  enthalpy inversion, latent heat, cooling, state persistence and timestep convergence.
- `wire-thermal` and `wire-thermal-restart` run **every registered utility descriptor**, including
  damage variants: real isolated MNA connections, production heat sampling, fusion and energy
  balance. These are numerical integration contracts, not a claim that every variant is placed.
- Dedicated `wire-world-place` / `wire-world-restart` launch actual server JVMs and saved worlds:
  26 AWG 50 A and voltage-fed shorts, damage, interruption, normal load, power-off cooling and restart.
- Temperature/current/energy traces are CSV artifacts. Missing, incomplete or failing reports
  block release publication. Existing generator/battery, Create and companion suites remain required.
- The EC-240 evaporative cooler's independent server/restart/client contracts also gate the same release.

Minecraft test execution belongs on GitHub runners; local development needs only code/compilation.

## Sources

- [NBS Copper Wire Tables](https://nvlpubs.nist.gov/nistpubs/Legacy/hb/nbshandbook100.pdf)
- [NBS Aluminum Wire Tables](https://nvlpubs.nist.gov/nistpubs/Legacy/hb/nbshandbook109.pdf)
- [NIST copper solid/liquid enthalpy](https://janaf.nist.gov/tables/Cu-004.html)
- [NIST aluminum solid/liquid enthalpy](https://janaf.nist.gov/tables/Al-004.html)

Cooling, jacket and contact assumptions above are explicit game parameters, not claims sourced
from conductor resistance tables. Never use the mod to size real electrical installations.
