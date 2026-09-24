package io.github.brainage04.actionassist.fabric;

import io.github.brainage04.actionassist.client.ActionAssistKeys;
import io.github.brainage04.actionassist.client.ClientRuntime;
import io.github.brainage04.actionassist.client.KeyMappingFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

public final class ActionAssistFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ActionAssistKeys keys = KeyMappingFactory.create();
        register(keys.pebbleMacro());
        register(keys.cropMacro());
        register(keys.selectContainer());

        ClientRuntime.initialize(FabricLoader.getInstance().getConfigDir(), keys);
        ClientTickEvents.END_CLIENT_TICK.register(ClientRuntime::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientRuntime.shutdown());
    }

    private static void register(KeyMapping mapping) {
        String[] helperNames = {
            "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper",
            "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper"
        };
        for (String helperName : helperNames) {
            try {
                Class<?> helper = Class.forName(helperName);
                for (Method method : helper.getMethods()) {
                    if (Modifier.isStatic(method.getModifiers())
                            && method.getName().startsWith("register")
                            && KeyMapping.class.isAssignableFrom(method.getReturnType())
                            && method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == KeyMapping.class) {
                        method.invoke(null, mapping);
                        return;
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Try the API name used by the other mapping generation.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "Could not register Action Assist key mapping", exception);
            }
        }
        throw new IllegalStateException("Fabric key mapping API is unavailable");
    }
}
