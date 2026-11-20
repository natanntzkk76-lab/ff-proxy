package com.breadcrumb.ffproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FreeFireVpnService extends VpnService {
    private static final String TAG = "FF_VPN";
    private ParcelFileDescriptor tunInterface;
    private Thread sendThread, receiveThread;
    private volatile boolean isRunning = true;
    private final Map<Integer, DatagramSocket> socketMap = new HashMap<>();
    private FileOutputStream outStream;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground();
        Builder builder = new Builder();
        builder.setSession("FF Proxy")
               .addAddress("10.0.0.2", 24)
               .addDnsServer("8.8.8.8")
               .addRoute("0.0.0.0", 0)
               .setMtu(1500);
        try {
            builder.addAllowedApplication("com.dts.freefireth");
            builder.addAllowedApplication("com.dts.freefiremax");
        } catch (PackageManager.NameNotFoundException e) {
            stopSelf(); return START_NOT_STICKY;
        }

        try {
            tunInterface = builder.establish();
            if (tunInterface == null) { stopSelf(); return START_NOT_STICKY; }
            outStream = new FileOutputStream(tunInterface.getFileDescriptor());
        } catch (Exception e) {
            stopSelf(); return START_NOT_STICKY;
        }

        sendThread = new Thread(this::runSendEngine);
        receiveThread = new Thread(this::runReceiveEngine);
        sendThread.start();
        receiveThread.start();

        return START_STICKY;
    }

    private void runSendEngine() {
        try (FileInputStream in = new FileInputStream(tunInterface.getFileDescriptor())) {
            byte[] buffer = new byte[65535];
            while (isRunning) {
                int length = in.read(buffer);
                if (length <= 0) continue;

                int ipVersion = (buffer[0] >> 4) & 0x0F;
                if (ipVersion != 4) continue;

                int ipHeaderLen = (buffer[0] & 0x0F) * 4;
                int protocol = buffer[9] & 0xFF;
                if (protocol != 17) continue; // UDP apenas

                int totalLen = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                int payloadLen = totalLen - ipHeaderLen - 8;
                if (payloadLen <= 0) continue;

                int srcPort = ((buffer[ipHeaderLen] & 0xFF) << 8) | (buffer[ipHeaderLen + 1] & 0xFF);
                int dstPort = ((buffer[ipHeaderLen + 2] & 0xFF) << 8) | (buffer[ipHeaderLen + 3] & 0xFF);
                
                int dstIpInt = ((buffer[16] & 0xFF) << 24) | ((buffer[17] & 0xFF) << 16) | 
                               ((buffer[18] & 0xFF) << 8) | (buffer[19] & 0xFF);
                String dstIp = ((dstIpInt >> 24) & 0xFF) + "." + ((dstIpInt >> 16) & 0xFF) + "." + 
                               ((dstIpInt >> 8) & 0xFF) + "." + (dstIpInt & 0xFF);

                byte[] payload = Arrays.copyOfRange(buffer, ipHeaderLen + 8, ipHeaderLen + 8 + payloadLen);
                sanitizePayload(payload);

                DatagramSocket socket = socketMap.computeIfAbsent(srcPort, k -> {
                    try { return new DatagramSocket(k); } catch (Exception e) { return null; }
                });

                if (socket != null) {
                    DatagramPacket outPacket = new DatagramPacket(payload, payload.length, InetAddress.getByName(dstIp), dstPort);
                    socket.send(outPacket);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Send engine error", e);
        }
    }

    private void runReceiveEngine() {
        byte[] buffer = new byte[65535];
        while (isRunning) {
            for (Map.Entry<Integer, DatagramSocket> entry : socketMap.entrySet()) {
                int localPort = entry.getKey();
                DatagramSocket socket = entry.getValue();
                try {
                    DatagramPacket inPacket = new DatagramPacket(buffer, buffer.length);
                    socket.setSoTimeout(50);
                    socket.receive(inPacket);
                    
                    byte[] respPayload = Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength());
                    InetAddress srcAddr = inPacket.getAddress();
                    int srcPortResp = inPacket.getPort();

                    // IP Header (20 bytes)
                    byte[] ipHeader = new byte[20];
                    ipHeader[0] = (byte) 0x45;
                    int totalRespLen = 20 + 8 + respPayload.length;
                    ipHeader[2] = (byte) ((totalRespLen >> 8) & 0xFF);
                    ipHeader[3] = (byte) (totalRespLen & 0xFF);
                    ipHeader[6] = 0x40; ipHeader[7] = 0x00;
                    ipHeader[8] = 0x40; // TTL 64
                    ipHeader[9] = 0x11; // UDP
                    byte[] srcIpBytes = srcAddr.getAddress();
                    System.arraycopy(srcIpBytes, 0, ipHeader, 12, 4);
                    ipHeader[16] = 10; ipHeader[17] = 0; ipHeader[18] = 0; ipHeader[19] = 2;

                    // UDP Header (8 bytes)
                    byte[] udpHeader = new byte[8];
                    udpHeader[0] = (byte) ((srcPortResp >> 8) & 0xFF);
                    udpHeader[1] = (byte) (srcPortResp & 0xFF);
                    udpHeader[2] = (byte) ((localPort >> 8) & 0xFF);
                    udpHeader[3] = (byte) (localPort & 0xFF);
                    int udpLen = 8 + respPayload.length;
                    udpHeader[4] = (byte) ((udpLen >> 8) & 0xFF);
                    udpHeader[5] = (byte) (udpLen & 0xFF);

                    // IP Checksum
                    short ipChecksum = checksum(ipHeader, 0, 20);
                    ipHeader[10] = (byte) ((ipChecksum >> 8) & 0xFF);
                    ipHeader[11] = (byte) (ipChecksum & 0xFF);

                    byte[] fullPacket = new byte[totalRespLen];
                    System.arraycopy(ipHeader, 0, fullPacket, 0, 20);
                    System.arraycopy(udpHeader, 0, fullPacket, 20, 8);
                    System.arraycopy(respPayload, 0, fullPacket, 28, respPayload.length);

                    synchronized (this) { outStream.write(fullPacket); }
                } catch (Exception e) { /* Timeout, ignorar */ }
            }
            try { Thread.sleep(5); } catch (Exception e) {}
        }
    }

    private short checksum(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            if (i + 1 < length) sum += ((data[offset + i] & 0xFF) << 8) | (data[offset + i + 1] & 0xFF);
            else sum += (data[offset + i] & 0xFF) << 8;
        }
        while ((sum >> 16) > 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (short) (~sum);
    }

    private void sanitizePayload(byte[] payload) {
        // Assinaturas de exemplo (re-derivar por build)
        if (payload.length >= 4 && payload[0] == 0x1A && payload[1] == 0x2B && payload[2] == 0x00 && payload[3] == 0x05) {
            if (payload.length > 12) payload[12] = 0x00; // Flag limpa
        }
        if (payload.length >= 4 && payload[0] == 0x1A && payload[1] == 0x2B && payload[2] == 0x00 && payload[3] == 0x12) {
            String cleanUuid = "00000000-0000-0000-0000-000000000000";
            if (payload.length >= 8 + cleanUuid.length()) {
                System.arraycopy(cleanUuid.getBytes(), 0, payload, 8, cleanUuid.length());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        for (DatagramSocket s : socketMap.values()) { if (s != null) s.close(); }
        socketMap.clear();
        try { if (outStream != null) outStream.close(); } catch (Exception e) {}
        try { if (tunInterface != null) tunInterface.close(); } catch (Exception e) {}
    }

    private void startForeground() {
        String channelId = "ff_proxy_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "FF Proxy", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("FF Proxy Active").setContentText("Sanitizing telemetry...")
                .setSmallIcon(android.R.drawable.ic_lock_lock).build();
        startForeground(1, notification);
    }
}
