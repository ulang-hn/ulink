package com.ulang.ulink;


import android.app.Application;

import com.ulang.libulink.common.CameraCaptureConfiguration;
import com.ulang.libulink.common.Constants;
import com.ulang.libulink.p2p.P2PEngine;
import com.ulang.libulink.utils.KLog;

public class SApplication extends Application {
    private static P2PEngine p2PEngine;
    public void onCreate() {
        super.onCreate();
        CameraCaptureConfiguration cameraCaptureConfiguration = new CameraCaptureConfiguration(1280,720,30,true);
        p2PEngine = new P2PEngine.Builder().setServerUrl(Constants.P2P_BASE_URL)
                .setApplicationContext(this)
                .setCameraCaptureConfiguration(cameraCaptureConfiguration)
                .build();
        KLog.init(true);
    }

    public static P2PEngine getP2PEngine() {
        return p2PEngine;
    }
}
