package sqz.wenku8.bg.hook.view.mode;

import android.os.Build;
import android.view.View;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import sqz.wenku8.bg.MainHook;
import sqz.wenku8.bg.hook.view.mode.handler.ActivityHandler;
import sqz.wenku8.bg.hook.view.mode.handler.BettrySaverHanlder;
import sqz.wenku8.bg.hook.view.mode.handler.DarkModeHandler;
import sqz.wenku8.bg.hook.view.mode.handler.ToggleModeHandler;

public class HookLightMode {
    public static final String BUTTON_RESL = "btn_daylight";

    // ===== runtime state =====
    private static final AtomicBoolean isModeChanged = new AtomicBoolean(false);

    // Hook activity state
    private final ActivityHandler activityHandler = new ActivityHandler();

    /**
     * mark user clicked btn_daylight
     */
    private void markUserTriggeredByOnClick() {
        try {
            XposedHelpers.findAndHookMethod(View.class, "performClick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    View v = (View) param.thisObject;
                    String idName = v.getId() == View.NO_ID ? "NO_ID"
                            : v.getResources().getResourceEntryName(v.getId());
                    if (idName.equals(BUTTON_RESL)) {
                        MainHook.log(true, "User clicked btn_daylight");
                        isModeChanged.set(!isModeChanged.get());
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] sqz.wenku8.bg markUserTriggeredByOnClick: " + t);
        }
    }

    /**
     * Main hook for handle dark and light mode
     */
    public void mainHook(XC_LoadPackage.LoadPackageParam lpParam) {
        DarkModeHandler darkModeHandler = new DarkModeHandler(isModeChanged);
        BettrySaverHanlder bettrySaverHanlder = new BettrySaverHanlder(isModeChanged);
        ToggleModeHandler toggleModeHandler = new ToggleModeHandler(activityHandler);
        try {
            // ===== Get state =====
            activityHandler.hookTopActivity();
            this.markUserTriggeredByOnClick();

            // ===== Listeners =====
            bettrySaverHanlder.hookBettrySaver(darkModeHandler, () -> toggleModeHandler.triggerAutoToggle(lpParam));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                darkModeHandler.hookDarkMode(() -> toggleModeHandler.triggerAutoToggle(lpParam));
            }
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] sqz.wenku8.bg mainHook: " + t);
        }
    }
}
