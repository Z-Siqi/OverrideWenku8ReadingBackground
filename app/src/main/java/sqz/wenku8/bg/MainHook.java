package sqz.wenku8.bg;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    /**
     * @noinspection SpellCheckingInspection
     */
    public static final String HOOK_PACKAGE = "org.mewx.wenku8";

    public static String MODULE_PATH;

    public static void log(boolean debug, String msg) {
        if (debug) {
            if (BuildConfig.DEBUG) {
                XposedBridge.log("[ DEBUG] " + msg);
            }
        } else {
            XposedBridge.log("[ INFO] " + msg);
        }
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
        MainHook.log(false, "Hooked " + HOOK_PACKAGE);
        this.hookMethods(lpParam);
    }
}
