package sqz.wenku8.bg;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import sqz.wenku8.bg.hook.primary.HookBackground;
import sqz.wenku8.bg.hook.view.mode.HookLightMode;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    /**
     * @noinspection SpellCheckingInspection
     */
    public static final String HOOK_PACKAGE = "org.mewx.wenku8";

    public static String MODULE_PATH;

    private static long PACKAGE_VERSION_CODE = -2;

    public static long getPackageVersionCode() throws NullPointerException {
        if (PACKAGE_VERSION_CODE < 0) {
            if (PACKAGE_VERSION_CODE == -1) {
                throw new NullPointerException("PACKAGE_VERSION_CODE seem like failed to set");
            }
            if (PACKAGE_VERSION_CODE == -2) {
                throw new NullPointerException("PACKAGE_VERSION_CODE never set");
            }
            throw new NullPointerException("Unknown reason from PACKAGE_VERSION_CODE");
        }
        return PACKAGE_VERSION_CODE;
    }

    public static void log(boolean debug, String msg) {
        if (debug) {
            if (BuildConfig.DEBUG) {
                XposedBridge.log("[ DEBUG] " + msg);
            }
        } else {
            XposedBridge.log("[ INFO] " + msg);
        }
    }

    private interface StartHook {
        void afterSet();
    }

    private void setVersionCode(XC_LoadPackage.LoadPackageParam lpParam, StartHook startHook) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                Context ctx = (Context) param.args[0];
                PackageManager pm = ctx.getPackageManager();
                try {
                    PackageInfo pi;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pi = pm.getPackageInfo(lpParam.packageName, PackageManager.PackageInfoFlags.of(0));
                    } else {
                        pi = pm.getPackageInfo(lpParam.packageName, 0);
                    }
                    PACKAGE_VERSION_CODE = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            ? pi.getLongVersionCode()
                            : pi.versionCode;
                } catch (PackageManager.NameNotFoundException e) {
                    XposedBridge.log("[ERROR] Package not found: " + lpParam.packageName);
                    PACKAGE_VERSION_CODE = -1;
                }
                startHook.afterSet();
            }
        });
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        MODULE_PATH = startupParam.modulePath;
    }

    private void hookMethods(XC_LoadPackage.LoadPackageParam lpParam) {
        // hook reading background
        new HookBackground().mainHook(lpParam);
        // hook light mode
        new HookLightMode().mainHook(lpParam);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpParam) {
        if (!lpParam.packageName.equals(HOOK_PACKAGE)) {
            return;
        }
        setVersionCode(lpParam, () -> {
            MainHook.log(false, "Hooked " + HOOK_PACKAGE + " (version code: " + getPackageVersionCode() + ")");
            hookMethods(lpParam);
        });
    }
}
