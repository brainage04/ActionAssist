package io.github.brainage04.actionassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AutomationEngineTest {
    @ParameterizedTest
    @MethodSource("actionRates")
    void everySupportedActionRateIsExact(int actionsPerSecond) {
        AutomationEngine engine =
                new AutomationEngine(
                        new AutomationSettings(
                                AutomationSettings.Action.USE, actionsPerSecond, 8, true));
        RecordingOutput output = new RecordingOutput();

        assertTrue(engine.toggle());
        tick(engine, output, 20);

        assertEquals(actionsPerSecond, output.actions.size());
        assertTrue(
                output.actions.stream()
                        .allMatch(action -> action == AutomationSettings.Action.USE));
        assertTrue(output.sneak);
        assertFalse(output.companion);
    }

    @Test
    void attackConfigurationIsForwardedToTheOutput() {
        AutomationEngine engine =
                new AutomationEngine(
                        new AutomationSettings(AutomationSettings.Action.ATTACK, 20, 8, true));
        RecordingOutput output = new RecordingOutput();

        engine.toggle();
        tick(engine, output, 3);

        assertEquals(
                List.of(
                        AutomationSettings.Action.ATTACK,
                        AutomationSettings.Action.ATTACK,
                        AutomationSettings.Action.ATTACK),
                output.actions);
    }

    @ParameterizedTest
    @MethodSource("sneakRates")
    void everySupportedSneakRateIsExact(int sneakTapsPerSecond) {
        AutomationEngine engine =
                new AutomationEngine(
                        new AutomationSettings(
                                AutomationSettings.Action.USE, 1, sneakTapsPerSecond, true));
        RecordingOutput output = new RecordingOutput();
        assertEquals(AutomationEngine.SneakMode.SPAM, engine.cycleSneakMode());

        engine.toggle();
        tick(engine, output, 20);

        assertEquals(sneakTapsPerSecond, output.sneakPresses);
        assertTrue(output.sneakReleases > 0);
        assertTrue(output.companion);
    }

    @Test
    void sneakModesFollowHoldSpamNoneCycle() {
        AutomationEngine engine = new AutomationEngine(AutomationSettings.defaults());
        RecordingOutput output = new RecordingOutput();
        engine.toggle();

        engine.tick(true, output);
        assertTrue(output.sneak);
        assertFalse(output.companion);

        assertEquals(AutomationEngine.SneakMode.SPAM, engine.cycleSneakMode());
        engine.tick(true, output);
        assertTrue(output.companion);

        assertEquals(AutomationEngine.SneakMode.NONE, engine.cycleSneakMode());
        engine.tick(true, output);
        assertFalse(output.sneak);
        assertFalse(output.companion);

        assertEquals(AutomationEngine.SneakMode.HOLD, engine.cycleSneakMode());
    }

    @Test
    void hotbarDumpRequiresAutomationRejectsDuplicatesAndPausesOutputs() {
        AutomationEngine engine = new AutomationEngine(AutomationSettings.defaults());
        RecordingOutput output = new RecordingOutput();

        assertFalse(engine.requestHotbarDump());
        engine.toggle();
        assertTrue(engine.requestHotbarDump());
        assertFalse(engine.requestHotbarDump());

        engine.tick(true, output);

        assertEquals(1, output.dumps);
        assertTrue(output.actions.isEmpty());
        assertFalse(output.sneak);
        assertFalse(output.companion);

        engine.tick(true, output);
        assertEquals(List.of(AutomationSettings.Action.USE), output.actions);
        assertTrue(engine.requestHotbarDump());
    }

    @Test
    void unavailableGameplayDisableAndStopReleaseSyntheticKeys() {
        AutomationEngine engine = new AutomationEngine(AutomationSettings.defaults());
        RecordingOutput output = new RecordingOutput();
        engine.toggle();
        engine.tick(true, output);
        assertTrue(output.sneak);

        engine.tick(false, output);
        assertTrue(engine.isEnabled());
        assertFalse(output.sneak);
        assertFalse(output.companion);

        engine.tick(true, output);
        assertTrue(output.sneak);

        assertTrue(engine.stop());
        assertFalse(engine.stop());
        engine.tick(true, output);
        assertFalse(engine.isEnabled());
        assertFalse(output.sneak);
        assertFalse(output.companion);
    }

    @Test
    void reenableResetsSchedulesAndPerformsImmediately() {
        AutomationEngine engine =
                new AutomationEngine(
                        new AutomationSettings(AutomationSettings.Action.USE, 1, 1, true));
        RecordingOutput output = new RecordingOutput();

        engine.toggle();
        engine.tick(true, output);
        engine.toggle();
        engine.tick(true, output);
        engine.toggle();
        engine.tick(true, output);

        assertEquals(2, output.actions.size());
        assertEquals(2, output.sneakPresses);
    }

    @Test
    void nullCollaboratorsAreRejected() {
        assertThrows(NullPointerException.class, () -> new AutomationEngine(null));

        AutomationEngine engine = new AutomationEngine(AutomationSettings.defaults());
        assertThrows(NullPointerException.class, () -> engine.tick(true, null));
    }

    private static IntStream actionRates() {
        return IntStream.rangeClosed(1, AutomationSettings.TICKS_PER_SECOND);
    }

    private static IntStream sneakRates() {
        return IntStream.rangeClosed(1, AutomationSettings.TICKS_PER_SECOND / 2);
    }

    private static void tick(AutomationEngine engine, RecordingOutput output, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            engine.tick(true, output);
        }
    }

    private static final class RecordingOutput implements AutomationEngine.Output {
        private final List<AutomationSettings.Action> actions = new ArrayList<>();
        private boolean sneak;
        private boolean companion;
        private int sneakPresses;
        private int sneakReleases;
        private int dumps;

        @Override
        public void setSneak(boolean down) {
            if (down && !sneak) {
                sneakPresses++;
            }
            if (!down && sneak) {
                sneakReleases++;
            }
            sneak = down;
        }

        @Override
        public void setCompanionKey(boolean down) {
            companion = down;
        }

        @Override
        public void perform(AutomationSettings.Action action) {
            actions.add(action);
        }

        @Override
        public void dumpHotbar() {
            dumps++;
        }
    }
}
