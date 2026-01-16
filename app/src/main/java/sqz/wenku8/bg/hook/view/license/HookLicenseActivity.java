package sqz.wenku8.bg.hook.view.license;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.content.ComponentName;
import android.content.Intent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookLicenseActivity {

    // Note: this will fix the LicenseActivity may showing and break user experence
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
            XposedBridge.log("[ERROR] HookLicenseActivity.java Failed: " + e);
        }
    }
}
