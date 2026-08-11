package io.github.brainage04.actionassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AutomationSettingsTest {
    @Test
    void defaultsMatchTheDocumentedClientConfiguration() {
        assertEquals(
                new AutomationSettings(AutomationSettings.Action.USE, 20, 8, true),
                AutomationSettings.defaults());
    }

    @Test
    void boundaryRatesAreAccepted() {
        assertEquals(
                1,
                new AutomationSettings(AutomationSettings.Action.USE, 1, 1, true)
                        .actionsPerSecond());
        AutomationSettings upperBounds =
                new AutomationSettings(AutomationSettings.Action.ATTACK, 20, 10, false);
        assertEquals(20, upperBounds.actionsPerSecond());
        assertEquals(10, upperBounds.sneakTapsPerSecond());
    }

    @Test
    void actionRateOutsideSupportedBoundsIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutomationSettings(AutomationSettings.Action.USE, 0, 8, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutomationSettings(AutomationSettings.Action.USE, 21, 8, true));
    }

    @Test
    void sneakRateOutsideSupportedBoundsIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutomationSettings(AutomationSettings.Action.USE, 20, 0, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutomationSettings(AutomationSettings.Action.USE, 20, 11, true));
    }

    @Test
    void actionIsRequiredAndParsingIsCaseInsensitive() {
        assertThrows(NullPointerException.class, () -> new AutomationSettings(null, 20, 8, true));
        assertEquals(AutomationSettings.Action.USE, AutomationSettings.Action.parse(" use "));
        assertEquals(AutomationSettings.Action.ATTACK, AutomationSettings.Action.parse("Attack"));
        assertThrows(
                IllegalArgumentException.class, () -> AutomationSettings.Action.parse("break"));
    }
}
