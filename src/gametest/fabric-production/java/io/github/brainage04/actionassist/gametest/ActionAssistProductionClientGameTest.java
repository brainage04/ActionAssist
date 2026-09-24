package io.github.brainage04.actionassist.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Runs the cross-target {@link PlatformScenario} under Fabric's client GameTest harness, so the same
 * scenario also covers the packaged jar in Loom's production client.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ActionAssistProductionClientGameTest implements FabricClientGameTest {
    private static final String RECORDING_START_ENV = "CLIENT_GAMETEST_RECORDING_START_SIGNAL";
    private static final String RECORDING_READY_ENV = "CLIENT_GAMETEST_RECORDING_READY_SIGNAL";
    private static final int SCENARIO_TIMEOUT_TICKS = 20 * 60 * 8;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(40);
            startRecording(context);
            context.runOnClient(client -> PlatformScenario.startEmbedded("26.2-fabric-client-gametest"));
            context.waitFor(client -> PlatformScenario.finished(), SCENARIO_TIMEOUT_TICKS);
            if (PlatformScenario.failure() != null) {
                throw new AssertionError("Platform scenario failed", PlatformScenario.failure());
            }
        }
    }

    private static void startRecording(ClientGameTestContext context) {
        Path startSignal = environmentPath(RECORDING_START_ENV);
        if (startSignal == null) {
            return;
        }
        try {
            if (startSignal.getParent() != null) {
                Files.createDirectories(startSignal.getParent());
            }
            Files.writeString(startSignal, Long.toString(System.currentTimeMillis()));
        } catch (IOException exception) {
            throw new AssertionError("Could not signal the client GameTest recorder", exception);
        }
        Path readySignal = environmentPath(RECORDING_READY_ENV);
        if (readySignal == null) {
            return;
        }
        for (int tick = 0; tick < 400; tick++) {
            if (Files.exists(readySignal)) {
                return;
            }
            context.waitTick();
        }
        throw new AssertionError("Recorder did not acknowledge the client within 400 ticks");
    }

    private static Path environmentPath(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }
}
