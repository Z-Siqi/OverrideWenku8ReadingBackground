package sqz.wenku8.bg.hook.view.mode.handler;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import sqz.wenku8.bg.MainHook;

public class DarkModeHandler {
    private final AtomicBoolean isModeChanged;

    public DarkModeHandler(final AtomicBoolean isModeChanged) {
        this.isModeChanged = isModeChanged;
    }

    private static AtomicBoolean inDarkMode;

    public AtomicBoolean getInDarkMode() {
        return inDarkMode;
    }

    public interface Callback {
        void onChanged();
    }

    private void initCurrentState(Context ctx, Callback callback) {
        int nightModeFlagsInit = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkInit = nightModeFlagsInit == Configuration.UI_MODE_NIGHT_YES;
        if (isDarkInit && inDarkMode == null) {
            callback.onChanged();
            isModeChanged.set(true);
        }
        inDarkMode = new AtomicBoolean(false);
        inDarkMode.set(isDarkInit);
        MainHook.log(false, "Initial dark mode = " + isDarkInit);
    }

    /**
     * dark mode change listener
     */
    public void hookDarkMode(Callback callback) {
        try {
            // Register hook: get dark mode state
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Application app = (Application) param.thisObject;
                    final Context ctx = app.getApplicationContext();
                    // Initial current states
                    initCurrentState(ctx, callback);
                }
            });
            // Register hook: listen onResume for process dark mode change
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    Context context = activity.getApplicationContext();
                    int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    boolean isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
                    if (inDarkMode == null) { // if failed to set in listener above
                        inDarkMode = new AtomicBoolean(isDarkMode);
                    }
                    boolean prev = inDarkMode.getAndSet(isDarkMode);
                    if (isDarkMode && !prev) {
                        if (!isModeChanged.get()) {
                            callback.onChanged();
                            isModeChanged.set(true);
                        }
                        MainHook.log(true, "Dark mode ENTER");
                    } else if (!isDarkMode && prev) {
                        if (isModeChanged.get()) {
                            callback.onChanged();
                            isModeChanged.set(false);
                        }
                        MainHook.log(true, "Dark mode EXIT");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] sqz.wenku8.bg hookDarkMode: " + t);
        }
    }
}
