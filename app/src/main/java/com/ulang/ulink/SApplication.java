package com.ulang.ulink;


import com.ulang.libulink.UApplication;
import com.ulang.libulink.utils.KLog;

public class SApplication extends UApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        KLog.init(true);
    }
}
