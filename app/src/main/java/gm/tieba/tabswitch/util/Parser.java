package gm.tieba.tabswitch.util;

import java.util.List;

import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.XposedContext;

public class Parser extends XposedContext {
    public static String parsePbContent(Object instance, String fieldName) {
        List<?> contents = (List<?>) XposedHelpers.getObjectField(instance, fieldName);
        StringBuilder pbContent = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            pbContent.append(XposedHelpers.getObjectField(contents.get(i), "text"));
        }
        return pbContent.toString();
    }
}
