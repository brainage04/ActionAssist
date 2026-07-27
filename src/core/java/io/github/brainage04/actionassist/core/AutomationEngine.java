package io.github.brainage04.actionassist.core;

import java.util.Objects;

public final class AutomationEngine {
    private final AutomationSettings settings;

    private boolean enabled;
    private boolean dumpRequested;
    private boolean firstAction;
    private boolean firstSneakTap;
    private boolean sneakWasDown;
    private int actionAccumulator;
    private int sneakAccumulator;
    private SneakMode sneakMode = SneakMode.HOLD;

    public AutomationEngine(AutomationSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public boolean toggle() {
        enabled = !enabled;
        resetSchedule();
        if (!enabled) {
            dumpRequested = false;
        }
        return enabled;
    }

    public boolean stop() {
        if (!enabled) {
            return false;
        }
        enabled = false;
        dumpRequested = false;
        resetSchedule();
        return true;
    }

    public SneakMode cycleSneakMode() {
        sneakMode = sneakMode.next();
        firstSneakTap = true;
        sneakAccumulator = 0;
        sneakWasDown = false;
        return sneakMode;
    }

    public boolean requestHotbarDump() {
        if (!enabled || dumpRequested) {
            return false;
        }
        dumpRequested = true;
        return true;
    }

    public void tick(boolean gameplayAvailable, Output output) {
        Objects.requireNonNull(output, "output");
        if (!enabled || !gameplayAvailable) {
            output.setSneak(false);
            output.setCompanionKey(false);
            sneakWasDown = false;
            return;
        }

        if (dumpRequested) {
            output.setSneak(false);
            output.setCompanionKey(false);
            sneakWasDown = false;
            dumpRequested = false;
            output.dumpHotbar();
            return;
        }

        updateSneak(output);
        updateAction(output);
    }

    private void updateAction(Output output) {
        if (firstAction) {
            firstAction = false;
            output.perform(settings.action());
            return;
        }

        actionAccumulator += settings.actionsPerSecond();
        if (actionAccumulator >= AutomationSettings.TICKS_PER_SECOND) {
            actionAccumulator -= AutomationSettings.TICKS_PER_SECOND;
            output.perform(settings.action());
        }
    }

    private void updateSneak(Output output) {
        switch (sneakMode) {
            case HOLD -> {
                output.setSneak(true);
                output.setCompanionKey(false);
                sneakWasDown = true;
            }
            case NONE -> {
                output.setSneak(false);
                output.setCompanionKey(false);
                sneakWasDown = false;
            }
            case SPAM -> {
                output.setCompanionKey(true);
                boolean tapDue;
                if (firstSneakTap) {
                    firstSneakTap = false;
                    tapDue = true;
                } else {
                    sneakAccumulator += settings.sneakTapsPerSecond();
                    tapDue = sneakAccumulator >= AutomationSettings.TICKS_PER_SECOND;
                }
                boolean down = !sneakWasDown && tapDue;
                if (down && sneakAccumulator >= AutomationSettings.TICKS_PER_SECOND) {
                    sneakAccumulator -= AutomationSettings.TICKS_PER_SECOND;
                }
                output.setSneak(down);
                sneakWasDown = down;
            }
        }
    }

    private void resetSchedule() {
        firstAction = enabled;
        firstSneakTap = enabled;
        sneakWasDown = false;
        actionAccumulator = 0;
        sneakAccumulator = 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SneakMode sneakMode() {
        return sneakMode;
    }

    public AutomationSettings settings() {
        return settings;
    }

    public enum SneakMode {
        HOLD,
        SPAM,
        NONE;

        private static final SneakMode[] VALUES = values();

        public SneakMode next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }

    public interface Output {
        void setSneak(boolean down);

        void setCompanionKey(boolean down);

        void perform(AutomationSettings.Action action);

        void dumpHotbar();
    }
}
