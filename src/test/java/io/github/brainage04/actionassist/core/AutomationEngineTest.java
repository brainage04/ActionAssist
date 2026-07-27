package io.github.brainage04.actionassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutomationEngineTest {
    @Test
    void enabledHoldModePerformsConfiguredRateAndHoldsSneak() {
        AutomationEngine engine = new AutomationEngine(
                new AutomationSettings(AutomationSettings.Action.USE, 5, 8, true)
        );
        RecordingOutput output = new RecordingOutput();

        assertTrue(engine.toggle());
        for (int tick = 0; tick < 20; tick++) {
            engine.tick(true, output);
        }

        assertEquals(5, output.actions.size());
        assertTrue(output.sneak);
        assertFalse(output.companion);
    }

    @Test
    void sneakModesFollowHoldSpamNoneCycle() {
        AutomationEngine engine = new AutomationEngine(AutomationSettings.defaults());
        RecordingOutput output = new RecordingOutput();
        engine.toggle();

        assertEquals(AutomationEngine.SneakMode.SPAM, engine.cycleSneakMode());
        for (int tick = 0; tick < 20; tick++) {
            engine.tick(true, output);
        }
        assertEquals(8, output.sneakPresses);
        assertTrue(output.sneakReleases > 0);
        assertTrue(output.companion);

        assertEquals(AutomationEngine.SneakMode.NONE, engine.cycleSneakMode());
        engine.tick(true, output);
        assertFalse(output.sneak);
        assertFalse(output.companion);

        assertEquals(AutomationEngine.SneakMode.HOLD, engine.cycleSneakMode());
    }

    @Test
    void hotbarDumpRequiresAutomationAndPausesOtherOutputs() {
        AutomationEngine engine = new AutomationEngine(AutomationSettings.defaults());
        RecordingOutput output = new RecordingOutput();

        assertFalse(engine.requestHotbarDump());
        engine.toggle();
        engine.tick(true, output);
        assertTrue(engine.requestHotbarDump());
        int actionsBeforeDump = output.actions.size();

        engine.tick(true, output);

        assertEquals(1, output.dumps);
        assertEquals(actionsBeforeDump, output.actions.size());
        assertFalse(output.sneak);
        assertFalse(output.companion);
    }

    @Test
    void unavailableGameplayAndDisableReleaseSyntheticKeys() {
        AutomationEngine engine = new AutomationEngine(AutomationSettings.defaults());
        RecordingOutput output = new RecordingOutput();
        engine.toggle();
        engine.tick(true, output);
        assertTrue(output.sneak);

        engine.tick(false, output);
        assertFalse(output.sneak);
        assertFalse(output.companion);

        assertFalse(engine.toggle());
        engine.tick(true, output);
        assertFalse(output.sneak);
        assertFalse(output.companion);
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
