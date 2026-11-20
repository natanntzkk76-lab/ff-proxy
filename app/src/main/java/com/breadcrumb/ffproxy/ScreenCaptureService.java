package com.breadcrumb.ffproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;
    public static TargetListener targetListener;

    public interface TargetListener {
        void onTargetFound(int x, int y, int width, int height);
        void onTargetLost();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(2, createNotification());
        
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    processFrame(image);
                }
            } finally {
                if (image != null) image.close();
            }
        }, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("CODE")) {
            int resultCode = intent.getIntExtra("CODE", 0);
            Intent data = intent.getParcelableExtra("DATA");
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = manager.getMediaProjection(resultCode, data);
            
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "JuninCapture", screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null
            );
        }
        return START_STICKY;
    }

    private void processFrame(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        // Otimização: Varre apenas o centro da tela (região de mira) para performance
        int scanStartX = (screenWidth / 2) - 100;
        int scanEndX = (screenWidth / 2) + 100;
        int scanStartY = (screenHeight / 2) - 150;
        int scanEndY = (screenHeight / 2) + 150;

        int targetX = -1, targetY = -1;

        // COR ALVO: Vermelho (0xFFFF0000) - Ajustar conforme a cor do contorno do inimigo no FF
        // Tolerância simples para variar tons de vermelho
        for (int y = scanStartY; y < scanEndY; y++) {
            int rowOffset = y * rowStride;
            for (int x = scanStartX; x < scanEndX; x++) {
                int offset = rowOffset + x * pixelStride;
                int pixel = buffer.getInt(offset);
                
                // Extração de componentes RGB
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Detecção de cor vermelha predominante (inimigo)
                if (r > 180 && g < 100 && b < 100) {
                    targetX = x;
                    targetY = y;
                    break;
                }
            }
            if (targetX != -1) break;
        }

        if (targetX != -1 && targetListener != null) {
            targetListener.onTargetFound(targetX, targetY, 40, 60); // Box de 40x60 como exemplo
        } else if (targetListener != null) {
            targetListener.onTargetLost();
        }
    }

    private Notification createNotification() {
        String channelId = "junin_capture";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Capture", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        return new Notification.Builder(this, channelId)
                .setContentTitle("Junin Mods").setContentText("Engine externa ativa")
                .setSmallIcon(android.R.drawable.ic_menu_camera).build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (imageReader != null) imageReader.close();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
