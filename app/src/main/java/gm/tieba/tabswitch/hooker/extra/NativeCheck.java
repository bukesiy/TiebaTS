package gm.tieba.tabswitch.hooker.extra;

import android.annotation.SuppressLint;

import de.robv.android.xposed.XposedBridge;
import gm.tieba.tabswitch.XposedContext;

@SuppressLint("UnsafeDynamicallyLoadedCode")
public class NativeCheck extends XposedContext {
    static {
        var soPaths = new String[]{
                sPath + "!/lib/armeabi-v7a/libcheck.so",
                sPath + "!/lib/arm64-v8a/libcheck.so",
        };
        for (int i = 0; i < 3; i++) {
            for (var soPath : soPaths) {
                try {
                    System.load(soPath);
                    break;
                } catch (UnsatisfiedLinkError e) {
                    XposedBridge.log(i + soPath);
                    XposedBridge.log(e);
                }
            }
        }
    }

    public static native boolean inline(String name);

    public static native boolean isFindClassInline();

    public static native boolean findXposed();

    public static native int access(String path);

    public static native String fopen(String path);
}
