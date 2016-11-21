package me.chiontang.wechatmomentexport;


import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.EditText;
import android.widget.ListView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import me.chiontang.wechatmomentexport.models.Comment;
import me.chiontang.wechatmomentexport.models.Like;
import me.chiontang.wechatmomentexport.models.Tweet;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class Main implements IXposedHookLoadPackage {

    private boolean isOnCreateInvoked;
    Tweet currentTweet = new Tweet();
    ArrayList<Tweet> tweetList = new ArrayList<Tweet>();
    String lastTimelineId = "";
    Thread intervalSaveThread = null;
    Context appContext = null;
    String wechatVersion = "";
    String wxPackageName = "com.tencent.mm";//测试版本:wechat_6.3.13.49_r4080b63

    private Method[] methods;
    private Field[] fields;
    private Class<?> snsClz;
    private Object snsObj;

    String[] activitys = new String[]{
            ".plugin.sns.ui.ClassifyTimeLineUI", ".plugin.sns.ui.SnsCommentDetailUI",
            ".plugin.sns.ui.SnsDetailUI", ".plugin.sns.ui.SnsStrangerCommentDetailUI",
            ".plugin.sns.ui.SnsUploadUI", ".plugin.sns.ui.SightUploadUI",
            ".plugin.sns.ui.SnsGalleryUI", ".plugin.sns.ui.ArtistBrowseUI",
            ".plugin.sns.ui.SnsBrowseUI", ".plugin.sns.ui.SnsBrowseUI",
            ".plugin.sns.ui.SnsSelectContactDialog", ".plugin.sns.ui.SnsMsgUI",
            ".plugin.sns.ui.SettingSnsBackgroundUI", ".plugin.sns.ui.ArtistUI",
            ".plugin.sns.ui.SnsUploadBrowseUI", ".plugin.sns.ui.SnsCommentUI",
            ".plugin.sns.ui.SnsTagUI", ".plugin.sns.ui.SnsTagDetailUI",
            ".plugin.sns.ui.SnsBlackDetailUI", ".plugin.sns.ui.SnsTagPartlyUI",
            ".plugin.sns.ui.SnsLabelUI", ".plugin.sns.ui.SnsPermissionUI",
            ".plugin.sns.ui.SnsSingleTextViewUI", ".plugin.sns.ui.SnsLongMsgUI",
            ".plugin.sns.ui.SnsSightPlayerUI", ".plugin.sns.ui.SnsNotInterestUI",
            ".plugin.sns.ui.VideoAdPlayerUI", "com.tencent.mm.plugin.record.ui.RecordMsgDetailUI",
            "com.tencent.mm.plugin.record.ui.FavRecordDetailUI", "com.tencent.mm.plugin.record.ui.RecordMsgImageUI",
            "com.tencent.mm.plugin.record.ui.RecordMsgFileUI", "com.tencent.mm.plugin.sysvideo.ui.video.VideoRecorderUI",
            "com.tencent.mm.plugin.sysvideo.ui.video.VideoRecorderPreviewUI"
    };

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("lpparam" + lpparam);
        if (!lpparam.packageName.equals("com.tencent.mm"))
            return;

        loadFromSharedPreference();
        //朋友圈长按条目弹出对话框
        if (isOnCreateInvoked) {
            XposedHelpers.findAndHookMethod("com.tencent.mm.ui.base.k", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);

                    Log.e("hookmsg", "com.tencent.mm.ui.base.k");
                }
            });
            XposedHelpers.findAndHookMethod("com.tencent.mm.ui.base.i", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Log.e("hookmsg", "com.tencent.mm.ui.base.i");
                }
            });
        }

        findAndHookMethod("com.tencent.mm.ui.LauncherUI", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (appContext != null) {
                    return;
                }
                XposedBridge.log("LauncherUI hooked.");
                Log.e("msg", "微信LauncherUI hooked" + "");
                appContext = ((Activity) param.thisObject).getApplicationContext();
                PackageInfo pInfo = appContext.getPackageManager().getPackageInfo(lpparam.packageName, 0);
                if (pInfo != null)
                    wechatVersion = pInfo.versionName;
                XposedBridge.log("WeChat version=" + wechatVersion);
                Config.checkWeChatVersion(wechatVersion);
                Log.e("msg", Config.ready + "" + pInfo);
                if (!Config.ready) {
                    return;
                }
                // hookMethods(lpparam);//朋友圈数据导出方法

                //↓以下为测试代码 需求：长按条目时，hook到朋友圈ListView的条目的长按事件，在弹出的对话框中添加“转发”按钮，点击转发按钮将当前条目的数据转发到朋友圈
                findAndHookMethod(wxPackageName + ".plugin.sns.ui.SnsTimeLineUI", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + ".plugin.sns.ui.SnsTimeLineUI");
                        isOnCreateInvoked = true;
                        //朋友圈
                        snsObj = param.thisObject;
                        snsClz = findClass(wxPackageName + ".plugin.sns.ui.SnsTimeLineUI", lpparam.classLoader);
                        fields = snsClz.getDeclaredFields();
                        methods = snsClz.getDeclaredMethods();
                        Method.setAccessible(methods, true);
                        Field.setAccessible(fields, true);
                        //arb()
                        for (int i = 0; i < methods.length; i++) {
                            Log.e("hookmsg", methods[i] + "");
                            if ("aBu".equals(methods[i].getName())) {
                                ListView lv = (ListView) methods[i].invoke(snsObj);
                                lv.setBackgroundColor(Color.CYAN);
                                Log.e("hookmsg", lv + ",lv.getcount:" + lv.getChildCount());
                                XposedHelpers.findAndHookMethod("com.tencent.mm.ui.base.k", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        super.afterHookedMethod(param);
//                                        Object oo=param.thisObject;
                                        Log.e("hookmsg", "com.tencent.mm.ui.base.k");
                                        //android.app.Dialog.setContentView(View)
                                    }
                                });
                            }
                        }
                        //aCI
                        XposedHelpers.findAndHookMethod(wxPackageName + ".plugin.sns.ui.SnsTimeLineUI", lpparam.classLoader, "Gb", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                super.afterHookedMethod(param);
                                Object o = param.thisObject;
                                Class<?> c = findClass(wxPackageName + ".plugin.sns.ui.SnsTimeLineUI", lpparam.classLoader);
                                Field[] fields = c.getDeclaredFields();
                                Method[] methods = c.getDeclaredMethods();
                                Method.setAccessible(methods, true);
                                Field.setAccessible(fields, true);
                                for (int i = 0; i < methods.length; i++) {
                                    Log.e("hookmsg", methods[i] + "");
                                    if ("aBu".equals(methods[i].getName())) {
                                        ListView lv = (ListView) methods[i].invoke(o);
                                        Log.e("hookmsg", lv + ",lv.getcount:" + lv.getChildCount());
                                    }
                                }
                            }
                        });
                        XposedHelpers.findAndHookMethod(wxPackageName + ".plugin.sns.ui.SnsUploadUI", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                super.afterHookedMethod(param);
                                Object o = param.thisObject;
                                Class<?> c = findClass(wxPackageName + ".plugin.sns.ui.SnsUploadUI", lpparam.classLoader);
                                Field[] fields = c.getDeclaredFields();
                                Field.setAccessible(fields, true);
                                for (int i = 0; i < fields.length; i++) {
                                    Log.e("hookmsg", fields[i] + "");
                                    //fVt
                                    if (fields[i].getName().contains("fVt")) {
                                        EditText o1 = (EditText) fields[i].get(o);
                                        Log.e("hookmsg", o1.getText() + "");
//                                        o1.setText("哈哈哈哈");
                                    }
                                    if (fields[i].getName().contains("fVx")) {
                                        ArrayList arr = (ArrayList) fields[i].get(o);
                                        Log.e("hookmsg", arr.toString() + "");
                                    }
                                }
                            }
                        });
                    }
                });

                /*findAndHookMethod(wxPackageName + activitys[0], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[0]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[1], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[1]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[2], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[2]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[3], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[3]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[4], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[4]);
                    }
                });*/

                /*findAndHookMethod(wxPackageName + activitys[5], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[5]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[6], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[6]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[7], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[7]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[8], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[8]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[9], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[9]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[10], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[10]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[11], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[11]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[12], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[12]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[13], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[13]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[14], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[14]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[15], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[15]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[16], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[16]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[17], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[17]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[18], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[18]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[19], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[19]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[20], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[20]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[21], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[21]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[22], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[22]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[23], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[23]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[24], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[24]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[25], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[25]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[26], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[26]);
                    }
                });
                findAndHookMethod(wxPackageName + activitys[27], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[27]);
                    }
                });
                findAndHookMethod(activitys[28], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[27]);
                    }
                });
                findAndHookMethod(activitys[29], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[27]);
                    }
                });
                findAndHookMethod(activitys[30], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[27]);
                    }
                });
                findAndHookMethod(activitys[31], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[27]);
                    }
                });
                findAndHookMethod(activitys[32], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[27]);
                    }
                });
                findAndHookMethod(activitys[33], lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Log.e("hookmsg", "当前类：" + wxPackageName + activitys[27]);
                    }
                });*/
            }
        });

    }

    private void hookMethods(final LoadPackageParam lpparam) {
        //com.tencent.mm.protocal.b.atp  method:a
        findAndHookMethod(Config.PROTOCAL_SNS_DETAIL_CLASS, lpparam.classLoader, Config.PROTOCAL_SNS_DETAIL_METHOD, int.class, Object[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Log.e("msg", "微信hooked" + "");
                loadFromSharedPreference();
                if (!Config.enabled || !Config.ready) {
                    intervalSaveThread = null;
                    tweetList.clear();
                    return;
                }
                Class atp = XposedHelpers.findClass(Config.PROTOCAL_SNS_DETAIL_CLASS, lpparam.classLoader);//com.tencent.mm.protocal.b.atp
                Class Parser = XposedHelpers.findClass(Config.SNS_XML_GENERATOR_CLASS, lpparam.classLoader);//com.tencent.mm.plugin.sns.f.i
                Method parseMethod = Parser.getMethod(Config.SNS_XML_GENERATOR_METHOD, atp);//a
                Method[] methods = atp.getMethods();
                Field[] fields = atp.getFields();
                Method.setAccessible(methods, true);
                Field.setAccessible(fields, true);
                for (Method m : methods) {
                    Log.e("msg", "方法：" + m);
                }
                Log.e("msg", "-----------------------------------方法--------------------------");
                for (Field f : fields) {
                    Log.e("msg", "字段：" + f);
                    if ("iXW".equals(f.getName())) {
                        String s = (String) f.get(param.thisObject);
                        Log.e("msg", "字段：iXW的值：" + s);//12400335908834193661
                    }
                }
                Log.e("methodName", parseMethod + "");//com.tencent.mm.plugin.sns.f.i.a(com.tencent.mm.protocal.b.atp)
                try {
                    String result = (String) parseMethod.invoke(this, param.thisObject);
                    Log.e("msg", "结果：" + result + "");
                    if (!getTimelineId(result).equals(lastTimelineId))
                        currentTweet.clear();
                    parseTimelineXML(result);
                    addTweetToListNoRepeat();
                    lastTimelineId = getTimelineId(result);
                } catch (Exception e) {
                    Log.e("msg", "错误：" + e + "");
                }
                if (intervalSaveThread == null) {
                    intervalSaveThread = new IntervalThread(tweetList);
                    intervalSaveThread.start();
                }
            }
        });

        findAndHookMethod(Config.PROTOCAL_SNS_OBJECT_CLASS, lpparam.classLoader, Config.PROTOCAL_SNS_OBJECT_METHOD, int.class, Object[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                loadFromSharedPreference();
                if (!Config.enabled || !Config.ready) {
                    tweetList.clear();
                    return;
                }
                Object aqiObject = param.thisObject;
                parseSnsObject(aqiObject);
            }
        });
    }

    private void loadFromSharedPreference() {
        Log.e("loadsharedpreference", "load");
        XSharedPreferences pref = new XSharedPreferences(Main.class.getPackage().getName(), "config");
        pref.makeWorldReadable();
        pref.reload();
        Config.enabled = pref.getBoolean("enabled", false);
        Config.outputFile = pref.getString("outputFile", Environment.getExternalStorageDirectory() + "/moments_output.json");
    }

    private String getTimelineId(String xmlResult) {
        Pattern idPattern = Pattern.compile("<id><!\\[CDATA\\[(.+?)\\]\\]></id>");
        Matcher idMatcher = idPattern.matcher(xmlResult);
        if (idMatcher.find()) {
            return idMatcher.group(1);
        } else {
            return "";
        }
    }

    private void parseTimelineXML(String xmlResult) throws Throwable {
        Pattern userIdPattern = Pattern.compile("<username><!\\[CDATA\\[(.+?)\\]\\]></username>");
        Pattern contentPattern = Pattern.compile("<contentDesc><!\\[CDATA\\[(.+?)\\]\\]></contentDesc>", Pattern.DOTALL);
        Pattern mediaPattern = Pattern.compile("<media>.*?<url.*?><!\\[CDATA\\[(.+?)\\]\\]></url>.*?</media>");
        Pattern timestampPattern = Pattern.compile("<createTime><!\\[CDATA\\[(.+?)\\]\\]></createTime>");

        Matcher userIdMatcher = userIdPattern.matcher(xmlResult);
        Matcher contentMatcher = contentPattern.matcher(xmlResult);
        Matcher mediaMatcher = mediaPattern.matcher(xmlResult);
        Matcher timestampMatcher = timestampPattern.matcher(xmlResult);

        currentTweet.id = getTimelineId(xmlResult);

        currentTweet.rawXML = xmlResult;

        if (timestampMatcher.find()) {
            currentTweet.timestamp = Integer.parseInt(timestampMatcher.group(1));
        }

        if (userIdMatcher.find()) {
            currentTweet.authorId = userIdMatcher.group(1);
        }

        if (contentMatcher.find()) {
            currentTweet.content = contentMatcher.group(1);
        }

        while (mediaMatcher.find()) {
            boolean flag = true;
            for (int i = 0; i < currentTweet.mediaList.size(); i++) {
                if (currentTweet.mediaList.get(i).equals(mediaMatcher.group(1))) {
                    flag = false;
                    break;
                }
            }
            if (flag)
                currentTweet.mediaList.add(mediaMatcher.group(1));
        }

    }

    private void parseSnsObject(Object aqiObject) throws Throwable {
        Tweet matchTweet = null;
        Field field = null;
        Object userId = null, nickname = null;

        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_USERID_FIELD);
        userId = field.get(aqiObject);

        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_NICKNAME_FIELD);
        nickname = field.get(aqiObject);

        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_TIMESTAMP_FIELD);
        long snsTimestamp = ((Integer) field.get(aqiObject)).longValue();

        if (userId == null || nickname == null) {
            return;
        }

        for (int i = 0; i < tweetList.size(); i++) {
            Tweet tweet = tweetList.get(i);
            if (tweet.timestamp == snsTimestamp && tweet.authorId.equals((String) userId)) {
                matchTweet = tweet;
                break;
            }
        }

        if (matchTweet == null) {
            return;
        }

        matchTweet.ready = true;
        matchTweet.author = (String) nickname;
        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_COMMENTS_FIELD);
        LinkedList list = (LinkedList) field.get(aqiObject);
        for (int i = 0; i < list.size(); i++) {
            Object childObject = list.get(i);
            parseSnsObjectExt(childObject, true, matchTweet);
        }

        field = aqiObject.getClass().getField(Config.PROTOCAL_SNS_OBJECT_LIKES_FIELD);
        LinkedList likeList = (LinkedList) field.get(aqiObject);
        for (int i = 0; i < likeList.size(); i++) {
            Object likeObject = likeList.get(i);
            parseSnsObjectExt(likeObject, false, matchTweet);
        }
        matchTweet.print();

    }

    private void parseSnsObjectExt(Object apzObject, boolean isComment, Tweet matchTweet) throws Throwable {
        if (isComment) {
            Field field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_AUTHOR_NAME_FIELD);
            Object authorName = field.get(apzObject);

            field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_REPLY_TO_FIELD);
            Object replyToUserId = field.get(apzObject);

            field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_COMMENT_FIELD);
            Object commentContent = field.get(apzObject);

            field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_AUTHOR_ID_FIELD);
            Object authorId = field.get(apzObject);

            if (authorId == null || commentContent == null || authorName == null) {
                return;
            }

            for (int i = 0; i < matchTweet.comments.size(); i++) {
                Comment loadedComment = matchTweet.comments.get(i);
                if (loadedComment.authorId.equals((String) authorId) && loadedComment.content.equals((String) commentContent)) {
                    return;
                }
            }

            Comment newComment = new Comment();
            newComment.authorName = (String) authorName;
            newComment.content = (String) commentContent;
            newComment.authorId = (String) authorId;
            newComment.toUserId = (String) replyToUserId;

            for (int i = 0; i < matchTweet.comments.size(); i++) {
                Comment loadedComment = matchTweet.comments.get(i);
                if (replyToUserId != null && loadedComment.authorId.equals((String) replyToUserId)) {
                    newComment.toUser = loadedComment.authorName;
                    break;
                }
            }

            matchTweet.comments.add(newComment);
        } else {
            Field field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_AUTHOR_NAME_FIELD);
            Object nickname = field.get(apzObject);
            field = apzObject.getClass().getField(Config.SNS_OBJECT_EXT_AUTHOR_ID_FIELD);
            Object userId = field.get(apzObject);
            if (nickname == null || userId == null) {
                return;
            }

            if (((String) userId).equals("")) {
                return;
            }
            for (int i = 0; i < matchTweet.likes.size(); i++) {
                if (matchTweet.likes.get(i).userId.equals((String) userId)) {
                    return;
                }
            }
            Like newLike = new Like();
            newLike.userId = (String) userId;
            newLike.userName = (String) nickname;
            matchTweet.likes.add(newLike);
        }
    }

    private void addTweetToListNoRepeat() {
        if (currentTweet.id.equals("")) {
            return;
        }
        int replaceIndex = -1;
        for (int i = 0; i < tweetList.size(); i++) {
            Tweet loadedTweet = tweetList.get(i);
            if (loadedTweet.id.equals(currentTweet.id)) {
                replaceIndex = i;
                break;
            }
        }

        Tweet tweetToAdd = currentTweet.clone();
        if (replaceIndex == -1) {
            tweetList.add(tweetToAdd);
        } else {
            tweetList.remove(replaceIndex);
            tweetList.add(replaceIndex, tweetToAdd);
        }

    }

}
