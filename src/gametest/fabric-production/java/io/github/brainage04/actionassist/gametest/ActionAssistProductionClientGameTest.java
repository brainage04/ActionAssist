package io.github.brainage04.actionassist.gametest;

import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
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

    @Override
    public void runTest(ClientGameTestContext context) {
        // The recorder's capture spans the world: it starts before the world opens and stops after it closes.
        ClientGameTestRecorder.startRecording(context);
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(40);
            context.runOnClient(client -> PlatformScenario.startEmbedded("26.3-fabric-client-gametest"));
            context.waitFor(client -> PlatformScenario.finished(), SCENARIO_TIMEOUT_TICKS);
            if (PlatformScenario.failure() != null) {
                throw new AssertionError("Platform scenario failed", PlatformScenario.failure());
            }
        }
        ClientGameTestRecorder.stopRecording(context);
    }
}
