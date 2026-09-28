# ActionAssist todo

## Loader parity findings (2026-09-29)

From running the release NeoForge jar on a real NeoForge 26.2.0.41-beta server and client. Items marked *both loaders* come from shared code.

- [ ] **Medium, 26.2:** no status message ever shows (start/stop, hand not empty, fewer than 3 free slots, no deposit container, container selected/cleared, vein key missing). `StatusMessages.java:39-53` looks up a `(Component, boolean)` player method by reflection; 26.2 has none, so it falls back to a single-`Component` method, which resolves to `Entity.setCustomName` and renames the local player instead.
- [ ] Low: the default F6 key conflicts with vanilla's debug options key.
