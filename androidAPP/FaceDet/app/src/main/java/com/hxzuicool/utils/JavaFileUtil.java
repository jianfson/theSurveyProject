package com.hxzuicool.utils;

import android.graphics.Bitmap;

import android.graphics.Matrix;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * @author: hxzuicool~
 * @date: 2020/10/26
 */
public class JavaFileUtil {

    private static final String TAG = "JavaFileUtil";
    private static int iFileName;
    /**
     * 根据byte数组，生成文件
     */
    public static void getFile(byte[] bfile) {
        String filePath = Environment.getExternalStorageDirectory().getPath() + "/hxzuicool";
        //通过Random() 类生成数组命名
        Random random = new Random();
        String fileName = String.valueOf(random.nextInt(Integer.MAX_VALUE));
        BufferedOutputStream bos = null;
        FileOutputStream fos = null;
        File file = null;
        try {
            File dir = new File(filePath);
            if (!dir.exists() && dir.isDirectory()) {
                dir.mkdirs();
            }
            file = new File(filePath + "/" + fileName + ".jpg");
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(bfile);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    /**
     * 保存bitmap到本地
     * @param bitmap
     * @return
     */
    public static String saveBitmap(Bitmap bitmap){
        Matrix m = new Matrix();
        m.setRotate(270, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
        Bitmap bm = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

        //通过Random()类生成数组命名
        Random random = new Random();
        iFileName = random.nextInt(Integer.MAX_VALUE);
        String fileName = String.valueOf(iFileName);
        String dir = Environment.getExternalStorageDirectory().getPath() + "/hxzuicool/";
        File rootFile = new File(Environment.getExternalStorageDirectory().getPath() + "/hxzuicool");
        if (!rootFile.exists()) {
            rootFile.mkdirs();
        }
        try {
            File file = new File(dir + fileName + ".jpg");
            System.out.println(file.getAbsolutePath());
            FileOutputStream out = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, out);
            Log.e(TAG,"成功保存，路径：" + file.getPath());
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dir+fileName+".jpg";
    }

    public static int getiFileName(){
        return iFileName;
    }
}
