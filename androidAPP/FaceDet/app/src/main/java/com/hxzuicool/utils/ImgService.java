package com.hxzuicool.utils;


import android.content.Context;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * @author hxzuicool
 */
public class ImgService {
    /**
     * 找到Java的ByteArray类型
     * @param imgStr 图片的地址
     * @param upType 上传图片的类型选择
     * @return 返回结果和服务器处理图片（如果有的话）
     */
    public static String[] uploadImg(String imgStr, int upType, Context context) {

        String[] pathRoute = {"http://47.108.134.136:5256/face/query", "http://47.108.134.136:5256/rock/correct"};
        // 写上传图片的方法
        ImgThread imgThread = new ImgThread(pathRoute[upType], imgStr, upType, context);
        try{
            imgThread.start();
            imgThread.join();
        } catch (InterruptedException e){
            System.out.println("uploadImg1");
            e.printStackTrace();
        }
        String[] str = new String[2];
        if (imgThread.getResult()) {
            str[0] = "true";
        } else {
            str[0] = "false";
        }
        str[1] = imgThread.getReturnImgPath();
        return str;
    }

    /**
     * 上传岩心拼接和批量岩心校正
     * @param mImagePaths 相册选择图片返回
     * @param upType 上传类型
     * @param context Activity context
     * @return 返回结果和服务器处理图片（如果有的话）
     */
    public static String[] uploadImg(ArrayList<String> mImagePaths, int upType, Context context) {

        String[] pathRoute = {"", "", "http://47.108.134.136:5256/rock/split", "http://47.108.134.136:5256/rock/batch_correct"};
        ImgThread imgThread = new ImgThread(pathRoute[upType], mImagePaths, upType, context);
        imgThread.start();
        try {
            imgThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String[] str = new String[2];
        if (imgThread.getResult()) {
            str[0] = "true";
        } else {
            str[0] = "false";
        }
        str[1] = imgThread.getReturnImgPath();
        return str;
    }

    /**
     * 上传人脸向量
     * @param faceVector 人脸128维向量组成的字符串，逗号隔开
     * @return
     */
    public static boolean uploadFaceVector(String faceVector, String userGroup) {
        String pathroute = ("http://47.108.134.136:5256/face/verify");
        // 写上传图片的方法
        ImgThread imgThread = new ImgThread(pathroute, faceVector,0, userGroup);
        try{
            imgThread.start();
            imgThread.join();
        } catch (InterruptedException e){
            e.printStackTrace();
        }
        return imgThread.getResult();
    }

    /**
     * 建立服务器连接
     * @param uri 服务器连接地址
     * @return
     * @throws IOException
     */
    public static HttpURLConnection httpConnection(String uri) throws IOException {
        URL url = new URL(uri);
        HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
        httpUrlConnection.setConnectTimeout(8000);
        //设置连接超时时间
        httpUrlConnection.setDoOutput(true);
        //允许输出
        httpUrlConnection.setDoOutput(true);
        //允许输入
        httpUrlConnection.setUseCaches(false);
        //使用Post方式不能使用缓存
        httpUrlConnection.setRequestMethod("POST");
        //设置请求方法,post
        httpUrlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + "---------------------------123821742118716");
        //设置响应类型
        return httpUrlConnection;
    }
}
