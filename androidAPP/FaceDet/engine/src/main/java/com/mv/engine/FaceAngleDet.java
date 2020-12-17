package com.mv.engine;

/**
 * @author hxzuicool
 * 2020/11/12
 */
public class FaceAngleDet {

    static {
        System.loadLibrary("opencv_java3");
    }

    public static native int[] faceAngleDet(long matAddr);

}
