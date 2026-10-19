# Test review (2026-10-19)

## Frozen rubric

- Keep: calls production behavior and checks a player-visible outcome, failure boundary, or necessary platform/packaging boundary.
- Redundant: every relevant input, boundary and assertion is already covered by a named retained test in the same execution environment.
- Tautological: checks a copied implementation, a mock echo, registration wiring alone, or a constant without a behavior boundary.
- An output recorder is not an implementation copy when it records the actual engine's input actions. Unit and client tests are not interchangeable: unit tests isolate error boundaries, while client tests exercise Minecraft menus, input and networking.

Classification was frozen before deciding removals. No test was flagged redundant or tautological, so none was removed. There are no dedicated-server GameTests: this is a client-only mod.

## Unit tests

All methods below are **keep**.

`MacroEngineTest`:

- `pebbleMacroHoldsSneakAndRightClicksEveryTick`: sustained pebble inputs and click cadence.
- `cropMacroTapsSneakEveryOtherTickHoldsVeinKeyAndRightClicksEveryTick`: crop sneak cadence and companion input.
- `togglingTheRunningMacroStopsAndReleasesInputs`: stop releases inputs and prevents later uses.
- `togglingTheOtherMacroSwitchesWithoutAStopMessage`: switching and feedback differ from stopping.
- `pebbleMacroRefusesToStartWithAnOccupiedHand`: start rejection and no uses.
- `macrosRefuseToStartWithFewerThanThreeFreeStorageSlots`: start-space boundary.
- `anOpenScreenPausesAndReleasesInputsUntilItCloses`: pause and resume.
- `disconnectingStopsSilently`: connection-loss stop without misleading feedback.
- `pebbleMacroMovesUnreservedHotbarItemsOutOfTheWayOfTheHand`: reserved-slot exclusion and settle delay.
- `compactionSplitsEachEligibleStackAcrossTheCraftingGridAndCraftsIt`: emitted menu protocol, eligible stacks and reserved exclusion.
- `anItemThatDoesNotCompactIsNotRetried`: failed-recipe suppression within a run.
- `depositOpensTheSelectedContainerMovesUnreservedItemsAndResumes`: complete deposit lifecycle and reserved exclusion.
- `aFullDepositContainerStopsTheMacro`: rejected-transfer stop.
- `itemsPickedUpDuringADepositDoNotCountAsAFullContainer`: reduced stack counts still represent progress even with no new free slots.
- `aSuccessfulDepositRefilledWithIdenticalStacksDoesNotStopTheMacro`: transient progress followed by identical stacks; not covered by the previous test, which leaves final counts lower.
- `aPartiallyAcceptedDepositKeepsTheMacroRunning`: one accepted stack is enough.
- `depositWithoutASelectedContainerStopsTheMacro`: missing-target feedback.
- `aContainerThatNeverOpensStopsTheMacro`: bounded open timeout without closing another menu.
- `closingTheDepositContainerEarlyStopsTheMacro`: interruption before transfer.

`SettingsFileTest`:

- `firstLaunchWritesLoadableDefaults`: real filesystem creation and reload.
- `validPropertiesAreTrimmedAndBlankListEntriesSkipped`: custom values and normalisation.
- `emptyCompactItemsDisablesCompaction`: intentionally empty list, unlike omitted/default list.
- `propertiesFromEarlierVersionsAreIgnoredAndMissingOnesDefault`: unknown properties and missing defaults.
- `anyInvalidPropertyFallsBackToAllDefaults` (six cases): blank key, malformed item identifiers and invalid boolean each reject the whole file with a warning.
- `unwritableConfigPathWarnsAndUsesDefaults`: filesystem error fallback.

## Client scenarios

- **Keep** `PlatformScenario` on all sixteen version/loader targets: real menus, compaction, conservation, reserved slots, screen pause, inputs and deposits. The server-side fixture supplies the modpack's mechanics; it does not replace Action Assist's implementation.
- **Keep** `ActionAssistProductionClientGameTest.runTest` in development and packaged 26.2 Fabric: uses the same scenario, but the packaged run checks transformed/shipped classes and dependency isolation that a standalone development launch cannot check.
- **Keep** `ModpackCompatibilityClientGameTest` on 1.21.1 NeoForge: real Ex Deorum recipes, KubeJS pebble script, Squat Grow, Mystical Agriculture and FTB Ultimine. The vanilla fixture cannot establish these integrations.
- `PlatformGameTestFabric` and `PlatformGameTestNeoForge` are scenario adapters, not extra assertion-only tests.

## Uncovered behavior before additions

- Explicit shutdown while running or depositing; disconnect/rejoin cleanup and target reset on both loaders.
- Replacing a deposit menu during verification, rather than only closing before transfer.
- Carried items and a non-empty crafting grid during a run.
- Failed compaction becoming eligible after a restart.
- Container selection clear/invalid feedback and disabled status messages in a real client.
- Dimension changes while a macro runs; physical keys held during synthetic release.

There are no mod commands, gamerules or custom rendering. Action-bar delivery is already checked by the platform scenario. The following coverage work focuses on destructive inventory/menu boundaries and lifecycle; rendering fonts and physical-key interaction remain manual checks.

## Added coverage

All additions are **keep** under the frozen rubric:

- `shutdownStopsAnActiveMacroSilentlyAndIsIdempotent`: running shutdown, repeated shutdown and no later uses.
- `shutdownDuringTransferClosesTheOwnedContainerOnlyOnce`: owned-menu cleanup without transfer after shutdown.
- `interruptionDuringVerificationStopsWithoutResumingUses`: interruption after transfer, a different phase from the original early-close test.
- `carriedItemsUseTheFirstUnreservedStorageSlotBeforeMoreUses`: cursor recovery and reserved-slot exclusion.
- `craftingGridItemsAreRecoveredBeforeMoreUses`: grid recovery before gameplay resumes.
- `failedCompactionIsRetriedAfterRestartingTheMacro`: restart clears the failed-recipe cache.
- The shared client scenario now clears and reselects the chest through F6 and invokes idempotent runtime shutdown with a crop macro running, checking both synthetic keys are released. Every supported loader/version runs these checks.
