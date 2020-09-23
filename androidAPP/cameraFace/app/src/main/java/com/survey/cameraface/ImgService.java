package com.survey.cameraface;



public class ImgService {
    //找到Java的ByteArray类型
    public static boolean UploadImg(byte img[]) {
        //ByteArray转字符串
        String imgStr = new String(img);
        // 写上传图片的方法
        ImgThread imgThread = new ImgThread("http://223.128.83.45:8081/BOOKCITY/loginServlet?action=login", imgStr);
        try{
            imgThread.start();
            imgThread.join();
        } catch (InterruptedException e){
            e.printStackTrace();
        }
        return imgThread.getResult();
    }

}
