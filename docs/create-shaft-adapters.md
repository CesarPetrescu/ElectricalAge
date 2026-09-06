# Create shaft adapters

Electrical Age includes optional support for Create 6.0.10 on NeoForge / Minecraft 1.21.1.
Install Create alongside ELN and Kotlin for Forge to enable the two adapter blocks and their recipes.
ELN still works without Create. The adapters appear under **Electrical Age - Mechanics**.

| Adapter | Maximum mechanical output | Maximum output torque | Housing |
| --- | --- | --- | --- |
| Create Shaft Adapter | 4 kW | 40 N·m | Iron |
| Industrial Create Shaft Adapter | 16 kW | 160 N·m | Gold |

## Connecting and operating

The gray port takes rotation from a Create shaft. The copper port on the opposite face connects to
an ELN shaft, joint, flywheel, or shaft machine. Placement points the copper port toward the face
you clicked. Vertical connections are supported. Both Create rotation directions work; ELN's
current shaft model records speed without a direction sign.

Right-click the adapter to open its controls. It starts engaged with an 8:1 step-up ratio.
Disengage before changing between 1:1, 2:1, 4:1, and 8:1. Re-engagement accelerates the attached
machinery using the available torque and power. Changing gear never resets a flywheel's speed.
The screen reports input RPM, target and actual output speed, output watts, and Create stress.

At 256 RPM, the 8:1 setting targets about 214.5 rad/s. The target is not a guarantee: heavy loads
slow the ELN network, and its inertia takes time to accelerate. Gearing changes target speed,
not the adapter's power limit. A shaft already turning faster than the target freewheels;
the adapter does not brake it or feed energy back into Create.

## Stress and overloads

The drive requests stress based on the mechanical power needed to approach the target, including
acceleration and connected loads. By default, one total Create stress unit pays for one input watt;
90% of input power becomes mechanical output. Thus 4 kW of output requires about 4,444 SU.
Create's per-RPM base impact is computed from that total and the current input RPM.

The Create network accepts an increase in stress before the adapter delivers that energy.
Stress demand falls over roughly half a second when a load disappears. No energy is supplied
when stopped, disengaged, disconnected, or overstressed. There is no hidden energy buffer.

An overstressed network trips the adapter's clutch and removes its stress demand. ELN machinery
coasts under its normal loads. Fix the insufficient Create capacity, then press **Reset fault**
or send a rising redstone edge. **Automatic retry** is off by default; when enabled it retries
every five seconds. A target above 240 rad/s also trips the adapter, below ELN's 250 rad/s shaft
limit. Reduce input RPM or disengage and select a lower ratio before resetting that fault.

Settings and fault state survive saves. Each adapter accounts for its own energy when several
drive one shaft network. Removing or unloading an adapter detaches its ELN shaft connection.

## Crafting

Basic adapter: `ISI / CMC / ISI`, where I = iron ingot, S = Create shaft,
C = Create cogwheel, and M = Create precision mechanism.

Industrial adapter: `BSB / PMP / BSB`, where B = Create brass ingot, S = Create shaft,
P = Create precision mechanism, and M = basic adapter.

## Server balance settings

The existing ELN configuration accepts these settings; restart the server after changing them:

| Key | Default |
| --- | --- |
| `integrations.create.basicPower` | `4000.0` W |
| `integrations.create.industrialPower` | `16000.0` W |
| `integrations.create.basicTorque` | `40.0` N·m |
| `integrations.create.industrialTorque` | `160.0` N·m |
| `integrations.create.efficiency` | `0.9` |
| `integrations.create.wattsPerStressUnit` | `1.0` |

Invalid/non-finite settings fall back to defaults; efficiency is limited to (0, 1].
Power and torque are bounded to prevent extreme configuration values destabilizing the simulation.

## Verification

`./gradlew build` checks the inventory categories and the independent drive calculations without
installing Create. `./gradlew runServer -PwithCreate -PcreateSmoke=place` builds two real Create
networks driving ELN joints, flywheels, and loaded generators, then tests overload/reset behavior.
Run `-PcreateSmoke=verify` against that saved world to check a restart.

CI runs the entire server/restart/client smoke suite both with and without Create. The Create run
also captures both adapters and their configuration screens. The release job waits for both runs.
Smoke tests own their test-world locations and should be run in a development instance.
