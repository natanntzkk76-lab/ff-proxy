package com.breadcrumb.ffproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class OverlayService extends Service implements ScreenCaptureService.TargetListener {
    private WindowManager windowManager;
    private View overlayView;
    private Paint boxPaint;
    private int currentX = -1, currentY = -1, currentW = 40, currentH = 60;
    private boolean isVisible = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(3, createNotification());
        ScreenCaptureService.targetListener = this;
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setAntiAlias(true);

        createOverlay();
    }

    private void createOverlay() {
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        overlayView = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (isVisible && currentX != -1) {
                    // Desenha a caixa ESP
                    canvas.drawRect(currentX - (currentW/2), currentY - (currentH/2), 
                                    currentX + (currentW/2), currentY + (currentH/2), boxPaint);
                    
                    // Desenha linha de mira
                    Paint linePaint = new Paint();
                    linePaint.setColor(Color.RED);
                    linePaint.setStrokeWidth(2f);
                    canvas.drawLine(getWidth()/2, getHeight()/2, currentX, currentY, linePaint);
                }
                invalidate(); // Redesenha continuamente
            }
        };

        windowManager.addView(overlayView, params);
    }

    @Override
    public void onTargetFound(int x, int y, int w, int h) {
        currentX = x;
        currentY = y;
        currentW = w;
        currentH = h;
        isVisible = true;

        // Dispara o Aimbot via Accessibility Service
        Intent aimIntent = new Intent(this, JuninAccessibilityService.class);
        aimIntent.setAction("AIM");
        aimIntent.putExtra("X", x);
        aimIntent.putExtra("Y", y);
        sendBroadcast(aimIntent);
    }

    @Override
    public void onTargetLost() {
        isVisible = false;
    }

    private Notification createNotification() {
        String channelId = "junin_overlay";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Overlay", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        return new Notification.Builder(this, channelId)
                .setContentTitle("Junin Mods").setContentText("ESP/Aimbot ativo")
                .setSmallIcon(android.R.drawable.ic_menu_view).build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ScreenCaptureService.targetListener = null;
        if (overlayView != null) windowManager.removeView(overlayView);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
