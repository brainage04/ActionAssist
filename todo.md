# ActionAssist todo

## Loader parity findings (2026-09-29)

From running the release NeoForge jar on a real NeoForge 26.2.0.41-beta server and client. Items marked *both loaders* come from shared code.

- [x] **Medium, 26.2:** no status message ever shows (start/stop, hand not empty, fewer than 3 free slots, no deposit container, container selected/cleared, vein key missing). `StatusMessages.java:39-53` looks up a `(Component, boolean)` player method by reflection; 26.2 has none, so it falls back to a single-`Component` method, which resolves to `Entity.setCustomName` and renames the local player instead.
  Fixed: `StatusMessages` calls a per-version `ActionBar` (`src/client-1.21`: `displayClientMessage(msg, true)`; `src/client-26`: `sendOverlayMessage(msg)`), no reflection; the platform GameTest asserts the F6 message reaches the action bar and does not rename the player.
- [ ] Low: the default F6 key conflicts with vanilla's debug options key.

- [ ] Flaky platform GameTest: "Expected the vein-mining key held on every clicking tick" fails off by one tick (e.g. 234/235, 228/229) on random targets under load (1.21.1/1.21.4/26.1.2 NeoForge; seen locally three times and in CI on 2026-10-01). The 26.2 Fabric "inventory key opens the inventory screen" assertion failed once the same way. Make the tick accounting tolerate the first/last tick instead of rerunning.
