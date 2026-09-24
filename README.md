# Action Assist

Action Assist is a client-side Minecraft mod with two hands-off macros built for ATM10 To the Sky: a pebble macro and a crop macro. Both keep running unattended: they clear the hand, compact pebbles, and empty the inventory into a chest you choose.

It replaces the external `atm10tts-autoclicker` with in-game controls. Synthetic inputs are released whenever a macro stops, a screen opens, or the player disconnects.

Use automation only where the world or server rules allow it.

## Macros

**Pebble macro.** Look at a dirt block with an empty hand and start it. It holds sneak and right-clicks 20 times per second; ATM10 To the Sky's pebble script only drops pebbles for a sneaking, empty-handed right-click. When a picked-up pebble lands in your hand, the macro shift-clicks the hotbar into the main inventory. When two or fewer main-inventory slots remain free, it crafts every stack of four or more pebbles into its block in the 2x2 crafting grid. When one or no slot remains free, it deposits into the selected container.

**Crop macro.** Look at a crop and start it. It right-clicks 20 times per second, holds the vein-mining key (FTB Ultimine's by default) so right-clicks harvest and replant the whole field, and taps sneak on every other client tick for Squat Grow. That is 10 crouches per second, the most Minecraft allows: the server reads sneak once per tick, and a crouch needs one tick down and one tick up. When one or no inventory slot remains free, it deposits into the selected container.

**Deposits.** Look at a chest (or any container block within reach) and press `F6` to select it; press `F6` on it again to clear it. When the inventory fills, the macro releases its keys, opens the container, shift-clicks everything it collected into it, closes it, and carries on. You do not need to look at the container.

**Reserved slots.** Whatever occupies your inventory when a macro starts is never moved, compacted, or deposited. A macro needs at least three free slots to start. The pebble macro also needs an empty main hand.

A macro stops with an action-bar message if the inventory fills with no container selected, the container does not open within two seconds (for example, when it is out of reach), you close the container while it is depositing, or the container accepts none of the items. Opening any screen yourself pauses the macro until the screen closes.

## Controls

| Default key | Control |
| --- | --- |
| G502 `G5` (`Mouse Button 5`) | Start or stop the pebble macro |
| G502 `G4` (`Mouse Button 4`) | Start or stop the crop macro |
| `F6` | Select or clear the deposit container you are looking at |

Starting one macro while the other runs switches to it. The bindings can be changed under **Options → Controls → Key Binds → Action Assist**.

## Configuration

The first client launch creates `config/actionassist.properties`:

```properties
veinMineKey=key.ftbultimine
compactItems=exdeorum\:stone_pebble,exdeorum\:andesite_pebble,exdeorum\:basalt_pebble,exdeorum\:blackstone_pebble,exdeorum\:calcite_pebble,exdeorum\:deepslate_pebble,exdeorum\:diorite_pebble,exdeorum\:granite_pebble,exdeorum\:tuff_pebble
statusMessages=true
```

- `veinMineKey`: the name of the key mapping the crop macro holds. Another vein miner works if you use its key mapping name. If no loaded mod registers the name, the crop macro runs without it and says so.
- `compactItems`: comma-separated item ids the pebble macro crafts in the 2x2 grid. Leave it empty to disable compaction. An item that does not craft is skipped until the macro restarts.
- `statusMessages`: `true` or `false`

Restart the client after editing the file. Settings files from earlier versions keep working: their old properties are ignored.

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

Run the loader-independent unit contracts for the macro state machine (click
rates, sneak taps, hotbar clearing, compaction clicks, deposits and their
failure stops) and the settings file:

```shell
./gradlew --no-daemon test
```

### Platform GameTest (every version and loader)

One scenario plays both macros end to end on all 16 targets. It launches each
development client straight into a flat fixture world with
`--quickPlaySingleplayer`, uses only vanilla blocks and recipes, and stands in
for the modded mechanics on the server: sneaking empty-hand right-clicks on dirt
yield vanilla "pebbles" in runs of eight (clay balls, snowballs, quartz,
amethyst shards, honeycomb, plus flint and sticks that never compact), each
crouch grows the wheat, and right-clicking mature wheat with the vein-mining key
held harvests the whole row. The macros' own work runs for real: key handling,
hotbar clearing, 2x2 crafting, and chest deposits. Each target must select the
chest with `F6`, conserve every generated pebble between chest and inventory
(counting a block as four), leave reserved slots alone, pause and release its
keys while a screen is open, vein-harvest, and deposit. Every target then has
to report the same outcome:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon platformGameTest
```

Run a single target with `:<target><Loader>:runPlatformGameTest`, for example
`:mc1211Fabric:runPlatformGameTest`. Reports are written to
`run/platformGameTest/<version>-<loader>/actionassist-platform-report.properties`.

The Minecraft 26.2 Fabric client GameTest runs the same scenario under Fabric's
client GameTest harness, against development classes and against the packaged
jar in Loom's isolated production client:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon --configure-on-demand :mc262Fabric:runClientGameTest

ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
./gradlew --no-daemon --configure-on-demand :mc262Fabric:runProductionClientGameTest
```

Record the packaged scenario with
`:mc262Fabric:recordClientGameTest`. Video, metadata, and the retained run
workspace are written below `build/recordings`.

### ATM10 To the Sky GameTest (real mods)

The Minecraft 1.21.1 NeoForge compatibility GameTest runs the macros against the
mods ATM10 To the Sky uses for them: Ex Deorum, KubeJS with the pack's own pebble
script, Squat Grow with the pack's configuration, Mystical Agriculture, and FTB
Ultimine with the pack's server configuration. The mod jars go into that run's
`mods` directory only. Each macro runs for at least 30 seconds. The pebble macro
must keep the hand clear, compact pebbles with Ex Deorum's recipes, and deposit
repeatedly. The crop macro must grow inferium crops through Squat Grow, harvest
crops it never clicked through FTB Ultimine (confirmed pressed on the server),
and deposit the essence:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon --configure-on-demand :mc1211Neoforge:runClientGameTest
```

It remains recordable with `:mc1211Neoforge:recordClientGameTest`; the extended
start wait documented by `GTR_RECORDING_START_WAIT_SECONDS=240` accommodates
first-run model loading.

Launch a specific development client with configuration on demand to avoid initializing unrelated Minecraft toolchains:

```shell
./gradlew --configure-on-demand :mc1211Fabric:runClient
./gradlew --configure-on-demand :mc262Neoforge:runClient
```

## License

MIT
