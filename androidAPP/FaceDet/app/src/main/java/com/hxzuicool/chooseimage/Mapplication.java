package com.hxzuicool.chooseimage;

import android.app.Application;

/**
 * @author hxzuicool
 * 2020/12/11
 */
public class Mapplication extends Application {

    private static Mapplication mApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        mApplication = this;
    }

    public static Mapplication getContext() {
        return mApplication;
    }
}
