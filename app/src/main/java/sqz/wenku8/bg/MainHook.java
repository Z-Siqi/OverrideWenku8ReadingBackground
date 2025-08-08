package sqz.wenku8.bg;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static final String HOOK_PACKAGE = "org.mewx.wenku8";
    public static final String OVERRIDE_DRAWABLE = "reader_bg_yellow_edge";

    public static String MODULE_PATH;

    @Override
    public void initZygote(StartupParam startupParam) {
        MODULE_PATH = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(HOOK_PACKAGE)) {
            return;
        }
        XposedBridge.log("[ INFO] Hooked " + HOOK_PACKAGE);
        XposedBridge.hookAllMethods(
                BitmapFactory.class, "decodeResource",
                new XC_MethodHook() {
                    @Override
                    @SuppressLint("ResourceType")
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args == null || param.args.length < 2) return;
                        Object resObj = param.args[0], idObj = param.args[1];
                        if (!(resObj instanceof Resources) || !(idObj instanceof Integer)) {
                            return;
                        }
                        Resources res = (Resources) resObj;
                        int id = (Integer) idObj;

                        try {
                            String pkg = res.getResourcePackageName(id);
                            String name = res.getResourceEntryName(id);
                            String type = res.getResourceTypeName(id);
                            if (HOOK_PACKAGE.equals(pkg) && "drawable".equals(type) && OVERRIDE_DRAWABLE.equals(name)) {
                                XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, null);
                                InputStream is = null;
                                try {
                                    is = modRes.openRawResource(R.drawable.background);
                                    BitmapFactory.Options opts = null;
                                    if (param.args.length >= 3 && param.args[2] instanceof BitmapFactory.Options) {
                                        opts = (BitmapFactory.Options) param.args[2];
                                    }

                                    Bitmap bmp = (opts != null)
                                            ? BitmapFactory.decodeStream(is, null, opts)
                                            : BitmapFactory.decodeStream(is);
                                    if (bmp != null) {
                                        param.setResult(bmp);
                                        XposedBridge.log("[ INFO] decodeResource replaced: " + pkg + "/" + type + "/" + name + " (#" + id + ")");
                                    } else {
                                        XposedBridge.log("[ERROR] replacement decode failed, fall through.");
                                    }
                                } finally {
                                    if (is != null) try {
                                        is.close();
                                    } catch (Throwable ignore) {
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[ERROR] " + t);
                        }
                    }
                }
        );
    }
}
