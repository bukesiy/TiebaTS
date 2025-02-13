package gm.tieba.tabswitch.hooker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.Constants;
import gm.tieba.tabswitch.XposedContext;
import gm.tieba.tabswitch.dao.Preferences;
import gm.tieba.tabswitch.util.ReflectUtils;
import gm.tieba.tabswitch.widget.Switch;
import gm.tieba.tabswitch.widget.TbDialog;
import gm.tieba.tabswitch.widget.TbEditText;
import gm.tieba.tabswitch.widget.TbToast;

public class TSPreferenceHelper extends XposedContext {
    public static TextView createTextView(String text) {
        TextView textView = new TextView(getContext());
        textView.setText(text);
        textView.setTextColor(ReflectUtils.getColor("CAM_X0108"));
        textView.setTextSize(ReflectUtils.getDimenDip("fontsize28"));
        LinearLayout.LayoutParams layoutParams;
        if (text != null) {
            textView.setPaddingRelative((int) ReflectUtils.getDimen("ds30"),
                    (int) ReflectUtils.getDimen("ds32"), 0,
                    (int) ReflectUtils.getDimen("ds10"));
            layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) ReflectUtils.getDimen("ds32"));
        }
        textView.setLayoutParams(layoutParams);
        return textView;
    }

    public static LinearLayout createButton(String text, String tip, boolean showArrow, View.OnClickListener l) {
        Object textTipView = XposedHelpers.newInstance(XposedHelpers.findClass(
                "com.baidu.tbadk.coreExtra.view.TbSettingTextTipView", sClassLoader), getContext());
        XposedHelpers.callMethod(textTipView, "setText", text);
        XposedHelpers.callMethod(textTipView, "setTip", tip);
        if (!showArrow) {
            // R.id.arrow2
            var imageView = ReflectUtils.getObjectField(textTipView, ImageView.class);
            imageView.setVisibility(View.GONE);
        }

        var newButton = ReflectUtils.getObjectField(textTipView, LinearLayout.class);
        ((ViewGroup) newButton.getParent()).removeView(newButton);
        newButton.setBackgroundColor(ReflectUtils.getColor("CAM_X0201"));
        if (l != null) newButton.setOnClickListener(l);
        return newButton;
    }

    static String randomToast() {
        switch (new Random().nextInt(5)) {
            case 0:
                return "别点了，新版本在做了";
            case 1:
                return "别点了别点了T_T";
            case 2:
                return "再点人傻了>_<";
            case 3:
                return "点了也没用~";
            case 4:
                return "点个小星星吧:)";
            default:
                return "";
        }
    }

    public static class PreferenceLayout extends LinearLayout {
        public PreferenceLayout(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
        }

        public void addView(SwitchButtonHolder view) {
            addView(view.switchButton);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    static class SwitchButtonHolder {
        public final static int TYPE_SWITCH = 0;
        public final static int TYPE_DIALOG = 1;
        public final static int TYPE_SET_FLUTTER = 2;
        public final static Map<Integer, String> sIdToTag = new HashMap<>();
        private final String mKey;
        public Switch bdSwitch;
        public LinearLayout switchButton;

        SwitchButtonHolder(Activity activity, String text, String key, int type) {
            mKey = key;
            if (sExceptions.containsKey(key)) {
                switchButton = createButton(text, "此功能初始化失败", false, v -> {
                    Throwable tr = sExceptions.get(key);
                    XposedBridge.log(tr);
                    Toast.makeText(activity, Log.getStackTraceString(tr), Toast.LENGTH_SHORT).show();
                });
                return;
            }
            bdSwitch = new Switch();
            bdSwitch.setOnSwitchStateChangeListener(new SwitchStatusChangeHandler());
            View bdSwitchView = bdSwitch.bdSwitch;
            bdSwitchView.setLayoutParams(new LinearLayout.LayoutParams(bdSwitchView.getWidth(),
                    bdSwitchView.getHeight(), 0.16F));
            bdSwitchView.setId(View.generateViewId());
            switch (type) {
                case TYPE_SWITCH:
                    switchButton = createButton(text, null, false, v -> bdSwitch.changeState());
                    sIdToTag.put(bdSwitchView.getId(), TYPE_SWITCH + key);
                    if (Preferences.getBoolean(key)) bdSwitch.turnOn();
                    else bdSwitch.turnOff();
                    break;
                case TYPE_DIALOG:
                    switchButton = createButton(text, null, false, v -> showRegexDialog(activity));
                    bdSwitchView.setOnTouchListener((v, event) -> false);
                    if (Preferences.getString(key) != null) bdSwitch.turnOn();
                    else bdSwitch.turnOff();
                    break;
                case TYPE_SET_FLUTTER:
                    switchButton = createButton(text, null, false, v -> bdSwitch.changeState());
                    sIdToTag.put(bdSwitchView.getId(), TYPE_SET_FLUTTER + key);
                    if (Preferences.getStringSet("switch_manager").contains(key)) bdSwitch.turnOn();
                    else bdSwitch.turnOff();
                    break;
            }
            switchButton.addView(bdSwitchView);
        }

        void setOnButtonClickListener(View.OnClickListener l) {
            switchButton.setOnClickListener(l);
            bdSwitch.bdSwitch.setOnTouchListener((View v, MotionEvent event) -> false);
        }

        private void showRegexDialog(Activity activity) {
            EditText editText = new TbEditText(getContext());
            editText.setHint(Constants.getStrings().get("regex_hint"));
            editText.setText(Preferences.getString(mKey));
            TbDialog bdAlert = new TbDialog(activity, null, null, true, editText);
            bdAlert.setOnNoButtonClickListener(v -> bdAlert.dismiss());
            bdAlert.setOnYesButtonClickListener(v -> {
                try {
                    if (TextUtils.isEmpty(editText.getText())) {
                        Preferences.remove(mKey);
                        bdSwitch.turnOff();
                    } else {
                        Pattern.compile(editText.getText().toString());
                        Preferences.putString(mKey, editText.getText().toString());
                        bdSwitch.turnOn();
                    }
                    bdAlert.dismiss();
                } catch (PatternSyntaxException e) {
                    TbToast.showTbToast(e.getMessage(), TbToast.LENGTH_SHORT);
                }
            });
            bdAlert.show();
            bdAlert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE || event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    bdAlert.findYesButton().performClick();
                    return true;
                }
                return false;
            });
            editText.requestFocus();
        }

        private static class SwitchStatusChangeHandler implements InvocationHandler {
            @SuppressWarnings("SuspiciousInvocationHandlerImplementation")
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                View view = (View) args[0];
                var tag = sIdToTag.get(view.getId());
                if (tag != null) {
                    switch (Integer.parseInt(tag.substring(0, 1))) {
                        case TYPE_SWITCH:
                            Preferences.putBoolean(tag.substring(1), args[1].toString().equals("ON"));
                            break;
                        case TYPE_SET_FLUTTER:
                            Preferences.putStringSet("switch_manager",
                                    tag.substring(1), args[1].toString().equals("ON"));
                            break;
                    }
                }
                return null;
            }
        }
    }
}
