package com.survey.cameraface;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class ImgThread extends Thread {
    private String path;
    private String img;
    private boolean result =false;

    public ImgThread(String path, String img){
        this.path = path;
        this.img = img;
    }

    @Override
    public void run(){
        try {
            URL url = new URL(path);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setConnectTimeout(8000);//设置连接超时时间
            httpURLConnection.setReadTimeout(8000);//设置读取超时时间
            httpURLConnection.setRequestMethod("POST");//设置请求方法,post

            String data = "username=" + URLEncoder.encode(img, "utf-8");//设置数据
            httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");//设置响应类型
            httpURLConnection.setRequestProperty("Content-Length", data.length() + "");//设置内容长度
            httpURLConnection.setDoOutput(true);//允许输出
            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(data.getBytes("utf-8"));//写入数据
            result = (httpURLConnection.getResponseCode() == 200);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean getResult() {
        return result;
    }

}
