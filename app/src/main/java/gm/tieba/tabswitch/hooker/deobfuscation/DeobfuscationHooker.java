package gm.tieba.tabswitch.hooker.deobfuscation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.XposedContext;
import gm.tieba.tabswitch.dao.AcRules;
import gm.tieba.tabswitch.dao.Preferences;
import gm.tieba.tabswitch.hooker.IHooker;

public class DeobfuscationHooker extends XposedContext implements IHooker {
    private static final String TRAMPOLINE_ACTIVITY = "com.baidu.tieba.tblauncher.MainTabActivity";
    private final DeobfuscationViewModel viewModel = new DeobfuscationViewModel();
    private final List<Matcher> mMatchers;
    private Activity mActivity;
    private View mProgress;
    private TextView mMessage;
    private FrameLayout mProgressContainer;
    private LinearLayout mContentView;

    public DeobfuscationHooker(List<Matcher> matchers) {
        mMatchers = matchers;
    }

    @NonNull
    @Override
    public String key() {
        return "deobfs";
    }

    @Override
    public void hook() throws Throwable {
        XposedHelpers.findAndHookMethod("com.baidu.tieba.LogoActivity", sClassLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @SuppressLint("ApplySharedPref")
            @Override
            public void afterHookedMethod(MethodHookParam param) throws Throwable {
                var hooks = disableStartAndFinishActivity();
                mActivity = (Activity) param.thisObject;
                if (Preferences.getBoolean("purge")) {
                    var editor = mActivity
                            .getSharedPreferences("settings", Context.MODE_PRIVATE)
                            .edit();
                    editor.putString("key_location_request_dialog_last_show_version",
                            DeobfuscationHelper.getTbVersion(mActivity)
                    );
                    editor.commit();
                }

                if (DeobfuscationHelper.isDexChanged(mActivity)) {
                    AcRules.dropAllRules();
                } else {
                    hooks.forEach(Unhook::unhook);
                    DeobfuscationHelper.saveAndRestart(mActivity,
                            DeobfuscationHelper.getTbVersion(mActivity),
                            XposedHelpers.findClass(TRAMPOLINE_ACTIVITY, sClassLoader)
                    );
                    return;
                }

                initProgressIndicator();
                mActivity.addContentView(mContentView, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
                ));
                viewModel.progress.subscribe(progress -> setProgress(progress));

                new Thread(() -> {
                    try {
                        setMessage("(1/4) 解压");
                        viewModel.deobfuscateStep1(mActivity, mMatchers);

                        setMessage("(2/4) 解析资源");
                        viewModel.deobfuscateStep2();

                        setMessage("(3/4) 搜索字符串和资源 id");
                        var scope = viewModel.deobfuscateStep3();

                        setMessage("(4/4) 在 " + scope.pkg + " 中搜索代码");
                        viewModel.deobfuscateStep4();

                        XposedBridge.log("deobfuscate accomplished, current version: "
                                + DeobfuscationHelper.getTbVersion(mActivity));
                        hooks.forEach(Unhook::unhook);
                        DeobfuscationHelper.saveAndRestart(mActivity,
                                DeobfuscationHelper.getTbVersion(mActivity),
                                XposedHelpers.findClass(TRAMPOLINE_ACTIVITY, sClassLoader)
                        );
                    } catch (Throwable e) {
                        XposedBridge.log(e);
                        setMessage("处理失败\n" + Log.getStackTraceString(e));
                    }
                }).start();
            }
        });
    }

    @NonNull
    private List<XC_MethodHook.Unhook> disableStartAndFinishActivity() {
        return List.of(
                XposedHelpers.findAndHookMethod(Instrumentation.class, "execStartActivity",
                        Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class,
                        int.class, Bundle.class, XC_MethodReplacement.returnConstant(null)),
                XposedHelpers.findAndHookMethod(Activity.class, "finish",
                        int.class, XC_MethodReplacement.returnConstant(null)),
                XposedHelpers.findAndHookMethod(Activity.class, "finishActivity",
                        int.class, XC_MethodReplacement.returnConstant(null)),
                XposedHelpers.findAndHookMethod(Activity.class, "finishAffinity",
                        XC_MethodReplacement.returnConstant(null))
        );
    }

    @SuppressLint({"SetTextI18n"})
    private void initProgressIndicator() {
        var title = new TextView(mActivity);
        title.setTextSize(16);
        title.setPaddingRelative(0, 0, 0, 8);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setTextColor(Color.parseColor("#FF303030"));
        title.setText("贴吧TS正在定位被混淆的类和方法，请耐心等待");
        mProgress = new View(mActivity);
        mProgress.setBackgroundColor(Color.parseColor("#FFBEBEBE"));
        mMessage = new TextView(mActivity);
        mMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mMessage.setTextSize(16);
        mMessage.setTextColor(Color.parseColor("#FF303030"));
        var messageLayoutParams = new FrameLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        mMessage.setLayoutParams(messageLayoutParams);
        mProgressContainer = new FrameLayout(mActivity);
        mProgressContainer.addView(mProgress);
        mProgressContainer.addView(mMessage);
        var frameLayoutParams = new FrameLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        mProgressContainer.setLayoutParams(frameLayoutParams);
        var progressIndicator = new LinearLayout(mActivity);
        progressIndicator.setOrientation(LinearLayout.VERTICAL);
        progressIndicator.setBackgroundColor(Color.WHITE);
        progressIndicator.addView(title);
        progressIndicator.addView(mProgressContainer);
        progressIndicator.setPaddingRelative(0, 16, 0, 16);
        var linearLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressIndicator.setLayoutParams(linearLayoutParams);
        mContentView = new LinearLayout(mActivity);
        mContentView.setGravity(Gravity.CENTER);
        mContentView.addView(progressIndicator);
    }

    private void setMessage(String message) {
        mActivity.runOnUiThread(() -> mMessage.setText(message));
    }

    private void setProgress(float progress) {
        mActivity.runOnUiThread(() -> {
            var lp = mProgress.getLayoutParams();
            lp.height = mMessage.getHeight();
            lp.width = Math.round(mProgressContainer.getWidth() * progress);
            mProgress.setLayoutParams(lp);
        });
    }
}
