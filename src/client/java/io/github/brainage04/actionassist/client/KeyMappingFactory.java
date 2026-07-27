package io.github.brainage04.actionassist.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyMappingFactory {
    private static final String LEGACY_CATEGORY = "key.categories.actionassist";
    private static final String CATEGORY_NAMESPACE = "actionassist";
    private static final String CATEGORY_PATH = "keys";
    private static Object modernCategory;

    private KeyMappingFactory() {
    }

    public static ActionAssistKeys create() {
        return new ActionAssistKeys(
                create("key.actionassist.toggle", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5),
                create("key.actionassist.cycle_sneak", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4),
                create("key.actionassist.dump_hotbar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6),
                create("key.actionassist.companion_hold", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT)
        );
    }

    private static KeyMapping create(String translationKey, InputConstants.Type inputType, int defaultKey) {
        try {
            for (Constructor<?> constructor : KeyMapping.class.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length != 4
                        || parameters[0] != String.class
                        || parameters[1] != InputConstants.Type.class
                        || parameters[2] != int.class) {
                    continue;
                }

                Object category;
                if (parameters[3] == String.class) {
                    category = LEGACY_CATEGORY;
                } else {
                    if (modernCategory == null) {
                        modernCategory = createCategory(parameters[3]);
                    }
                    category = modernCategory;
                }
                return (KeyMapping) constructor.newInstance(
                        translationKey,
                        inputType,
                        defaultKey,
                        category
                );
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create Action Assist key mapping", exception);
        }
        throw new IllegalStateException("Unsupported Minecraft KeyMapping constructor");
    }

    private static Object createCategory(Class<?> categoryType) throws ReflectiveOperationException {
        Constructor<?> categoryConstructor = categoryType.getConstructors()[0];
        Class<?> identifierType = categoryConstructor.getParameterTypes()[0];
        Object identifier = createIdentifier(identifierType);

        for (Method method : categoryType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == categoryType
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == identifierType) {
                return method.invoke(null, identifier);
            }
        }
        return categoryConstructor.newInstance(identifier);
    }

    private static Object createIdentifier(Class<?> identifierType) throws ReflectiveOperationException {
        for (Method method : identifierType.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == identifierType
                    && parameters.length == 2
                    && parameters[0] == String.class
                    && parameters[1] == String.class) {
                Object identifier = method.invoke(null, CATEGORY_NAMESPACE, CATEGORY_PATH);
                if (identifier != null) {
                    return identifier;
                }
            }
        }
        throw new IllegalStateException("Unsupported Minecraft identifier API");
    }
}
