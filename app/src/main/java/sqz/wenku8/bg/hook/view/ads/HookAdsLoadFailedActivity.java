package sqz.wenku8.bg.hook.view.ads;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.content.ComponentName;
import android.content.Intent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookAdsLoadFailedActivity {

    // Note: this will not disable any ads to display, just fix the ads load failed.
    // This issue should not happend in web ads can display correctly, but target app failed.
    // THAT IS WHY TESTING IS IMPORTANT!
    private void interceptActivityStart(XC_LoadPackage.LoadPackageParam lpParam) {
        findAndHookMethod(
                "android.app.ContextImpl",
                lpParam.classLoader,
                "startActivity",
                Intent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Intent it = (Intent) param.args[0];
                        if (it == null) return;
                        ComponentName cn = it.getComponent();
                        if (cn != null) {
                            String cls = cn.getClassName();
                            if ("com.pairip.licensecheck.LicenseActivity".equals(cls)) {
                                param.setResult(null);
                            }
                        }
                    }
                }
        );
    }

    public void mainHook(XC_LoadPackage.LoadPackageParam lpParam) {
        try {
            this.interceptActivityStart(lpParam);
        } catch (Exception e) {
            XposedBridge.log("[ERROR] HookAdsLoadFailedActivity.java Failed: " + e);
        }
    }
}
