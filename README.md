# Action Assist

Action Assist is a client-side Minecraft mod for repetitive interactions such as collecting pebbles, accelerating crops with repeated sneaking, keeping a vein-mining key held, harvesting and replanting crops, and moving a full hotbar into the player inventory.

It replaces the external `atm10tts-autoclicker` with in-game controls. Synthetic inputs are released whenever automation is disabled, a screen opens, or the player disconnects.

Use automation only where the world or server rules allow it.

## Controls

| Default key | Control | Behaviour |
| --- | --- | --- |
| G502 `G5` (`Mouse Button 5`) | Toggle automation | Starts or stops the configured use/attack action. Sneak mode starts at `hold`. |
| G502 `G4` (`Mouse Button 4`) | Cycle sneak mode | Cycles `hold` → `spam` → `none`. |
| `F6` | Transfer hotbar | Quick-moves occupied hotbar slots into available player-inventory slots. Automation must be enabled. |
| `` ` `` | Companion hold | Shared binding held during `spam`; bind a vein-mining activation to the same key. |

All four bindings can be changed under **Options → Controls → Key Binds → Action Assist**.

The default `use` action supports mechanics driven by repeated right-clicks, including pebble collection and right-click crop harvesting. Change `action` to `attack` for repeated left-clicks.

## Configuration

The first client launch creates `config/actionassist.properties`:

```properties
action=use
actionsPerSecond=20
sneakTapsPerSecond=8
statusMessages=true
```

- `action`: `use` or `attack`
- `actionsPerSecond`: `1`–`20`
- `sneakTapsPerSecond`: `1`–`10`
- `statusMessages`: `true` or `false`

Restart the client after editing the file. Action rates are intentionally bounded by Minecraft's 20 client ticks per second.

## Supported versions

Every listed Minecraft version has both a Fabric and a NeoForge artifact:

| Minecraft | Java |
| --- | --- |
| 1.21.1 | 21 |
| 1.21.4 | 21 |
| 1.21.5 | 21 |
| 1.21.8 | 21 |
| 1.21.10 | 21 |
| 1.21.11 | 21 |
| 26.1.2 | 25 |
| 26.2 | 25 |

These targets cover the major 1.21-era modpack releases and the current 26.x loader ecosystem without pretending one jar is binary-compatible across Minecraft versions.

## Installation

1. Install Fabric Loader plus Fabric API, or install NeoForge, for the exact Minecraft version.
2. Copy the matching Action Assist jar into the client `mods` directory.
3. Do not install the mod on a dedicated server; it contains client-only automation and input code.

Artifact names include the Minecraft version and loader so incompatible jars remain distinguishable.

## Building

Use JDK 25 to run Gradle. The build toolchains compile 1.21 targets for Java 21 and 26.x targets for Java 25.

```shell
./gradlew --no-daemon build
```

All 16 distributable jars are collected in `build/libs`. Per-target outputs remain under `targets/<target>/<loader>/build/libs`.

Run the loader-independent unit contracts, including every supported action and
sneak rate plus valid, partial, generated, and invalid settings files:

```shell
./gradlew --no-daemon test
```

The Minecraft 26.2 Fabric client GameTest exercises the complete user-facing
contract: first-launch configuration, translations and default/rebound controls,
`use` and `attack`, all three sneak modes, exact sneak spam, companion hold,
status suppression, screen-open release/resume, disabled/full/limited-capacity
hotbar transfers, disconnect release, and shutdown release.

Run it against development classes:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon --configure-on-demand :mc262Fabric:runClientGameTest
```

Run the same assertions against the packaged Fabric jar in Loom's isolated
production client:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
./gradlew --no-daemon --configure-on-demand :mc262Fabric:runProductionClientGameTest
```

Record the packaged complete scenario with its on-screen showcase descriptions:

```shell
./gradlew --no-daemon --configure-on-demand :mc262Fabric:recordClientGameTest
```

Video, metadata, and the retained run workspace are written below
`build/recordings`.

The separate Minecraft 1.21.1 NeoForge compatibility GameTest loads Ex Deorum,
Squat Grow, Mystical Agriculture, FTB Ultimine, and their real runtime
dependencies. It verifies pebble farming, crop growth, vein-mining key hold,
crop harvest/replant, and hotbar transfer:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon --configure-on-demand :mc1211Neoforge:runClientGameTest
```

Its focused compatibility scenario remains recordable with
`:mc1211Neoforge:recordClientGameTest`; the extended start wait documented by
`GTR_RECORDING_START_WAIT_SECONDS=240` accommodates first-run model loading.

Launch a specific development client with configuration on demand to avoid initializing unrelated Minecraft toolchains:

```shell
./gradlew --configure-on-demand :mc1211Fabric:runClient
./gradlew --configure-on-demand :mc262Neoforge:runClient
```

## License

MIT
