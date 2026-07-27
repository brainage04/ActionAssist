package io.github.brainage04.actionassist.core;

import java.util.Locale;
import java.util.Objects;

public record AutomationSettings(
        Action action,
        int actionsPerSecond,
        int sneakTapsPerSecond,
        boolean statusMessages
) {
    public static final int TICKS_PER_SECOND = 20;

    public AutomationSettings {
        Objects.requireNonNull(action, "action");
        if (actionsPerSecond < 1 || actionsPerSecond > TICKS_PER_SECOND) {
            throw new IllegalArgumentException("actionsPerSecond must be between 1 and 20");
        }
        if (sneakTapsPerSecond < 1 || sneakTapsPerSecond > TICKS_PER_SECOND / 2) {
            throw new IllegalArgumentException("sneakTapsPerSecond must be between 1 and 10");
        }
    }

    public static AutomationSettings defaults() {
        return new AutomationSettings(Action.USE, 20, 8, true);
    }

    public enum Action {
        USE,
        ATTACK;

        public static Action parse(String value) {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
    }
}
