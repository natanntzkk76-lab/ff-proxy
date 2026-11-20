package com.breadcrumb.ffproxy;

import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class ShizukuHelper {
    public interface ShizukuCallback {
        void onSuccess(String output);
        void onError(String error);
    }

    public static void executeShellCommand(String[] commands, ShizukuCallback callback) {
        new Thread(() -> {
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
                if (error.length() > 0) {
                    callback.onError(error.toString());
                } else {
                    callback.onSuccess(output.toString());
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
