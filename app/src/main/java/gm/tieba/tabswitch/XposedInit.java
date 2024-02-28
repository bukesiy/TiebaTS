package gm.tieba.tabswitch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.XModuleResources;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import gm.tieba.tabswitch.dao.AcRules;
import gm.tieba.tabswitch.dao.Adp;
import gm.tieba.tabswitch.dao.Preferences;
import gm.tieba.tabswitch.hooker.Obfuscated;
import gm.tieba.tabswitch.hooker.TSPreference;
import gm.tieba.tabswitch.hooker.add.HistoryCache;
import gm.tieba.tabswitch.hooker.add.Ripple;
import gm.tieba.tabswitch.hooker.add.SaveImages;
import gm.tieba.tabswitch.hooker.auto.AgreeNum;
import gm.tieba.tabswitch.hooker.auto.AutoSign;
import gm.tieba.tabswitch.hooker.auto.FrsTab;
import gm.tieba.tabswitch.hooker.auto.MsgCenterTab;
import gm.tieba.tabswitch.hooker.auto.OpenSign;
import gm.tieba.tabswitch.hooker.auto.OriginSrc;
import gm.tieba.tabswitch.hooker.deobfuscation.DeobfuscationHelper;
import gm.tieba.tabswitch.hooker.deobfuscation.DeobfuscationHooker;
import gm.tieba.tabswitch.hooker.deobfuscation.Matcher;
import gm.tieba.tabswitch.hooker.eliminate.ContentFilter;
import gm.tieba.tabswitch.hooker.eliminate.FoldTopCardView;
import gm.tieba.tabswitch.hooker.eliminate.FollowFilter;
import gm.tieba.tabswitch.hooker.eliminate.FragmentTab;
import gm.tieba.tabswitch.hooker.eliminate.FrsPageFilter;
import gm.tieba.tabswitch.hooker.eliminate.PersonalizedFilter;
import gm.tieba.tabswitch.hooker.eliminate.Purge;
import gm.tieba.tabswitch.hooker.eliminate.PurgeEnter;
import gm.tieba.tabswitch.hooker.eliminate.PurgeMy;
import gm.tieba.tabswitch.hooker.eliminate.RedTip;
import gm.tieba.tabswitch.hooker.eliminate.RemoveUpdate;
import gm.tieba.tabswitch.hooker.eliminate.SwitchManager;
import gm.tieba.tabswitch.hooker.extra.ForbidGesture;
import gm.tieba.tabswitch.hooker.extra.Hide;
import gm.tieba.tabswitch.hooker.extra.StackTrace;
import gm.tieba.tabswitch.util.DisplayUtils;
import gm.tieba.tabswitch.widget.TbDialog;
import gm.tieba.tabswitch.widget.TbToast;

public class XposedInit extends XposedContext implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    @Override
    public void initZygote(final IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        sPath = startupParam.modulePath;
    }

    private AppComponentFactory mAppComponentFactory = null;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.baidu.tieba".equals(lpparam.packageName) && XposedHelpers.findClassIfExists(
                "com.baidu.tieba.tblauncher.MainTabActivity", lpparam.classLoader) == null) return;
        sClassLoader = lpparam.classLoader;
        sAssetManager = XModuleResources.createInstance(sPath, null).getAssets();

        // Workaround to address an issue with LSPatch (unable to open personal homepage)
        // com.baidu.tieba.flutter.base.view.FlutterPageActivity must be instantiated by com.baidu.nps.hook.component.NPSComponentFactory
        // However, LSPatch incorrectly sets appComponentFactory to null, causing android.app.Instrumentation.getFactory to fall back to AppComponentFactory.DEFAULT
        // (see https://github.com/LSPosed/LSPatch/blob/bbe8d93fb9230f7b04babaf1c4a11642110f55a6/patch-loader/src/main/java/org/lsposed/lspatch/loader/LSPApplication.java#L173)
        // TODO: Report issue to upstream
        XposedHelpers.findAndHookMethod(
                Instrumentation.class,
                "getFactory",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args[0].toString().equals("com.baidu.tieba")) {
                            if (mAppComponentFactory == null) {
                                mAppComponentFactory = (AppComponentFactory) sClassLoader.loadClass("com.baidu.nps.hook.component.NPSComponentFactory").newInstance();
                            }
                            param.setResult(mAppComponentFactory);
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                if (!(param.args[0] instanceof Application)) return;
                attachBaseContext((Application) param.args[0]);
                Preferences.init(getContext());
                AcRules.init(getContext());

                final var hookers = List.of(
                        new TSPreference(),
                        new FragmentTab(),
                        new SwitchManager(),
                        new Purge(),
                        new PurgeEnter(),
                        new PurgeMy(),
                        new RedTip(),
                        new FollowFilter(),
                        new PersonalizedFilter(),
                        new ContentFilter(),
                        new FrsPageFilter(),
                        new HistoryCache(),
                        new Ripple(),
                        new SaveImages(),
                        new AutoSign(),
                        new OpenSign(),
                        new OriginSrc(),
                        new ForbidGesture(),
                        new AgreeNum(),
                        new FrsTab(),
                        new Hide(),
                        new StackTrace(),
                        new RemoveUpdate(),
                        new FoldTopCardView(),
                        new MsgCenterTab()
                );
                final var matchers = new ArrayList<Obfuscated>(hookers.size() + 2);
                matchers.add(new TbDialog());
                matchers.add(new TbToast());
                for (final var hooker : hookers) {
                    if (hooker instanceof Obfuscated) {
                        matchers.add((Obfuscated) hooker);
                    }
                }

                if (DeobfuscationHelper.isVersionChanged(getContext())) {
                    if ("com.baidu.tieba".equals(lpparam.processName)) {
                        XposedHelpers.findAndHookMethod("com.baidu.tieba.tblauncher.MainTabActivity", sClassLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                                final var activity = (Activity) param.thisObject;
                                final var intent = new Intent(activity, XposedHelpers.findClass("com.baidu.tieba.LogoActivity", sClassLoader));
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                activity.startActivity(intent);
                            }
                        });
                    }
                    XposedBridge.log("Deobfuscation");
                    new DeobfuscationHooker(
                            matchers.stream()
                                    .map(Obfuscated::matchers)
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toList())
                    ).hook();
                    return;
                }
                final var lostList = matchers.stream()
                        .map(Obfuscated::matchers)
                        .flatMap(Collection::stream)
                        .map(Matcher::toString)
                        .filter(matcher -> !AcRules.isRuleFound(matcher))
                        .collect(Collectors.toList());
                if (!lostList.isEmpty()) {
                    XposedHelpers.findAndHookMethod("com.baidu.tieba.tblauncher.MainTabActivity", sClassLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                            final var activity = (Activity) param.thisObject;
                            final var messages = new ArrayList<String>();
                            messages.add(String.format(Constants.getStrings().get("exception_rules_incomplete"), BuildConfig.TARGET_VERSION));
                            messages.add(String.format(Locale.CHINA, "tbversion: %s, module version: %d",
                                    DeobfuscationHelper.getTbVersion(getContext()), BuildConfig.VERSION_CODE));
                            messages.add(String.format(Locale.CHINA, "%d rule(s) lost: %s", lostList.size(), lostList));
                            final var message = TextUtils.join("\n", messages);
                            XposedBridge.log(message);
                            AlertDialog alert = new AlertDialog.Builder(activity, DisplayUtils.isLightMode(getContext()) ?
                                    android.R.style.Theme_DeviceDefault_Light_Dialog_Alert : android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle("警告").setMessage(message).setCancelable(false)
                                    .setNegativeButton(activity.getString(android.R.string.cancel), null)
                                    .setPositiveButton(activity.getString(android.R.string.ok), (dialogInterface, i) -> {
                                        Preferences.putSignature(0);
                                        DeobfuscationHelper.saveAndRestart(activity, "unknown", null);
                                    }).create();
                            alert.show();
                            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                            layoutParams.copyFrom(alert.getWindow().getAttributes());
                            layoutParams.width = DisplayUtils.getDisplayWidth(getContext());
                            alert.getWindow().setAttributes(layoutParams);
                        }
                    });
                    return;
                }

                new Adp();
                if (Preferences.getBoolean("hide_native")) {
                    try {
                        System.loadLibrary("hide");
                    } catch (final UnsatisfiedLinkError e) {
                        XposedBridge.log(e);
                    }
                }
                final var activeHookerKeys = Preferences.getAll().entrySet().stream()
                        .filter(entry -> Boolean.FALSE != entry.getValue())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
                activeHookerKeys.add("ts_pref");
                activeHookerKeys.add("fragment_tab");
                for (final var hooker : hookers) {
                    try {
                        if (activeHookerKeys.contains(hooker.key())) {
                            hooker.hook();
                        }
                    } catch (final Throwable tr) {
                        XposedBridge.log(tr);
                        sExceptions.put(hooker.key(), tr);
                    }
                }
            }
        });
    }
}
