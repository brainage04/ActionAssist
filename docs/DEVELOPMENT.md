# Development

Use JDK 25 to run Gradle. The build toolchains compile 1.21 targets for Java 21 and 26.x targets for Java 25.

```shell
./gradlew --no-daemon build
./gradlew --no-daemon test
```

All 16 distributable jars are collected in `build/libs`. Per-target outputs remain under `targets/<target>/<loader>/build/libs`.

The loader-independent unit contracts cover the macro state machine (click rates, sneak taps, hotbar clearing, compaction clicks, deposits and their failure stops) and the settings file.

## Platform GameTest (every version and loader)

One scenario plays both macros end to end on all 16 targets. It launches each development client straight into a flat fixture world with `--quickPlaySingleplayer`, uses only vanilla blocks and recipes, and stands in for the modded mechanics on the server: sneaking empty-hand right-clicks on dirt yield vanilla "pebbles" in runs of eight (clay balls, snowballs, quartz, amethyst shards, honeycomb, plus flint and sticks that never compact), each crouch grows the wheat, and right-clicking mature wheat with the vein-mining key held harvests the whole row. The macros' own work runs for real: key handling, hotbar clearing, 2x2 crafting, and chest deposits. Each target must select the chest with `F6`, conserve every generated pebble between chest and inventory (counting a block as four), leave reserved slots alone, pause and release its keys while a screen is open, vein-harvest, and deposit. Every target then has to report the same outcome:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon platformGameTest
```

Run a single target with `:<target><Loader>:runPlatformGameTest`, for example `:mc1211Fabric:runPlatformGameTest`. Reports are written to `run/platformGameTest/<version>-<loader>/actionassist-platform-report.properties`.

Build and GameTest tasks can share one Gradle invocation. Shared asset downloads and modpack runtime extraction are ordered before the runs that use them. The build workflow runs all client GameTests in one task graph so Gradle checks these producer-consumer dependencies.

Crop setup waits for client-visible, lit farmland before planting, then for all three wheat blocks before aiming and for the centre-wheat crosshair hit before starting the macro. Server task completion is not a block-update acknowledgement; frame catch-up can advance client ticks while those updates are still pending. Each readiness phase has a bounded timeout, not a fixed delay or replant retry.

The screen-pause check opens the inventory synchronously through vanilla's screen setter, before another end-tick listener can begin a deposit. A queued inventory-key click would not establish that boundary and can be cleared by `KeyMapping.releaseAll` before vanilla handles it. The check requires the actual inventory screen to remain open until the scenario closes it, observes fixture-block uses on the client thread throughout the pause, and rejects every use with a screen open. Server receipt is not evidence of client dispatch: frame catch-up can leave pre-screen packets pending on the integrated server. Closing the inventory must resume client uses.

## Fabric client GameTests

The Minecraft 26.2 Fabric client GameTest runs the same scenario under Fabric's client GameTest harness, against development classes and against the packaged jar in Loom's isolated production client:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon --configure-on-demand :mc262Fabric:runClientGameTest

ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
./gradlew --no-daemon --configure-on-demand :mc262Fabric:runProductionClientGameTest
```

Record the packaged scenario with `:mc262Fabric:recordClientGameTest`. Video, metadata, and the retained run workspace are written below `build/recordings`.

## ATM10 To the Sky GameTest (real mods)

The Minecraft 1.21.1 NeoForge compatibility GameTest runs the macros against the mods ATM10 To the Sky uses for them: Ex Deorum, KubeJS with the pack's own pebble script, Squat Grow with the pack's configuration, Mystical Agriculture, and FTB Ultimine with the pack's server configuration. The mod jars go into that run's `mods` directory only. Each macro runs for at least 30 seconds. The pebble macro must keep the hand clear, compact pebbles with Ex Deorum's recipes, and deposit repeatedly. The crop macro must grow inferium crops through Squat Grow, harvest crops it never clicked through FTB Ultimine (confirmed pressed on the server), and deposit the essence:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon --configure-on-demand :mc1211Neoforge:runClientGameTest
```

The stop-key checks finish any in-flight deposit before queuing `G5` or `G4`. Opening the deposit screen calls vanilla `KeyMapping.releaseAll`, which clears queued key clicks; a click queued while the container response is pending is not evidence that the macro received a stop request. The scenario keeps its original stop-assertion ticks, without retrying the key or extending the assertion wait.

Crop setup uses client-visible farmland, crop survival, planted crops and the crosshair hit as readiness boundaries instead of fixed tick delays.

It remains recordable with `:mc1211Neoforge:recordClientGameTest`; the extended start wait documented by `GTR_RECORDING_START_WAIT_SECONDS=240` accommodates first-run model loading.

Launch a specific development client with configuration on demand to avoid initializing unrelated Minecraft toolchains:

```shell
./gradlew --no-daemon --configure-on-demand :mc1211Fabric:runClient
./gradlew --no-daemon --configure-on-demand :mc262Neoforge:runClient
```
