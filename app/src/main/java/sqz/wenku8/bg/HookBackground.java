package sqz.wenku8.bg;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookBackground {
    public static final String OVERRIDE_DRAWABLE = "reader_bg_yellow_edge";
    public static final String OVERRIDE_DESCRIBE_ARRAY = "reader_background_option";

    @SuppressLint("ResourceType")
    private void hookReadingBackground(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length < 2) return;
        Object resObj = param.args[0], idObj = param.args[1];
        if (!(resObj instanceof Resources) || !(idObj instanceof Integer)) {
            XposedBridge.log("[ERROR] replacement decode failed: instance type err");
            return;
        }
        Resources res = (Resources) resObj;
        int id = (Integer) idObj;

        try {
            String pkg = res.getResourcePackageName(id);
            String name = res.getResourceEntryName(id);
            String type = res.getResourceTypeName(id);
            if (MainHook.HOOK_PACKAGE.equals(pkg) && "drawable".equals(type) && OVERRIDE_DRAWABLE.equals(name)) {
                XModuleResources modRes = XModuleResources.createInstance(MainHook.MODULE_PATH, null);
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
                        MainHook.log(false, "decodeResource replaced: " + pkg + "/" + type + "/" + name + " (#" + id + ")");
                        MainHook.log(true, "target resource replaced");
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

    @SuppressLint("DiscouragedApi")
    private void hookDescribe(XC_MethodHook.MethodHookParam param, XC_LoadPackage.LoadPackageParam lpParam) {
        Resources res = (Resources) param.thisObject;
        int id = (int) param.args[0];
        int targetId = res.getIdentifier(OVERRIDE_DESCRIBE_ARRAY, "array", lpParam.packageName);
        if (targetId == 0 || id != targetId) {
            XposedBridge.log("[ERROR] replacement target string failed: invalid id");
            return;
        }
        CharSequence[] origin = (CharSequence[]) param.getResult();
        if (origin == null || origin.length == 0) {
            XposedBridge.log("[ERROR] replacement target string failed: invalid array");
            return;
        }
        CharSequence[] modified = Arrays.copyOf(origin, origin.length);
        int indexToChange = 0;
        if (indexToChange >= 0 && indexToChange < modified.length) {
            if (Objects.equals(modified[indexToChange], "使用默认的模拟纸张")) {
                MainHook.log(true, "target local string is Chinese-CPP");
                modified[indexToChange] = "正在使用 Xposed 模块提供的背景";
            } else if (Objects.equals(modified[indexToChange], "使用默認的模擬紙張")) {
                MainHook.log(true, "target local string is Chinese-Taiwan");
                modified[indexToChange] = "正在使用 Xposed 模組提供的背景";
            } else {
                MainHook.log(true, "Unknown target local string");
                modified[indexToChange] = "The Xposed module is already replaced the reading background";
            }
        }
        param.setResult(modified);
        MainHook.log(false, "target string replaced");
    }

    /**
     * Main hook for reading background
     */
    public void mainHook(XC_LoadPackage.LoadPackageParam lpParam) {
        // hook reading background
        XposedBridge.hookAllMethods(
                BitmapFactory.class, "decodeResource",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        hookReadingBackground(param);
                    }
                }
        );
        // hook reading bg describe
        XposedBridge.hookAllMethods(
                Resources.class, "getTextArray",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            hookDescribe(param, lpParam);
                        } catch (Throwable e) {
                            XposedBridge.log("[ERROR] replacement target string failed: " + e);
                        }
                    }
                }
        );
    }
}
