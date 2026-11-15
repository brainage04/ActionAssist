package io.github.brainage04.actionassist.core;

import java.util.ArrayList;
import java.util.List;

public final class StrictGateProbe {
    public static int probe() {
        List raw = new ArrayList();
        raw.add("x");
        return raw.size();
    }
}
