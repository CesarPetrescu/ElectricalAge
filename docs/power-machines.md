# Generators, shaft motors and batteries

## Wiring and power flow

- **Generator / Large Generator:** the front and rear floor-level electrical ports are the **same positive terminal**. The return is electrical ground. Connect the load's return to a Ground Cable; connecting both load terminals to these two positive ports shorts out the load instead of powering it.
- **Polarized Shaft Generator / Motor:** front is positive, rear is negative. Use both terminals for a floating two-wire circuit. The shaft connects on the other two sides.
- **Shaft Motor / Large Shaft Motor:** normal operation consumes electricity to turn the shaft. Driving the shaft externally also generates electricity, including into an already powered bus or a battery, but at only **10% conversion efficiency**. A dedicated generator is **95% efficient** before winding and friction losses.
- Feeding a generator electrically turns it as a motor, also at **10% efficiency**. Reverse operation produces substantial waste heat; it is not an efficient substitute for the correct machine.
- Electrical current needs a complete circuit **and a load**. A spinning unloaded generator can show voltage while delivering essentially zero current. Shaft power and speed must be sufficient for the connected load.

Standard machines are rated **480 V at 200 rad/s**; large machines use the **3.2 kV** cable tier. Generator voltage depends on shaft speed. Batteries are rated **12, 24, 48 or 120 V**, depending on type: do not connect them directly to a nominal-speed 480 V generator. Use suitable voltage conversion and charge-current control. A generator current limiter is not a battery charger.

## Battery limits

Stored charge is bounded from empty to full. Charging a full battery, or trying to recharge a single-use battery, creates heat instead of extra stored energy. Battery internal resistance, temperature protection and aging still apply. Charged batteries restore their voltage immediately when placed or loaded; partial item metadata uses defaults only for missing fields, never for an explicitly empty charge.

## Indicator lights

Generator LEDs indicate delivered power; reverse electrical consumption uses red. Motor LEDs indicate actual current draw (green/yellow/red load bands), and reverse generation uses red. Unloaded machines may have mostly dark indicators. LED meshes update their tint every frame and remain visible in darkness.

## Automated regression coverage

GitHub Actions runs the normal standalone and Create smoke profiles, as well as the companion-mod compatibility jobs. No local Minecraft launch is required to run this CI.

The dedicated-server `power-behavior` suites use every registered generator, shaft motor and battery variant with the real MNA solver and production process classes:

- Generator no-load, 10%, 50%, nominal and overload circuits; output power, current limits, stopping and energy balance.
- Generator charging a battery, and battery-driven reverse motor operation.
- Motor startup, reverse generation into a live supply, conversion heat and energy balance; polarized motor tests at three common-mode voltages.
- Battery discharge, recharge/single-use rejection, full/empty boundaries and charge/life NBT round trips.
- Fresh battery placement voltage and configured self-discharge on every supported mounting orientation.
- Actual client OBJ vertex colors: all seven LEDs on every motor/generator descriptor, repeatedly black → green → yellow → red → black, with full-bright lightmap values.

JSON/JUnit reports live in the smoke artifacts under `contracts/power-*.json` and `.xml`. Missing, incomplete or failing server reports block release publication. Client assertions fail the client job. These are additional targeted checks, not a claim that every possible machine combination, inventory lifecycle or visual design is covered. The isolated solver scenarios are repeated in the restarted server; existing placed-world mechanical circuits also survive a real save/reload.
