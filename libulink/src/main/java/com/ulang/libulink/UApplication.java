package com.ulang.libulink;

import android.app.Application;

import com.ulang.libulink.common.OwtContext;
import com.ulang.libulink.utils.KLog;

public class UApplication extends Application {
    private static Application sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        setApplication(this);
        //初始化
        OwtContext.getInstance();
    }

    /**
     * 获得当前app运行的Application
     */
    public static Application getInstance() {
        KLog.e(sInstance);
        if (sInstance == null) {
            throw new NullPointerException("please inherit BaseApplication or call setApplication.");
        }
        return sInstance;
    }

    public static synchronized void setApplication(Application application) {
        sInstance = application;
    }
}
