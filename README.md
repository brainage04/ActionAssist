# Action Assist

Action Assist is a client-side Fabric and NeoForge mod with two hands-off macros built for ATM10 To the Sky: a pebble macro and a crop macro. Both empty the inventory into a chest you choose; the pebble macro also clears the hand and compacts pebbles.

It replaces the external `atm10tts-autoclicker` with in-game controls. Synthetic inputs are released whenever a macro stops, a screen opens, or the player disconnects.

Use automation only where the world or server rules allow it.

## Features

**Pebble macro.** Look at a dirt block with an empty hand and start it. It holds sneak and right-clicks 20 times per second; ATM10 To the Sky's pebble script only drops pebbles for a sneaking, empty-handed right-click. When a picked-up pebble lands in your hand, the macro shift-clicks the hotbar into the main inventory. When two or fewer main-inventory slots remain free, it crafts every stack of four or more pebbles into its block in the 2x2 crafting grid. When one or no slot remains free, it deposits into the selected container.

**Crop macro.** Look at a crop and start it. It right-clicks 20 times per second, holds the vein-mining key (FTB Ultimine's by default) so right-clicks harvest and replant the whole field, and taps sneak on every other client tick for Squat Grow. That is 10 crouches per second, the most Minecraft allows: the server reads sneak once per tick, and a crouch needs one tick down and one tick up. When one or no inventory slot remains free, it deposits into the selected container.

**Deposits.** Look at a chest (or any container block within reach) and press `F6` to select it; press `F6` on it again to clear it. When the inventory fills, the macro releases its keys, opens the container, shift-clicks everything it collected into it, closes it, and carries on. You do not need to look at the container.

**Reserved slots.** Whatever occupies your inventory when a macro starts is never moved, compacted, or deposited. A macro needs at least three free slots to start. The pebble macro also needs an empty main hand.

A macro stops with an action-bar message if the inventory fills with no container selected, the container does not open within two seconds (for example, when it is out of reach), you close or replace the container while it is depositing, or the container accepts none of the items. Deposit clicks and closure are restricted to the menu opened for that deposit; interrupting it does not close your inventory or another screen. Outside a deposit, opening any screen yourself pauses the macro until the screen closes.

## Getting started

1. Install Fabric Loader plus Fabric API, or install NeoForge, for the exact Minecraft version.
2. Copy the matching Action Assist jar into the client `mods` directory.
3. Do not install the mod on a dedicated server; it contains client-only automation and input code.

Every listed Minecraft version has both a Fabric and a NeoForge artifact:

- Minecraft 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10 and 1.21.11: Java 21.
- Minecraft 26.1.2 and 26.2: Java 25.

Artifact names include the Minecraft version and loader so incompatible jars remain distinguishable.

## Commands

No commands; the client controls are:

- G502 `G5` (`Mouse Button 5`) - start or stop the pebble macro.
- G502 `G4` (`Mouse Button 4`) - start or stop the crop macro.
- `F6` - select or clear the deposit container you are looking at.

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
- `statusMessages`: `true` or `false`.

Restart the client after editing the file.

## Compatibility

The macros automate the pack's mechanics; they do not add pebble drops, crop growth or vein harvesting themselves. The F6 default can conflict with debug controls; rebind it in Key Binds if needed.

## Documentation

- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) - builds, unit tests, platform and modpack client GameTests, and recordings.
- [docs/RELEASE.md](docs/RELEASE.md) - release automation.
- [docs/MODRINTH.md](docs/MODRINTH.md) - Modrinth publishing.

License: MIT.
