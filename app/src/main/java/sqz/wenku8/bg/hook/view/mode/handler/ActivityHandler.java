package sqz.wenku8.bg.hook.view.mode.handler;

import android.annotation.SuppressLint;
import android.app.Activity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import sqz.wenku8.bg.MainHook;

public class ActivityHandler {

    @SuppressLint("StaticFieldLeak")
    private static volatile Activity sLastResumed = null;

    public Activity sLastResumedGetter() {
        return ActivityHandler.sLastResumed;
    }

    /**
     * Activity forground recorder
     */
    public void hookTopActivity() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    sLastResumed = (Activity) param.thisObject;
                    MainHook.log(true, "hookTopActivity: onResume");
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (sLastResumed == param.thisObject) sLastResumed = null;
                    MainHook.log(true, "hookTopActivity: onPause");
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] sqz.wenku8.bg hookTopActivity: " + t);
        }
    }
}
