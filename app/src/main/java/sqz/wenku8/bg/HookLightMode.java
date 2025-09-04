package sqz.wenku8.bg;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookLightMode {
    public static final String BUTTON_RES_FULL = "org.mewx.wenku8:id/btn_daylight";
    public static final String CLASS_THEME = "y2.c";
    public static final String CLASS_ACTIVITY = "org.mewx.wenku8.reader.activity.Wenku8ReaderActivityV1";

    // ===== runtime state =====
    private static AtomicBoolean inPowerSave;
    private static AtomicBoolean inDarkMode;
    private static final ThreadLocal<Boolean> isProgrammatic = ThreadLocal.withInitial(() -> false);

    private static final AtomicBoolean isModeChanged = new AtomicBoolean(false);

    @SuppressLint("StaticFieldLeak")
    private static volatile Activity sLastResumed = null;

    /**
     * Activity forground recorder
     */
    private void hookTopActivity() {
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

    /**
     * mark user clicked btn_daylight
     */
    private void markUserTriggeredByOnClick(XC_LoadPackage.LoadPackageParam lpp) {
        try {
            Class<?> listenerImpl = XposedHelpers.findClass("t2.b", lpp.classLoader);
            XposedBridge.hookAllMethods(listenerImpl, "onClick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        View v = (View) p.args[0];
                        if (v == null) return;
                        String full = fullResName(v);
                        if (BUTTON_RES_FULL.equals(full)) {
                            boolean programmatic = Boolean.TRUE.equals(isProgrammatic.get());
                            if (!programmatic) {
                                MainHook.log(true, "User clicked btn_daylight → mark userTriggered=true");
                            } else {
                                MainHook.log(true, "Programmatic click detected, not marking userTriggered");
                            }
                            isModeChanged.set(!isModeChanged.get());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] sqz.wenku8.bg markUserTriggeredByOnClick: " + t);
        }
    }

    private static String fullResName(View v) {
        try {
            int id = v.getId();
            if (id == View.NO_ID) return "NO_ID";
            return v.getResources().getResourceName(id);
        } catch (Throwable e) {
            return "UNKNOWN_ID";
        }
    }

    /**
     * power save mode change listener
     */
    private void hookBettrySaver(final XC_LoadPackage.LoadPackageParam lpp) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Application app = (Application) param.thisObject;
                    final Context ctx = app.getApplicationContext();

                    // Initial current states
                    PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                    boolean now = pm != null && pm.isPowerSaveMode();
                    if (now && inPowerSave == null) {
                        triggerBusinessDirect(lpp);
                        isModeChanged.set(true);
                    }
                    inPowerSave = new AtomicBoolean(now);
                    MainHook.log(false, "Initial power save = " + now);

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
                                        triggerBusinessDirect(lpp);
                                        isModeChanged.set(true);
                                    }
                                    MainHook.log(true, "PowerSave ENTER");
                                } else if (!on && prev) {
                                    if (isModeChanged.get()) {
                                        if (inDarkMode != null && inDarkMode.get()) {
                                            return;
                                        }
                                        triggerBusinessDirect(lpp);
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

    /**
     * dark mode change listener
     */
    private void hookDarkMode(final XC_LoadPackage.LoadPackageParam lpp) {
        try {
            // Register hook: get dark mode state
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Application app = (Application) param.thisObject;
                    final Context ctx = app.getApplicationContext();

                    // Initial current states
                    int nightModeFlagsInit = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    boolean isDarkInit = nightModeFlagsInit == Configuration.UI_MODE_NIGHT_YES;
                    if (isDarkInit && inDarkMode == null) {
                        triggerBusinessDirect(lpp);
                        isModeChanged.set(true);
                    }
                    inDarkMode = new AtomicBoolean(false);
                    inDarkMode.set(isDarkInit);
                    MainHook.log(false, "Initial dark mode = " + isDarkInit);
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
                            triggerBusinessDirect(lpp);
                            isModeChanged.set(true);
                        }
                        MainHook.log(true, "Dark mode ENTER");
                    } else if (!isDarkMode && prev) {
                        if (isModeChanged.get()) {
                            triggerBusinessDirect(lpp);
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

    /**
     * trigger business logic directly
     */
    private void triggerBusinessDirect(XC_LoadPackage.LoadPackageParam lpp) {
        try {
            isProgrammatic.set(true);

            // 1) y2.c
            Class<?> C = XposedHelpers.findClass(CLASS_THEME, lpp.classLoader);

            // 2) get current mode & flip static flag y2.c.i
            boolean inDay = (boolean) XposedHelpers.callStaticMethod(C, "getInDayMode");
            MainHook.log(true, "getInDayMode()=" + inDay + " → flipping");
            XposedHelpers.setStaticBooleanField(C, "i", !inDay);

            // 3) get color y2.c.k.{a,b}
            Object k = safeGetStaticField(C, "k");
            int colA = (k != null) ? safeGetIntField(k, "a") : 0;
            int colB = (k != null) ? safeGetIntField(k, "b") : 0;
            int newColor = (!inDay) ? colB : colA; // fliped target color

            // 4) update color y2.c.s / y2.c.t
            Object tpS = safeGetStaticField(C, "s"); // TextPaint
            Object tpT = safeGetStaticField(C, "t"); // TextPaint
            if (tpS != null) XposedHelpers.callMethod(tpS, "setColor", newColor);
            if (tpT != null) XposedHelpers.callMethod(tpT, "setColor", newColor);

            // 5) If the reader is in the foreground, refresh UI: A.i() / A.h()
            Activity a = sLastResumed;
            if (a != null && CLASS_ACTIVITY.equals(a.getClass().getName())) {
                try {
                    Object A = XposedHelpers.getObjectField(a, "A");
                    if (A != null) {
                        XposedHelpers.callMethod(A, "i");
                        XposedHelpers.callMethod(A, "h");
                        MainHook.log(true, "UI refreshed via A.i()/A.h()");
                    } else {
                        MainHook.log(true, "Field A is null; skipped refresh");
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[ERROR] " + "Refresh failed: " + t);
                }
            } else {
                MainHook.log(true, "ReaderActivity not resumed; state toggled only.");
            }

        } catch (Throwable t) {
            XposedBridge.log("[ERROR] sqz.wenku8.bg triggerBusinessDirect: " + t);
        } finally {
            isProgrammatic.set(false);
        }
    }

    private static Object safeGetStaticField(Class<?> c, String name) {
        try {
            return XposedHelpers.getStaticObjectField(c, name);
        } catch (Throwable t) {
            XposedBridge.log("[ WARN] " + "getStaticField fail: " + name + " -> " + t);
            return null;
        }
    }

    private static int safeGetIntField(Object obj, String name) {
        try {
            return XposedHelpers.getIntField(obj, name);
        } catch (Throwable t) {
            XposedBridge.log("[ WARN] " + "getIntField fail: " + name + " -> " + t);
            return 0;
        }
    }

    /**
     * Main hook for handle dark and light mode
     */
    public void mainHook(XC_LoadPackage.LoadPackageParam lpParam) {
        try {
            // ===== Get state =====
            hookTopActivity();
            markUserTriggeredByOnClick(lpParam);

            // ===== Listeners =====
            hookBettrySaver(lpParam);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hookDarkMode(lpParam);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ERROR] sqz.wenku8.bg mainHook: " + t);
        }
    }
}
