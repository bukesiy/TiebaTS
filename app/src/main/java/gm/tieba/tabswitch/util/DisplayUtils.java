package gm.tieba.tabswitch.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

public class DisplayUtils {
    public static boolean isLightMode(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_NO;
    }

    public static void restart(Activity activity) {
        var intent = activity.getPackageManager().getLaunchIntentForPackage(activity
                .getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            System.exit(0);
        }
    }

    public static String getTbSkin(Context context) {
        final var settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (settings.getBoolean("key_is_follow_system_mode", false)) {
            return isLightMode(context) ? "" : "_2";
        } else {
            final var commonSettings = context.getSharedPreferences(
                    "common_settings", Context.MODE_PRIVATE);
            switch (commonSettings.getString("skin_", "0")) {
                case "4":
                    return "_2";
                case "0":
                default:
                    return "";
            }
        }
    }

    public static int dipToPx(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    public static int pxToDip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }
}
