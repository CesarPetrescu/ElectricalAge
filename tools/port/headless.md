# Running the 1.12.2 client and server headlessly

Both run tasks work without a desktop, which is what makes phases 2 and 3 verifiable in CI or
over ssh. `source tools/port/env.sh` first (Gradle needs JDK 25 for RetroFuturaGradle 2.x).

## Dedicated server

    mkdir -p run && echo eula=true > run/eula.txt
    printf 'online-mode=false\nlevel-type=FLAT\nmax-tick-time=-1\n' > run/server.properties
    ./gradlew runServer

Boots to `Done (…)`. `-PdumpRegistry` additionally logs every block and item Electrical Age
registers (`REGDUMP block eln:ore 16` / `REGDUMP item eln:copper_sword`) — that list is what the
model assets have to cover.

## Client

LWJGL 2.9.4 enumerates display modes through `xrandr`, and Xvfb does not give it anything usable
(`ArrayIndexOutOfBoundsException` in `XRandR.findPrimary`). An Xorg server on the dummy driver
does, as long as the *current* mode has a plain `WxH` name — LWJGL's parser matches
`^(\d+)x(\d+)$` and silently drops a mode called `1280x1024_60.00`, which leaves it with no
screens at all. Mesa's llvmpipe then provides GL 4.5.

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
    DISPLAY=:99 xrandr --noprimary          # LWJGL's parser is happier without it
    DISPLAY=:99 ./gradlew runClient

`build.gradle.kts` forwards `DISPLAY` into the run task, because the Gradle daemon is usually
started without it and `JavaExec` inherits the daemon's environment.

The client reaches the main menu. There is no audio device, so `SoundManager` logs
"Unable to initialize OpenAL" and carries on; everything else is real.

## What to grep for

    grep -c 'Exception loading model\|Exception loading blockstate' <log>   # models/blockstates
    grep -o 'FileNotFoundException: eln:[a-z0-9_./-]*' <log> | sort -u      # which file is missing
    grep 'does not exist, cannot add it to event' <log>                     # sounds.json paths
    ls -t run/crash-reports/ | head -1                                      # hard crash

All four are zero on `port/1.12.2`.

Two gotchas worth remembering, both silent:

- In a **blockstate** the `model` value is relative to `models/block/`, so it is written
  `eln:ore_copperore`. In a **model** file the `parent` is relative to `models/`, so the same
  model is `eln:block/ore_copperore`.
- Missing models are logged, not fatal. A missing *sound file* is only a warning too. A tooltip
  that dereferences `Minecraft.getMinecraft().player` **is** fatal: 1.12.2 indexes the creative
  search tree during startup, before any player exists.
