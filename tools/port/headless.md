# Running the 1.21.1 client and server headlessly

Both run tasks work without a desktop, which is what makes the port verifiable in CI or over
ssh. `source tools/port/env.sh` first (JDK 21). ModDevGradle gives every run type its own
directory under `run/` (`run/server`, `run/client`, `run/data`, `run/gameTestServer`).

## Dedicated server

    mkdir -p run/server && echo eula=true > run/server/eula.txt
    printf 'online-mode=false\nmax-tick-time=-1\n' > run/server/server.properties
    ./gradlew runServer -PdumpRegistry -PstopAfterStart=40

`-PstopAfterStart=<ticks>` (system property `eln.stopAfterStart`) halts the server that many
ticks after `ServerStartedEvent`, so the task ends and its exit code means something.
`-PdumpRegistry` logs every block and item Electrical Age registers (`REGDUMP block eln:copper_ore`)
- that list is what the generated models have to cover.

A flat world starts fastest; 1.21 wants the layers spelled out in `generator-settings`:

    level-type=minecraft\:flat
    generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":63}],"biome":"minecraft:plains"}

## Client

LWJGL 3 / GLFW needs an X display and a GL 3.2 core context. An Xorg server on the dummy driver
plus Mesa's llvmpipe (GL 4.5) is enough:

    apt-get install -y --no-install-recommends xserver-xorg-video-dummy xserver-xorg-core x11-xserver-utils

`/tmp/dummy.conf`:

    Section "Monitor"
      Identifier "Monitor0"
      HorizSync 28.0-90.0
      VertRefresh 48.0-80.0
    EndSection
    Section "Device"
      Identifier "Card0"
      Driver "dummy"
      VideoRam 256000
    EndSection
    Section "Screen"
      Identifier "Screen0"
      Device "Card0"
      Monitor "Monitor0"
      DefaultDepth 24
      SubSection "Display"
        Depth 24
        Modes "1280x1024" "1024x768"
        Virtual 1280 1024
      EndSubSection
    EndSection

Then:

    Xorg :99 -config /tmp/dummy.conf -logfile /tmp/xorg.log -noreset &
    printf 'onboardAccessibility:false\nnarrator:0\n' > run/client/options.txt
    DISPLAY=:99 ./gradlew runClient -PstopAtTitle

`build.gradle.kts` forwards `DISPLAY` into the run task, because the Gradle daemon is usually
started without it. The `options.txt` matters: a fresh profile stops on the accessibility
onboarding screen, which is not the title screen, and the run never ends. `-PstopAtTitle`
(`eln.stopAtTitle`) exits the game once the title screen is up *and* the loading overlay is gone,
i.e. after models are baked. There is no audio device, so `SoundEngine` logs "Failed to open
OpenAL device" and carries on; everything else is real.

## The smoke tests

`tools/port/smoke.sh` runs everything below in order and stops at the first failed run: every
check logs `SMOKE PASS`/`SMOKE FAIL`, and a run with a failed check exits 1 once the game has
stopped, which fails the Gradle task. `-PwithoutCc` leaves CC: Tweaked out of a run.

The dedicated server places a circuit, a lamp and the computer probe through the real item-use
path (a FakePlayer), reads the meters, the light and the peripheral, and reads them again after a
restart against the saved world. South of those, the rotating machines: a shaft line (shaft motor,
joint, tachometer, flywheel, generator) fed by a creative source and loaded with a resistor; the
large 3x3x3 motor and generator with a joint at shaft height between them; a gallery of every other
shaft machine (the turbines with a blade, the radial motor over open floor); and the early-game
generator, a stone heat furnace burning coal into a 48V turbine through a thermal cable. Those are
read 12 seconds after placement (`LAST_TICK`), once the shafts have spun up:

    ./gradlew runServer -PsmokeTest=place      # SMOKE PASS ... energised=true current flowing=true
                                               # SMOKE PASS lamp: node light=13 aux=13 block light at socket=13; ...
                                               # SMOKE PASS computer probe ... peripheral type=ElnProbe methods=[...]
                                               # SMOKE PASS spool '...' into a lamp socket / dropped on the GUI slot / shift-clicked
                                               # SMOKE PASS shaft line is one network / turns at 200 rad/s / generator delivers 477 V 4.8 A
                                               # SMOKE PASS large shaft line ... / large generator delivers 3.2 kV ...
                                               # SMOKE PASS shaft machine gallery present / heat generator: furnace at ... C, 48V turbine delivers ...
    ./gradlew runServer -PsmokeTest=verify     # the same after the restart; also runs /eln on the console; then breaks
                                               # the flywheel (the network must split) and the large motor (its ghosts must go)
    ./gradlew runServer -PsmokeTest=all        # place, plus every placeable descriptor on a grid north of it:
                                               # SMOKE ALL placed 388 of 401 descriptors / 414 nodes alive after 80 ticks

The chunks under the test are force-loaded by the test (nobody is online, and a chunk that is only
touched by a block access drops out a tick later, taking its ticking block entities with it). The
ore count in the `place` log is only meaningful on normal terrain (`level-type=minecraft:normal`
in `run/server/server.properties`); on the flat dev world it says SKIP. `all` is the run to make
after touching anything an element does at placement or in its first ticks: an exception in one
of them stops the server, and the log names the descriptor.

The client joins a copy of that world (taken before the restart run breaks things), flies to the
circuit and screenshots the world by day and by midnight (the lamp), the descriptor grid from above,
the shaft line twice a second apart (`smoke-mech`, `smoke-mech-spin`: the client-side shaft angle
must have moved on, which is the published speed integrated by the renderer), the tachometer's
screen, the large machines, the gallery, a third-person view with items on the floor, the macerator
in hand from the front and through the player's eyes, a cable in hand, the resistor GUI, the
macerator's container GUI, the inventory and two Electrical Age creative tabs into
`run/client/screenshots/`:

    cp -r run/server/world run/client/saves/smoke
    DISPLAY=:99 ./gradlew runClient -PsmokeClient=smoke

`SMOKE PASS` for the three screens and the two turning shafts, no crash report, and the pictures
are the evidence (`docs/port/smoke-*-1.21.png` are the committed ones). Software GL (llvmpipe) on two cores takes
about a minute per run; do not run two game processes at once on a small box, the second Gradle
daemon and the Kotlin compile daemon together push it into swap.

## What to grep for

    grep -c 'Missing textures in model' <log>          # atlas / model texture problems
    grep 'Unable to load model\|Exception loading' <log>  # model JSON problems
    grep 'REGDUMP' <log>                                 # what was registered
    ls -t run/client/crash-reports/ | head -1            # hard crash

All zero on `port/1.21.1` for the registered content.

Two gotchas worth remembering, both silent:

- Since 1.19.3 the block/item atlas only stitches the directories listed in
  `atlases/blocks.json` (`block/` and `item/` by default). The mod keeps its 1.12 layout
  (`textures/items/`, `textures/blocks/`, `textures/voltages/`), so it ships
  `assets/minecraft/atlases/blocks.json` adding those sources. The file must be in the
  `minecraft` namespace: atlas definitions merge per atlas id, and `eln:blocks` would be a
  different, unused atlas.
- `Item`/`Block` constructors throw "Registry is already frozen" outside `RegisterEvent`.
  Anything that builds one in the mod constructor is a bug; stage it in `ElnRegistry`.
