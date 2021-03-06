package com.cyb.wework.service;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.cyb.wework.R;
import com.cyb.wework.utils.AppUtil;
import com.cyb.wework.utils.LogUtil;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 辅助功能服务
 * Created by cyb on 2017/7/10.
 */
public class RedPacketService extends AccessibilityService {

    /**
     * 消息列表页面Activity类名
     */
    private static final String MessageList = "com.tencent.wework.msg.controller.MessageListActivity";
    /**
     * 红包页面Activity类名
     */
    private static final String RedEnvelope = "com.tencent.wework.enterprise.redenvelopes.controller.RedEnvelopeCollectorActivity";
    /**
     * 红包详情页面Activity类名
     */
    private static final String RedEnvelopeDetail = "com.tencent.wework.enterprise.redenvelopes.controller.RedEnvelopeDetailActivity";
    public static final String RED_ENVELOPE_WITH_COVER = "com.tencent.wework.enterprise.redenvelopes.controller.RedEnvelopeCollectorWithCoverActivity";
    public static final String RED_ENVELOPE_WITH_COVER_DETAIL = "com.tencent.wework.enterprise.redenvelopes.controller.RedEnvelopeDetailWithCoverActivity";

    private String currentActivity;

    private SharedPreferences sharedPreferences;
    public static void logViewHierarchy(AccessibilityNodeInfo nodeInfo, final int depth) {

        if (nodeInfo == null) return;

        String spacerString = "";

        for (int i = 0; i < depth; ++i) {
            spacerString += '-';
        }
        //Log the info you care about here... I choce classname and view resource name, because they are simple, but interesting.
        Log.d("TAG", spacerString + nodeInfo.getClassName() + " " + nodeInfo.getViewIdResourceName());

        for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
            logViewHierarchy(nodeInfo.getChild(i), depth+1);
        }
    }
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        LogUtil.d("RedPacketService onServiceConnected 企业微信红包助手已启动");
        Toast.makeText(this, "企业微信红包助手已启动", Toast.LENGTH_SHORT).show();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        LogUtil.d("event=" + event);
//        if (event.getRecordCount() > 0) {
//            logViewHierarchy(getRootInActiveWindow(), 0);
//        }

        switch (event.getEventType()) {
            //第一步：监听通知栏消息
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                if (getBooleanSetting("pref_watch_notification", true)) {
                    onNotificationStateChanged(event);
                }
                break;

            //第二步：监听是否进入微信红包消息界面
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                String activityName = event.getClassName().toString();
                currentActivity = activityName;
                if (checkOpenPacket()) {
                    openPacket(); // 开红包
                }
                LogUtil.d("activityName:" + activityName);
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                String className = event.getClassName().toString();
                LogUtil.d("className:" + className);

                if (MessageList.equals(currentActivity)) { // 消息列表
                    if (getBooleanSetting("pref_auto_click_msg", true)) {
                        queryPacket();
                    }
                } else if (checkOpenPacket()) {
                    openPacket(); // 开红包

                } else if (checkCloseDetail()) {
                    if (getBooleanSetting("pref_auto_close", true)) {
                        closeRedEnvelopeDetail(); // 关闭红包详情页面
                    }
                }
                break;
            default:
                break;
        }
    }

    boolean checkOpenPacket() {
        return RedEnvelope.equals(currentActivity) || RED_ENVELOPE_WITH_COVER.equals(currentActivity);
    }

    boolean checkCloseDetail() {
        return RedEnvelopeDetail.equals(currentActivity) || RED_ENVELOPE_WITH_COVER_DETAIL.equals(currentActivity);
    }

    /**
     * 通知状态改变时，判断是否有红包消息，有则模拟点击红包消息
     *
     * @param accessibilityEvent
     */
    private void onNotificationStateChanged(AccessibilityEvent accessibilityEvent) {
        LogUtil.d("RedPacketService TYPE_NOTIFICATION_STATE_CHANGED");
        List<CharSequence> textList = accessibilityEvent.getText();
        if (textList != null && textList.size() > 0) {
            for (CharSequence text : textList) {
                LogUtil.d("notification or toast text=" + text);
                String content = text.toString();

                String defaultKeyword = getResources().getString(R.string.notification_default_keyword); // 拼手气红包
                String keywords = sharedPreferences.getString("pref_notification_keyword", defaultKeyword);
                String[] keywordArray = keywords.split(";");
                for (String keyword : keywordArray) {
                    if (keyword != null && keyword.length() > 0) {
                        if (content.contains(keyword)) {
                            //模拟打开通知栏消息
                            Parcelable parcelableData = accessibilityEvent.getParcelableData();
                            if (parcelableData != null && parcelableData instanceof Notification) {
                                Notification notification = (Notification) parcelableData;
                                PendingIntent pendingIntent = notification.contentIntent;
                                try {
                                    pendingIntent.send();
                                } catch (PendingIntent.CanceledException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    /**
     * 关闭红包详情界面,实现自动返回聊天窗口
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void closeRedEnvelopeDetail() {
        LogUtil.d("关闭红包详情 closeRedEnvelopeDetail");
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            performGlobalAction(GLOBAL_ACTION_BACK); // 模拟按返回按钮
            //为了演示,直接查看了关闭按钮的id
//            List<AccessibilityNodeInfo> infos = nodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.wework:id/ce0");
//            LogUtil.d( "infos=" + infos);
//            nodeInfo.recycle();
//            for (AccessibilityNodeInfo item : infos) {
//                item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//            }
        }
    }

    /**
     * 模拟点击,拆开红包
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void openPacket() {
        LogUtil.d("拆开红包 openPacket");
        final AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {

            String haveBeenOpened = getResources().getString(R.string.red_packet_have_opened); // 手慢了，红包派完了
            String redPacketExpired = getResources().getString(R.string.red_packet_expired); // 红包已过期
            String redPackedGot = "红包已领取";
            String othersRedPackedGot = "对方已领取";
            List<AccessibilityNodeInfo> resultList = nodeInfo.findAccessibilityNodeInfosByText(haveBeenOpened);
            List<AccessibilityNodeInfo> resultList2 = nodeInfo.findAccessibilityNodeInfosByText(redPacketExpired);
            List<AccessibilityNodeInfo> resultList3 = nodeInfo.findAccessibilityNodeInfosByText(redPackedGot);
            List<AccessibilityNodeInfo> resultList4 = nodeInfo.findAccessibilityNodeInfosByText(othersRedPackedGot);
            LogUtil.d("手慢了，红包派完了 resultList=" + resultList.size());
            LogUtil.d("该红包已过期 resultList2=" + resultList2.size());
            LogUtil.d("红包已领取 resultList=" + resultList3.size());
            LogUtil.d("对方已领取 resultList=" + resultList4.size());
            // 判断红包是否已抢完，如已经抢完则自动关闭抢红包页面，如没有抢完则自动抢红包
            if (resultList.size() > 0 || resultList2.size() > 0 || resultList3.size() > 0 || resultList4.size() > 0) { // 红包已抢完
                LogUtil.d("红包已抢完或已失效");
                if (!getBooleanSetting("pref_auto_close", true)) {
                    return;
                }
                performGlobalAction(GLOBAL_ACTION_BACK); // 模拟按返回键
            } else {
                if (!getBooleanSetting("pref_auto_open", true)) {
                    return;
                }
                int delayMs = getIntegerSetting("pref_delay_ms", 0);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
//                        String viewId = getOpenBtnId(); // 获取已安装版本企业微信红包开按钮的Id
//                        if (!TextUtils.isEmpty(viewId)) {
//                            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(viewId);
//                            nodeInfo.recycle();
//                            for (AccessibilityNodeInfo item : list) {
//                                item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//                            }
//                        } else {
//                            LogUtil.d("open id is null");
//                        }
                        handleNode(nodeInfo);

                    }
                }, delayMs);
            }
        }
    }

    private void handleNode(AccessibilityNodeInfo node) {
        if (node.getChildCount() > 0) {
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child.isClickable()) {
                    LogUtil.d("viewId " + child.getViewIdResourceName());
                    LogUtil.d("detail child " + child);
                    if (child.getClassName().toString().contains("ImageView")) {
                        child.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        break;
                    }
                }
                handleNode(child);
            }
        }
    }

    private String getOpenBtnId() {
        String weworkVersion = AppUtil.getWeworkVersion(this);
        LogUtil.d("weworkVersion=" + weworkVersion);
        return "com.tencent.wework:id/drl";
    }

    /**
     * 在消息列表查找红包
     * 模拟点击,打开抢红包界面
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void queryPacket() {
        LogUtil.d("开始查找红包 queryPacket");
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        String searchText = getResources().getString(R.string.open_red_packet); // 领取红包
        AccessibilityNodeInfo node = getLastRedpackageNode(rootNode, searchText);
        LogUtil.d("最新的红包=" + node);

        if (node != null) {
            CharSequence charSequence = node.getText();
            String text = null;
            if (charSequence != null) {
                text = charSequence.toString();
            }
            LogUtil.d("hongbao text is " + text);
            if (!TextUtils.equals("红包", text)) {
                return;
            }
//            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            AccessibilityNodeInfo parent = null;
            while ((parent = node.getParent()) != null) {
                LogUtil.d("parentNode=" + parent);
                if (parent.isClickable()) {
                    for (int i = 0; i < parent.getChildCount(); i++) {
                        AccessibilityNodeInfo child = parent.getChild(i);
                        LogUtil.d("child text index " + i + child);
                    }
                    if (parent.getChildCount() == 2) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 查找包含指定字符串的在屏幕最下面的一个节点
     *
     * @param rootNode
     * @param search
     * @return
     */
    public AccessibilityNodeInfo getLastRedpackageNode(AccessibilityNodeInfo rootNode, String search) {
        AccessibilityNodeInfo resultNode = null;
        if (rootNode != null) {
            List<AccessibilityNodeInfo> nodeInfoList = rootNode.findAccessibilityNodeInfosByText(search);
//            LogUtil.d( "nodeInfoList=" + nodeInfoList);
            if (nodeInfoList != null && nodeInfoList.size() > 0) {
                int bottom = 0;
                for (AccessibilityNodeInfo node : nodeInfoList) {
                    if (node != null) {
                        final Rect rect = new Rect();
                        node.getBoundsInScreen(rect);
                        if (rect.bottom > bottom) {
                            resultNode = node;
                            bottom = rect.bottom;
                        }
                    }
                }
            }
        }
        return resultNode;
    }

    @Override
    public void onInterrupt() {
        LogUtil.d("RedPacketService onInterrupt 企业微信红包助手已停止");
    }

    private boolean getBooleanSetting(String key, boolean defaultValue) {
        if (sharedPreferences != null) {
            boolean value = sharedPreferences.getBoolean(key, defaultValue);
            LogUtil.d(key + "=" + value);
            return value;
        }
        return defaultValue;
    }

    private int getIntegerSetting(String key, int defaultValue) {
        if (sharedPreferences != null) {
            String delayTime = sharedPreferences.getString(key, "" + defaultValue);
            LogUtil.d(key + "=" + delayTime);
            try {
                return Integer.parseInt(delayTime);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }
}
