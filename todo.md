# ActionAssist todo

## Loader parity findings (2026-09-29)

From running the release NeoForge jar on a real NeoForge 26.2.0.41-beta server and client. Items marked *both loaders* come from shared code.

- [x] **Medium, 26.2:** no status message ever shows (start/stop, hand not empty, fewer than 3 free slots, no deposit container, container selected/cleared, vein key missing). `StatusMessages.java:39-53` looks up a `(Component, boolean)` player method by reflection; 26.2 has none, so it falls back to a single-`Component` method, which resolves to `Entity.setCustomName` and renames the local player instead.
  Fixed: `StatusMessages` calls a per-version `ActionBar` (`src/client-1.21`: `displayClientMessage(msg, true)`; `src/client-26`: `sendOverlayMessage(msg)`), no reflection; the platform GameTest asserts the F6 message reaches the action bar and does not rename the player.
- [ ] Low: the default F6 key conflicts with vanilla's debug options key.

- [x] Flaky platform GameTest: "Expected the vein-mining key held on every clicking tick" failed off by one tick (e.g. 234/235, 228/229) on random targets under load (1.21.1/1.21.4/26.1.2 NeoForge; seen locally three times and in CI on 2026-10-01).
  Cause: a test artefact. The observer only tracked deposits on observed ticks, so a deposit that began inside the 41-tick pause window went unseen; when it ended on the first observed tick, the chest close (`KeyMapping.setAll` on `setScreen(null)`) had released the vein key and the tick was counted. Fixed: `trackDeposits` runs on every running tick, including the pause window; reproduced by forcing that boundary on 1.21.1 NeoForge (2/2 failures before, 8/8 passes after; 3/3 on 26.2 Fabric, where the scenario's tick listener runs before the macro's).
- [ ] Flaky platform GameTest: the 26.2 Fabric "inventory key opens the inventory screen" assertion failed once (2026-10-02 01:54, pause tick 3). Not reproduced in 10 instrumented 26.2 Fabric runs with 0–260 ms frame stalls. On 26.2 Fabric the scenario's `END_CLIENT_TICK` listener runs before the macro's, so the pause starts from the previous tick's macro state; nothing traced so far releases the click (only `Gui.setScreen(non-null)` → `KeyMapping.releaseAll` clears it) or closes the screen within three ticks.
- [ ] Flaky platform GameTest under frame stalls: "Expected no right-clicks while a screen is open" failed in 2 of 29 runs with injected 0–260 ms frame stalls (26.1.2 NeoForge, 26.2 Fabric). The client caught up on pause ticks 201–203 in one frame, so `usesAtPause` was read before the integrated server had processed right-clicks sent before the screen opened. The baseline must be taken once the server has handled every click sent before the pause, not at a fixed client tick.
