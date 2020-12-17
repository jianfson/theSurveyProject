package com.hxzuicool.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Environment;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.opencv.core.CvType.CV_64F;

/**
 * @author: hxzuicool~
 * @date: 2020/10/26
 */
public class JavaImageUtil {


    private static final String TAG = "JavaImageUtil";

    /**
     * 返回文件名称
     * @return
     */
    public static String createFileName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File file = new File(Environment.getExternalStorageDirectory().getPath() + "/hxzuicool");
        if (!file.exists()){
            file.mkdirs();
        }
        return file.getAbsolutePath() + "/JEPG_" + timeStamp + ".jpg";
    }

    /**
     * 获取Mat类型图片的内存地址
     * @param imgPath 图片本地储存地址
     * @return 返回Mat类型图片内存地址
     */
    public static long getMatAddr(String imgPath) {
        Mat img = Imgcodecs.imread(imgPath);
        return img.getNativeObjAddr();
    }

    /**
     * 旋转图片
     * @param bitmap 输入为bitmap格式图片
     * @return
     */
    public static Bitmap rotateBitmap(Bitmap bitmap) {
        Matrix m = new Matrix();
        m.setRotate(270, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    /**
     * 人脸图像清晰度检测
     * @param matImg
     * @return
     */
    public static boolean isFaceSharpnessDet(Mat matImg) {

        Mat imgLaplacian = matImg.clone();
        Imgproc.Laplacian(matImg, imgLaplacian, CV_64F);
        MatOfDouble mu = new MatOfDouble();
        MatOfDouble sigma = new MatOfDouble();
        Core.meanStdDev(imgLaplacian, mu, sigma);
        double imgStd = sigma.get(0, 0)[0];
        Log.e(TAG, String.valueOf(imgStd));
        return imgStd > 12;
    }

    /**
     * 岩心图像清晰度检测
     * @param imgPath
     * @return
     */
    public static boolean isStoneSharpnessDet(String imgPath) {

        Mat imreadimg = Imgcodecs.imread(imgPath);
        Mat imgLaplacian = imreadimg.clone();
        Imgproc.Laplacian(imreadimg, imgLaplacian, CV_64F);
        MatOfDouble mu = new MatOfDouble();
        MatOfDouble sigma = new MatOfDouble();
        Core.meanStdDev(imgLaplacian, mu, sigma);
        double imgStd = sigma.get(0, 0)[0];
        Log.e(TAG, "isStoneSharpnessDet: " + imgStd);
        return imgStd > 12;
    }

    public static String saveImage(Bitmap bitmap, String imgPath) throws IOException {

        File file = new File(imgPath);
        if (!file.exists()){
            file.mkdirs();
        }
        String imageNameStr = String.valueOf(System.currentTimeMillis());
        FileOutputStream outputStream = new FileOutputStream(imgPath + imageNameStr + ".jpg");
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        outputStream.flush();
        outputStream.close();
        return imgPath + imageNameStr + ".jpg";
    }
    /**
     * 获得图片byte数组
     * @param imgPath
     * @return
     */
    public static byte[] getImgByte(String imgPath) {
        File imageFile = new File(imgPath);
        byte[] imageData = null;
        // 读取图片字节数组
        try {
            InputStream in = new FileInputStream(imageFile);
            imageData = new byte[in.available()];
            in.read(imageData);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageData;
    }

    /**
     * Image类型转Mat
     * @param mImage
     * @return
     */
    public Mat rgba(Image mImage) {
        Mat mRgba = new Mat();

        Image.Plane[] planes = mImage.getPlanes();
        int w = mImage.getWidth();
        int h = mImage.getHeight();
        int chromaPixelStride = planes[1].getPixelStride();

// Chroma channels are interleaved
        if (chromaPixelStride == 2) {
            assert (planes[0].getPixelStride() == 1);
            assert (planes[2].getPixelStride() == 2);
            ByteBuffer y_plane = planes[0].getBuffer();
            int y_plane_step = planes[0].getRowStride();
            ByteBuffer uv_plane1 = planes[1].getBuffer();
            int uv_plane1_step = planes[1].getRowStride();
            ByteBuffer uv_plane2 = planes[2].getBuffer();
            int uv_plane2_step = planes[2].getRowStride();
            Mat y_mat = new Mat(h, w, CvType.CV_8UC1, y_plane, y_plane_step);
            Mat uv_mat1 = new Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane1, uv_plane1_step);
            Mat uv_mat2 = new Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane2, uv_plane2_step);
            long addr_diff = uv_mat2.dataAddr() - uv_mat1.dataAddr();
            if (addr_diff > 0) {
                assert (addr_diff == 1);
                Imgproc.cvtColorTwoPlane(y_mat, uv_mat1, mRgba, Imgproc.COLOR_YUV2RGBA_NV12);
            } else {
                assert (addr_diff == -1);
                Imgproc.cvtColorTwoPlane(y_mat, uv_mat2, mRgba, Imgproc.COLOR_YUV2RGBA_NV21);
            }
            return mRgba;
        } else { // Chroma channels are not interleaved
            byte[] yuv_bytes = new byte[w * (h + h / 2)];
            ByteBuffer y_plane = planes[0].getBuffer();
            ByteBuffer u_plane = planes[1].getBuffer();
            ByteBuffer v_plane = planes[2].getBuffer();

            int yuv_bytes_offset = 0;

            int y_plane_step = planes[0].getRowStride();
            if (y_plane_step == w) {
                y_plane.get(yuv_bytes, 0, w * h);
                yuv_bytes_offset = w * h;
            } else {
                int padding = y_plane_step - w;
                for (int i = 0; i < h; i++) {
                    y_plane.get(yuv_bytes, yuv_bytes_offset, w);
                    yuv_bytes_offset += w;
                    if (i < h - 1) {
                        y_plane.position(y_plane.position() + padding);
                    }
                }
                assert (yuv_bytes_offset == w * h);
            }

            int chromaRowStride = planes[1].getRowStride();
            int chromaRowPadding = chromaRowStride - w / 2;

            if (chromaRowPadding == 0) {
                // When the row stride of the chroma channels equals their width, we can copy
                // the entire channels in one go
                u_plane.get(yuv_bytes, yuv_bytes_offset, w * h / 4);
                yuv_bytes_offset += w * h / 4;
                v_plane.get(yuv_bytes, yuv_bytes_offset, w * h / 4);
            } else {
                // When not equal, we need to copy the channels row by row
                for (int i = 0; i < h / 2; i++) {
                    u_plane.get(yuv_bytes, yuv_bytes_offset, w / 2);
                    yuv_bytes_offset += w / 2;
                    if (i < h / 2 - 1) {
                        u_plane.position(u_plane.position() + chromaRowPadding);
                    }
                }
                for (int i = 0; i < h / 2; i++) {
                    v_plane.get(yuv_bytes, yuv_bytes_offset, w / 2);
                    yuv_bytes_offset += w / 2;
                    if (i < h / 2 - 1) {
                        v_plane.position(v_plane.position() + chromaRowPadding);
                    }
                }
            }

            Mat yuv_mat = new Mat(h + h / 2, w, CvType.CV_8UC1);
            yuv_mat.put(0, 0, yuv_bytes);
            Imgproc.cvtColor(yuv_mat, mRgba, Imgproc.COLOR_YUV2RGBA_I420, 4);
            return mRgba;
        }
    }
}

