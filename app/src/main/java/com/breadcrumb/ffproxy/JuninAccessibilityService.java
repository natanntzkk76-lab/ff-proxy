package com.breadcrumb.ffproxy;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;

public class JuninAccessibilityService extends AccessibilityService {
    private BroadcastReceiver aimReceiver;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        aimReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("AIM".equals(intent.getAction()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    int x = intent.getIntExtra("X", 0);
                    int y = intent.getIntExtra("Y", 0);
                    performTap(x, y);
                }
            }
        };
        registerReceiver(aimReceiver, new IntentFilter("AIM"));
    }

    private void performTap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 50); // 50ms de toque
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }
        }, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (aimReceiver != null) unregisterReceiver(aimReceiver);
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override
    public void onInterrupt() {}
}
