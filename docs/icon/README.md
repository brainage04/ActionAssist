# ActionAssist icon

## What this is

`icon.png` — the ActionAssist mod icon, 1024 x 1024 PNG, SHA-256
`a90ea8507f40777f62de16396ac98b7e9323eac150012fefea7ff1b789a82615`.

Copied byte-identically from `.local-icon-variants/provenance/from-round3/blender-y/actionassist-3x.png`
(SHA-256 verified at the source, and again on the copy). The hash in the copy matches the
`png_sha256` recorded in `provenance/actionassist-3x-metadata.json`.

## How it was made

Blender render, not a screenshot.

* Blender 5.1.1, headless: `blender --background -noaudio --threads 2 --python-exit-code 1 --python ...`,
  Cycles, CPU device (no GPU), 64 samples, 1024 x 1024, render duration 42.056 s.
  No display server, no audio sink, no Minecraft shader pack — Cycles CPU path tracing.
* Scene: `provenance/base-actionassist-2.blend`, the approved ActionAssist packed scene.
  That base scene descends from a **real in-game capture**: 26 Ex Deorum pebble item entities
  observed in-game at t = 5 s, radially compacted by 0.72 into a recorded 52-instance field
  (`base-field.json`, `upstream-provenance.json`).
* This particular icon is **not** a longer in-game run. `derive-field.py` extends the recorded
  52-instance field deterministically to 156 instances (126 resting / 30 airborne) by rotating
  copies of every instance about world Y by the golden angle (137.50776405003785 deg per added
  layer), keeping each instance's source radius, entity/mesh Y, resting/airborne state and
  recorded Y-only spin. The added layers are illustrative copies, not newly observed entities;
  natural overlaps and player occlusion are retained. See `derivation-method.json`.
* Camera and framing are the approved base scene's, unchanged: yaw 225 deg, pitch 45 deg,
  roll 0, west-facing player on the unchanged 25-block (5 x 5) dirt platform. `recreate.py` sets only
  engine, device, samples, threads and resolution (Cycles/CPU/64/2/1024x1024, matching the base
  metadata); Cycles seed, adaptive sampling and denoising settings are left as they are in the base scene.
* Imagery: the real vanilla dirt block texture, the supplied player skin
  (SHA-256 `e9ebbeece495d9c96040e235dc865fdb1a530cf6a2243a6c8fcec22e72f03e3f`) and Ex Deorum 3.10
  pebble item models + textures — all snapshotted byte-identically under `provenance/assets/`
  (hashes in `source-provenance.json` / `upstream-provenance.json`). The vanilla
  `item/generated` display data in `assets/generated.json` and the client evidence in
  `orientation-source.json` were read from the local official client jar for this mod's
  Minecraft version, `~/.gradle/caches/fabric-loom/1.21.1/neoforge/21.1.244/minecraft-merged-mojang.jar`
  (SHA-256 `8fedaea0d093b45092dbfb2e12f0d9aff4f67016ba57a6582e6eff52911c1aa9`). No jar or
  Minecraft runtime is shipped here; the small assets the render actually reads are.

## Provenance files

`provenance/` holds the author recipe and the recorded verification:

* `actionassist-3x.py` — entry point (`recreate.render(3)`); `recreate.py` — builds and measures
  the saved `.blend`; `derive-field.py` — the deterministic field extension;
  `run-blender.py` — the recorded CPU batch launcher.
* `actionassist-3x.blend` (packed scene), `actionassist-3x-metadata.json`,
  `actionassist-3x-verification.json`, `actionassist-3x-run.json`, `evidence/initial-actionassist-3x-run.json`.
* Inputs: `base-actionassist-2.blend`, `base-actionassist-2-metadata.json`, `base-field.json`,
  `orientation-source.json`, `pebbles-3x.json` (the 156-instance dataset), `assets/`.
* Chain of provenance: `source-provenance.json`, `upstream-provenance.json`,
  `derivation-method.json`, `manifest.json`, `png-verification.json`, `render-report.json`,
  `run-summary.json`, `visual-review.json`, `cleanup-report.json`, `verify-pngs.py`.
* `CURATION.json` — exactly what was copied, which JSON entries were dropped and which round-3
  files were deliberately left behind.

## How to regenerate

From `<repo>/docs/icon/provenance`:

```sh
python3 derive-field.py     # rewrites pebbles-3x.json + derivation-method.json (deterministic)
nix shell nixpkgs#blender --command blender --background -noaudio --threads 2 \
  --python-exit-code 1 --python actionassist-3x.py
python3 verify-pngs.py      # Pillow decode + geometry/dataset assertions
```

The recorded launcher was `python3 run-blender.py 3`, which pins the process to two CPUs,
empties `DISPLAY`/`WAYLAND_DISPLAY`/CUDA/HIP/ROCR, writes `evidence/actionassist-3x.log` and
`actionassist-3x-run.json`, and expects the pre-existing systemd user unit
`render-blender.service` (CPUWeight 20, quota <= 3 cores). Re-running the render overwrites
`actionassist-3x.png` in place.

## Notes

* The retired "2x" density candidate (`actionassist-2x.*`, `pebbles-2x.json`) is deliberately not
  copied; only the selected 3x icon and its inputs are here.
* The previous-round base render `base-actionassist-2.png` was deliberately not copied (only the
  current icon ships). Its SHA-256, `33ce7ccbb38945a364df63ab866a3060d62a2212d9bbc4df4b23046410650c3b`,
  is recorded in `source-provenance.json`.
* Run logs (`evidence/*.log`) were excluded per the delivery contract; the machine-readable run
  records are kept.
* Nothing else in the mod repository was modified and nothing was committed.

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.
