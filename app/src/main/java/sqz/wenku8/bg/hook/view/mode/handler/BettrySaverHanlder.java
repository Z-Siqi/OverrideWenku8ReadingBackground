package sqz.wenku8.bg.hook.view.mode.handler;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import sqz.wenku8.bg.MainHook;

public class BettrySaverHanlder {
    private final AtomicBoolean isModeChanged;

    public BettrySaverHanlder(final AtomicBoolean isModeChanged) {
        this.isModeChanged = isModeChanged;
    }

    private static AtomicBoolean inPowerSave;

    private void initCurrentState(Context ctx, Callback callback) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean now = pm != null && pm.isPowerSaveMode();
        if (now && inPowerSave == null) {
            callback.onChanged();
            isModeChanged.set(true);
        }
        inPowerSave = new AtomicBoolean(now);
        MainHook.log(false, "Initial power save = " + now);
    }

    public interface Callback {
        void onChanged();
    }

    /**
     * power save mode change listener
     */
    public void hookBettrySaver(DarkModeHandler darkModeHandler, Callback callback) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Application app = (Application) param.thisObject;
                    final Context ctx = app.getApplicationContext();

                    // Initial current states
                    initCurrentState(ctx, callback);

                    // Register receiver: power save mode change
                    IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                    ctx.registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                PowerManager p2 = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                                boolean on = p2 != null && p2.isPowerSaveMode();
                                boolean prev = inPowerSave.getAndSet(on);
                                if (on && !prev) {
                                    if (!isModeChanged.get()) {
                                        callback.onChanged();
                                        isModeChanged.set(true);
                                    }
                                    MainHook.log(true, "PowerSave ENTER");
                                } else if (!on && prev) {
                                    if (isModeChanged.get()) {
                                        if (darkModeHandler.getInDarkMode() != null && darkModeHandler.getInDarkMode().get()) {
                                            return;
                                        }
                                        callback.onChanged();
                                        isModeChanged.set(false);
                                    }
                                    MainHook.log(true, "PowerSave EXIT");
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[ERROR] sqz.wenku8.bg hookBettrySaver: " + t);
                            }
                        }
                    }, filter);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] sqz.wenku8.bg hookBettrySaver: " + t);
        }
    }
}
