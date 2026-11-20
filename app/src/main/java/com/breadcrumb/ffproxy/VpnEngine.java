package com.breadcrumb.ffproxy;
public class VpnEngine {
    static { System.loadLibrary("vpn_packet_engine"); }
    public static native void startProxy(int tunFd);
    public static native void stopProxy();
}
