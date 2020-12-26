package com.hxzuicool.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;


public class ImgThread extends Thread {
    private static final String TAG = "ImgThread";
    private final String path;
    private ArrayList<String> imgPaths;
    private String imgStr;
    private final int upType;
    private boolean result = false;
    private String returnImgPath;
    private String userGroup;
    private Context context;

    /**
     * 岩心拼接上传
     * @param path 服务器端口地址
     * @param upType 上传图片的类型
     * @param imgPaths 岩心拼接的多张图片list
     */
    public ImgThread(String path, ArrayList<String> imgPaths, int upType, Context context) {
        this.path = path;
        this.imgPaths = imgPaths;
        this.upType = upType;
        this.context = context;
    }

    public ImgThread(String path, String imgStr, int upType, String userGroup) {
        this.path = path;
        this.imgStr = imgStr;
        this.upType = upType;
        this.userGroup = userGroup;
    }

    public ImgThread(String path, String imgStr, int upType, Context context) {
        this.path = path;
        this.imgStr = imgStr;
        this.upType = upType;
        this.context = context;
    }

    @Override
    public void run() {
        JSONObject jsonObject = null;
        StringBuilder resp = new StringBuilder();
        try {
            HttpURLConnection httpURLConnection = ImgService.httpConnection(path);
            DataOutputStream dos = new DataOutputStream(httpURLConnection.getOutputStream());
            switch (upType) {
                case 0:
                    String text0 = "--" + "---------------------------123821742118716" +
                            "\r\n" + "Content-Disposition: form-data; name=\"ugroup\"" +
                            "\r\n" + "Content-Type: text/plain; charset=\"utf-8\"" +
                            "\r\n" +
                            "\r\n" + userGroup +
                            "\r\n" +
                            "--" + "---------------------------123821742118716" +
                            "\r\n" + "Content-Disposition: form-data; name=\"vector\"" +
                            "\r\n" + "Content-Type: text/plain; charset=\"utf-8\"" +
                            "\r\n" +
                            "\r\n";
                    dos.write(text0.getBytes(StandardCharsets.UTF_8));
                    //写入图片数据
                    dos.write(imgStr.getBytes());
                    dos.write("\r\n".getBytes());
                    break;
                case 1:
                    String text1 = "--" + "---------------------------123821742118716" +
                            "\r\n" + "Content-Disposition: form-data; name=\"imagefile\"; filename=\"" + "test.jpg" + "\"" +
                            "\r\n" + "Content-Type: image/jpg; charset=\"utf-8\"" +
                            "\r\n" +
                            "\r\n";
                    dos.write(text1.getBytes());
                    dos.write(JavaImageUtil.getImgByte(imgStr));
                    dos.write("\r\n".getBytes());
                    break;
                case 2:
                case 3:
                    for (int i = 0; i < imgPaths.size(); i++) {
                        String text2 = "--" + "---------------------------123821742118716" +
                                "\r\n" + "Content-Disposition: form-data; name=\"imagefile\"; filename=\"" + "test" + i + ".jpg" + "\"" +
                                "\r\n" + "Content-Type: image/jpg; charset=\"utf-8\"" +
                                "\r\n" +
                                "\r\n";
                        dos.write(text2.getBytes());
                        dos.write(JavaImageUtil.getImgByte(imgPaths.get(i)));
                        dos.write("\r\n".getBytes());
                    }
                    break;
                default:
            }
            // 请求结束标志
            byte[] endData = ("--" + "---------------------------123821742118716" + "--" + "\r\n").getBytes();
            dos.write(endData);
            dos.flush();
            dos.close();
            long startTime = System.currentTimeMillis();
            result = (httpURLConnection.getResponseCode() == 200);
            Log.e("ImgThread", String.valueOf(httpURLConnection.getResponseCode()));
            if (result) {
                InputStream imgIs = httpURLConnection.getInputStream();
                long endTime = System.currentTimeMillis();
                Log.e(TAG, "服务器处理图像时间：" + (endTime - startTime) / 1000 + "秒");
                switch (upType) {
                    case 0:
                    case 3:
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(httpURLConnection.getInputStream()));
                        String line;
                        while ((line = in.readLine()) != null) {
                            resp.append(line);
                        }
                        in.close();
                        httpURLConnection.disconnect();
                        String imgjs = resp.toString().replaceAll("[\\[\\]]", "");
                        jsonObject = new JSONObject(imgjs);
                        jscontent.jsonObject = jsonObject;
                        break;
                    case 1:
                        Bitmap bitmap1 = BitmapFactory.decodeStream(imgIs);
                        returnImgPath = JavaImageUtil.saveImage(bitmap1, Environment.getExternalStorageDirectory() + "/CameraDemo/ImageReviseResult/", context);
                        httpURLConnection.disconnect();
                        imgIs.close();
                        break;
                    case 2:
                        Bitmap bitmap2 = BitmapFactory.decodeStream(imgIs);
                        returnImgPath = JavaImageUtil.saveImage(bitmap2, Environment.getExternalStorageDirectory() + "/CameraDemo/ImageJointResult/", context);
                        httpURLConnection.disconnect();
                        imgIs.close();
                        break;
                    default:
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 返回是否上传成功
     * @return
     */
    public boolean getResult() {
        return result;
    }

    /**
     * 返回保存的图片路径
     * @return
     */
    public String getReturnImgPath() {
        return returnImgPath;
    }

}
