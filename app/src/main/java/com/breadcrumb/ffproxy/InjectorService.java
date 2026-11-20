package com.breadcrumb.ffproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class InjectorService extends Service {
    private static final String TAG = "JUNIN_INJECTOR";
    private static final String FF_PACKAGE = "com.dts.freefireth";
    private static final String SO_PATH_LOCAL = "/data/local/tmp/junin_engine.so";
    private static final String INJECT_SCRIPT = 
        "PID=$(pidof " + FF_PACKAGE + ")\n" +
        "if [ -z \"$PID\" ]; then\n" +
        "  echo 'ERROR: Free Fire not running'\n" +
        "  exit 1\n" +
        "fi\n" +
        "echo \"Found FF PID: $PID\"\n" +
        "app_process -Djava.class.path=" + SO_PATH_LOCAL + " /system/bin " +
        "com.breadcrumb.ffproxy.InjectorMain $PID " + SO_PATH_LOCAL + "\n";

    public interface InjectCallback {
        void onSuccess(String output);
        void onError(String error);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(4, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "INJECT".equals(intent.getAction())) {
            executeInjection(new InjectCallback() {
                @Override
                public void onSuccess(String output) {
                    Log.i(TAG, "Injection success: " + output);
                    stopSelf();
                }
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Injection failed: " + error);
                    stopSelf();
                }
            });
        }
        return START_NOT_STICKY;
    }

    private void executeInjection(InjectCallback callback) {
        new Thread(() -> {
            try {
                // Step 1: Push the .so to /data/local/tmp via Shizuku
                String[] pushCmd = {
                    "cp /data/data/com.breadcrumb.ffproxy/lib/libmemory_engine.so " + SO_PATH_LOCAL,
                    "chmod 755 " + SO_PATH_LOCAL,
                    "ls -la " + SO_PATH_LOCAL
                };
                String pushResult = runShell(pushCmd);
                if (pushResult.contains("ERROR")) {
                    callback.onError("Failed to push .so: " + pushResult);
                    return;
                }

                // Step 2: Execute injection via app_process
                String injectResult = runShell(new String[]{INJECT_SCRIPT});
                if (injectResult.contains("ERROR")) {
                    callback.onError(injectResult);
                } else {
                    callback.onSuccess(injectResult);
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private String runShell(String[] commands) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh");
            ShizukuRemoteProcess process = Shizuku.newProcess(pb.command(), null, null);
            
            java.io.OutputStream os = process.getOutputStream();
            for (String cmd : commands) {
                os.write((cmd + "\n").getBytes());
            }
            os.write("exit\n".getBytes());
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            while ((line = errorReader.readLine()) != null) error.append(line).append("\n");

            process.waitFor();
            if (error.length() > 0) return "ERROR: " + error.toString();
            return output.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private Notification createNotification() {
        String channelId = "junin_injector";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Injector", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        return new Notification.Builder(this, channelId)
                .setContentTitle("Junin Mods").setContentText("Injetando engine no Free Fire...")
                .setSmallIcon(android.R.drawable.ic_lock_lock).build();
    }
}
