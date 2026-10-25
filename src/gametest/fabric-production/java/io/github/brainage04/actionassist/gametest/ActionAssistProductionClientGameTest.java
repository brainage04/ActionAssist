package io.github.brainage04.actionassist.gametest;

import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Runs the cross-target {@link PlatformScenario} under Fabric's client GameTest harness, so the same
 * scenario also covers the packaged jar in Loom's production client.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ActionAssistProductionClientGameTest implements FabricClientGameTest {
    private static final int SCENARIO_TIMEOUT_TICKS = 20 * 60 * 8;
    /** Recording captions, shown when the scenario enters each phase: title, then subtitle. */
    private static final Map<String, String[]> CAPTIONS = Map.of(
            "SELECT_CONTAINER", new String[] {"F6 selects the deposit chest",
                    "F6 on dirt selects nothing; on the chest it selects, clears and reselects it."},
            "RUN_PEBBLE", new String[] {"Pebble macro (G5)",
                    "Holds sneak and right-clicks dirt, clears the hand, compacts pebbles and deposits into the chest."},
            "PLANT_CROPS", new String[] {"Crop setup",
                    "The scenario plants wheat on lit farmland for the crop macro."},
            "RUN_CROP", new String[] {"Crop macro (G4)",
                    "Right-clicks wheat, taps sneak for crop growth, holds the vein-mining key and deposits the harvest."},
            "PREPARE_SHUTDOWN", new String[] {"Input release",
                    "Stopping a macro, or the client shutting down, releases sneak and the vein-mining key."});

    @Override
    public void runTest(ClientGameTestContext context) {
        // The recorder's capture spans the world: it starts before the world opens and stops after it closes.
        ClientGameTestRecorder.startRecording(context);
        try {
            try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
                context.waitTicks(40);
                context.runOnClient(client -> PlatformScenario.startEmbedded("26.3-fabric-client-gametest"));
                awaitScenarioWithCaptions(context);
                if (PlatformScenario.failure() != null) {
                    throw new AssertionError("Platform scenario failed", PlatformScenario.failure());
                }
            }
        } finally {
            ClientGameTestRecorder.stopRecording(context);
        }
    }

    private static void awaitScenarioWithCaptions(ClientGameTestContext context) {
        String previous = null;
        for (int tick = 0; tick < SCENARIO_TIMEOUT_TICKS; tick++) {
            if (PlatformScenario.finished()) {
                return;
            }
            String phase = context.computeOnClient(client -> PlatformScenario.phaseName());
            String[] caption = CAPTIONS.get(phase);
            if (caption != null && !phase.equals(previous)) {
                ClientGameTestRecorder.showStep(context, "actionassist." + phase.toLowerCase(), caption[0], caption[1]);
            }
            previous = phase;
            context.waitTick();
        }
        throw new AssertionError("Platform scenario did not finish within " + SCENARIO_TIMEOUT_TICKS + " ticks");
    }
}
