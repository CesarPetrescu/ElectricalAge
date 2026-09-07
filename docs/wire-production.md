# Wire production and resistance

Utility cables (AWG/kcmil and multicore families) are **machine products**, not ordinary crafting-table recipes. Press **P** and open a cable to see its machine, inputs, output length and reusable tools. Click inputs to follow the production chain.

## Survival production

1. Craft a **Wire Roller**, two **Iron Roller Wheels**, and a **Wire Insulator**. These use ordinary ingots and the existing raw-wire/motor/machine-block progression, not utility cables. Steel and aluminum roller wheels also work and are reusable.
2. Power the roller, insert copper or aluminum ingots and both wheels, then choose a gauge and target length. One ingot supplies one kilogram in the game's material balance. Metal used is `density × cross-section × length`; unused metal remains buffered.
3. Feed bare wire and rubber into the insulator. One rubber supplies 32 metres of insulation. It coats the entire input spool; enough rubber must be buffered for that length. Wire gauge, metal and length are preserved.
4. For multicore cable, craft a **Wire Combiner**. Insert 2–8 insulated single cores of the same metal and gauge, choose the desired bundle with the arrow buttons, then insulate that bundle. The shortest core sets the output length; longer cores retain their unused lengths.

Machines use a nominal 200 V supply: roller 300 W / 6 m/s, insulator 160 W / 4 m/s, combiner 120 W / 5 m/s. Lower power slows processing. Connect power to their bottom-edge electrical ports and provide the ELN circuit return/reference as usual.

The wiki shows **32 m examples**, not a fixed output limit. Roller length is selectable from 1 to 65,536 m. Partial spools carry their actual length; the creative inventory's 128 m spool is not a crafting yield. Damaged/melted wire is a failure product, not a manufacturing recipe.

Existing combiner saves preserve inputs 0–4 and output 5; the three new inputs use slots 6–8.

## Electrical properties

For each intact conductor:

`R20 = rho20 × length_m / area_mm²`

`R(T) = R20 × [1 + alpha20 × (T_C − 20)]`

| Conductor model | Resistivity at 20 °C (Ω·mm²/m) | Temperature coefficient (/°C) |
|---|---:|---:|
| Annealed copper | 0.017241 | 0.00393 |
| EC-grade aluminum (61% IACS) | 0.028264 | 0.00403 |

Values follow [NBS Copper Wire Tables, Handbook 100](https://nvlpubs.nist.gov/nistpubs/Legacy/hb/nbshandbook100.pdf) and [NBS Aluminum Wire Tables, Handbook 109](https://nvlpubs.nist.gov/nistpubs/Legacy/hb/nbshandbook109.pdf). This is a simplified DC model, not an electrical-installation design tool.

Each placed segment consumes and simulates **one metre**, irrespective of the remaining spool length. Each core of a multicore cable has its own resistance; eight cores do not magically reduce each core's resistance eightfold. ELN stores half the segment resistance at an electrical node; the two connection endpoints add it correctly. Joule heating uses `I²R`. Temperature updates use absolute ambient-plus-heating temperature, with a 0.1% change threshold to avoid needless matrix rebuilds.

For example, 12 AWG copper (3.309 mm²) has about **5.21 mΩ/m at 20 °C**. A 10 m conductor is about 52.1 mΩ; at 20 A it drops about 1.04 V and dissipates about 20.8 W. A separate 10 m return conductor adds its own resistance.

The tooltip shows per-core resistance per metre and over the current spool; the in-world overlay shows the segment's current resistance. Modern utility cables no longer use the old arbitrary near-zero resistance formula. Legacy voltage-tier and ideal creative/signal devices retain their existing models because they have no specified physical conductor geometry.

Limits: no contact resistance, AC skin/proximity effect or strand lay factor; the low-temperature linear factor is clamped positive, not a superconductivity model. Insulation and current ratings remain gameplay limits. Metal densities retain the existing nominal game values, 8,960 kg/m³ copper and 2,700 kg/m³ aluminum.

## Regression gates

GitHub's standalone and Create-enabled smoke servers require `wire-behavior` and `wire-behavior-restart` reports. Each non-damaged utility descriptor is manufactured through the machine chain and checked for identity, length, material/rubber balance, reusable wheels and occupied-output safety. The restart variant serializes and reloads intermediate machines mid-process. Actual packaged table recipes must resolve nonempty ingredients and assemble the machines/wheels.

Every intact descriptor also gets an MNA voltage-drop test, checking resistance per core and warmer-wire behavior. A rendered client contract verifies that an eight-core cable page displays its machine recipe. Pure unit tests cover resistivity reference values, geometry scaling, temperature and invalid inputs. These checks supplement—not replace—the existing placed-block and separate-server-restart suites.
