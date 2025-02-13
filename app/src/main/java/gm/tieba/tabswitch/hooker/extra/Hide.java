package gm.tieba.tabswitch.hooker.extra;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.BuildConfig;
import gm.tieba.tabswitch.XposedContext;
import gm.tieba.tabswitch.hooker.IHooker;

public class Hide extends XposedContext implements IHooker {

    @NonNull
    @Override
    public String key() {
        return "hide";
    }

    /**
     * @deprecated hook VMStack_getThreadStackTrace instead.
     */
    @Deprecated
    @Override
    public void hook() throws Throwable {
        for (Class<?> clazz : new Class<?>[]{Throwable.class, Thread.class}) {
            XposedHelpers.findAndHookMethod(clazz, "getStackTrace", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    StackTraceElement[] stes = (StackTraceElement[]) param.getResult();
                    List<StackTraceElement> filtered = new ArrayList<>();
                    for (StackTraceElement ste : stes) {
                        String name = ste.getClassName();
                        if (!name.contains("posed") && !name.contains("Hooker")
                                && !name.contains(BuildConfig.APPLICATION_ID)
                                && !name.equals("java.lang.reflect.Method")) {
                            filtered.add(ste);
                        }
                    }

                    StackTraceElement[] result = new StackTraceElement[filtered.size()];
                    for (int i = 0; i < filtered.size(); i++) {
                        result[i] = filtered.get(i);
                    }
                    param.setResult(result);
                }
            });
        }
    }
}
