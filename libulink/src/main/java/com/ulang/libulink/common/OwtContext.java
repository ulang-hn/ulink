package com.ulang.libulink.common;

import com.ulang.libulink.UApplication;

import org.webrtc.EglBase;

import owt.base.ContextInitialization;

public class OwtContext {
    private volatile static OwtContext INSTANCE = null;
    private final EglBase rootEglBase;

    public static OwtContext getInstance() {
        if (INSTANCE == null) {
            synchronized (OwtContext.class) {
                if (INSTANCE == null) {
                    INSTANCE = new OwtContext();
                }
            }
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }

    public OwtContext() {
        rootEglBase = EglBase.create();
        ContextInitialization.create()
                .setApplicationContext(UApplication.getInstance().getApplicationContext())
                .addIgnoreNetworkType(ContextInitialization.NetworkType.LOOPBACK)
                .setVideoHardwareAccelerationOptions(
                        rootEglBase.getEglBaseContext(),
                        rootEglBase.getEglBaseContext())
                .initialize();
    }
    public EglBase getRootEglBase() {
        return rootEglBase;
    }

}
