package sqz.wenku8.bg.hook.view.mode.handler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import sqz.wenku8.bg.MainHook;

public class ToggleModeHandler {
    public static final List<String> THEME_CLASS_CANDIDATES = Arrays.asList(
            "x2.c", "y2.c"
    );

    private final ActivityHandler activityHandler;

    public ToggleModeHandler(final ActivityHandler activityHandler) {
        this.activityHandler = activityHandler;
    }

    private static volatile Class<?> sThemeClass = null;
    private static volatile Method sGetInDayMode = null;
    private static volatile Field sControllingBooleanField = null;

    private static void log(String msg) {
        MainHook.log(true, "[HookLightMode] " + msg);
    }

    private void ensureThemeClassAndGetter(XC_LoadPackage.LoadPackageParam lpp) throws ClassNotFoundException {
        if (sThemeClass != null && sGetInDayMode != null) return;

        ClassLoader cl = (activityHandler.sLastResumedGetter() != null)
                ? activityHandler.sLastResumedGetter().getClassLoader()
                : lpp.classLoader;
        Throwable lastErr = null;
        for (String cn : THEME_CLASS_CANDIDATES) {
            try {
                Class<?> c = XposedHelpers.findClass(cn, cl);
                Method getter = c.getDeclaredMethod("getInDayMode");
                getter.setAccessible(true);
                if (getter.getReturnType() != boolean.class) continue;

                sThemeClass = c;
                sGetInDayMode = getter;
                log("Theme class found: " + cn + " with getInDayMode()");
                return;
            } catch (Throwable t) {
                lastErr = t;
            }
        }

        throw new ClassNotFoundException("No theme class/getter found from candidates. last=" + lastErr);
    }

    /**
     * @noinspection DataFlowIssue
     */
    private boolean getInDayMode() throws Exception {
        if (sGetInDayMode == null) throw new IllegalStateException("getInDayMode not initialized");
        return (boolean) sGetInDayMode.invoke(null);
    }

    private Field ensureControllingBooleanField() {
        if (sControllingBooleanField != null) return sControllingBooleanField;
        if (sThemeClass == null || sGetInDayMode == null) return null;

        try {
            boolean orig = getInDayMode();
            Field[] fs = sThemeClass.getDeclaredFields();

            for (Field f : fs) {
                int m = f.getModifiers();
                if (!Modifier.isStatic(m)) continue;
                if (f.getType() != boolean.class) continue;

                f.setAccessible(true);
                boolean oldVal = f.getBoolean(null);

                // try flip
                f.setBoolean(null, !oldVal);
                boolean now = getInDayMode();

                if (now != orig) {
                    // on target, restore; the real flip will do via triggerAutoToggle
                    f.setBoolean(null, oldVal);
                    sControllingBooleanField = f;
                    log("Controlling boolean field located: " + f.getName());
                    return f;
                }

                // not target, revert
                f.setBoolean(null, oldVal);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] HookLightMode.ensureControllingBooleanField: " + t);
        }
        return null;
    }

    public void triggerAutoToggle(XC_LoadPackage.LoadPackageParam lpp) {
        try {
            ensureThemeClassAndGetter(lpp);
            boolean orig = getInDayMode();
            log("getInDayMode() = " + orig + " → flipping");

            Field ensureControllingBoolean = ensureControllingBooleanField();
            if (ensureControllingBoolean == null) {
                log("No controlling boolean field found; abort.");
                return;
            }

            boolean cur = ensureControllingBoolean.getBoolean(null);
            ensureControllingBoolean.setBoolean(null, !cur);
            boolean now = getInDayMode();

            if (now != orig) {
                log("Dark/Light toggled successfully. Now getInDayMode() = " + now);
            } else {
                // no effect, rollback
                ensureControllingBoolean.setBoolean(null, cur);
                log("Flip had no effect on getInDayMode(); reverted.");
            }
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] HookLightMode.triggerAutoToggle: " + t);
        }
    }
}
