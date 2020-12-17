package com.hxzuicool;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.hxzuicool.utils.JavaImageUtil;
import com.mv.engine.Live;
import com.mv.livebodyexample.R;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author: hxzuicool~
 * @date: 2020/10/20
 */
public class SharpnessDetActivity extends AppCompatActivity {
    private static final String TAG = "SharpnessDetection";
    private Handler mBackgroundHandler;          // 定义的子线程的处理器
    private HandlerThread mBackgroundThread;     // 处理拍照等工作的子线程
    private Handler mDetectorHandler;
    private HandlerThread mDetectorThread;
    private Paint paint;                         //画笔
    private Canvas canvas;
    private SurfaceHolder surfaceHolder;
    private static Range<Integer>[] fpsRanges;   // 相机的FPS范围
    private boolean isComputing = false;
    private double imgStd;                       //图像标准差
    private long matAddr;

    private TextureView tv;
    private SurfaceView sv;
    private String mCameraId = "0";//摄像头id（通常0代表后置摄像头，1代表前置摄像头）
    private final int RESULT_CODE_CAMERA = 1;//判断是否有拍照权限的标识码
    private CameraDevice cameraDevice;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest.Builder mCaptureRequestBuilder, captureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private ImageReader mImageReader;
    private int height, width;
    private Size previewSize;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @SuppressLint("SdCardPath")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate: ");
        setContentView(R.layout.activity_sharpness_detection);
        tv = findViewById(R.id.textureViewOfSharpnessDet);
        sv = findViewById(R.id.surfaceViewOfSharpnessDet);
        surfaceHolder = sv.getHolder();

        //surfaceView置于顶层
        sv.setZOrderOnTop(true);
        //surfaceView透明
        sv.getHolder().setFormat(PixelFormat.TRANSPARENT);
        //画框
        surfaceHolder = sv.getHolder();
        //画笔
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(60f);
        canvas = new Canvas();
        startBackgroundThread();
        startCameraThread();

        // 通过Activity类中的getWindowManager()方法获取窗口管理，再调用getDefaultDisplay()方法获取获取Display对象
        Display display = getWindowManager().getDefaultDisplay();
        //使用Point来保存屏幕宽、高两个数据
        Point phoneSize = new Point();
        // 通过Display对象获取屏幕宽、高数据并保存到Point对象中
        display.getSize(phoneSize);
        // 从Point对象中获取宽、高
        int x = phoneSize.x;
        int y = phoneSize.y;
        System.out.println("手机像素：" + x + " " + y);
        width = x;
        height = y;

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e(TAG, "onStart: ");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume: ");
        startCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBackgroundThread();
        stopCameraThread();
        Log.e(TAG, "onPause: ");
        if (cameraDevice != null) {
            stopCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy: ");
    }

    /**
     * TextureView的监听
     */
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        //可用
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "onSurfaceTextureAvailable: 摄像头开启");
            System.out.println("textureView的宽:" + tv.getWidth() + " 高: " + tv.getHeight());
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        //释放
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.e(TAG, "onSurfaceTextureDestroyed: ");
            stopCamera();
            return true;
        }

        //更新
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
//--------------------------------------------------------------------------------------------------------------------

    /**
     * 开启子线程
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * 停止子线程
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startCameraThread() {
        mDetectorThread = new HandlerThread("DetectorThread");
        mDetectorThread.start();
        mDetectorHandler = new Handler(mDetectorThread.getLooper());
    }

    private void stopCameraThread() {
        mDetectorThread.quitSafely();
        try {
            mDetectorThread.join();
            mDetectorThread = null;
            mDetectorHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    int i = 1;
    private final static int EXECUTION_FREQUENCY = 3;
    private int PREVIEW_RETURN_IMAGE_COUNT;
    //监听到每一帧图片
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            // 设置识别的频率，当EXECUTION_FREQUENCY为5时，也就是此处被回调五次只识别一次
            // 假若帧率被我设置在15帧/s，那么就是 1s 识别 3次，若是30帧/s，那就是1s识别6次，以此类推
            // 测试机一加3大概是15帧左右，所以 1s 识别 5 次
            PREVIEW_RETURN_IMAGE_COUNT++;
            if (PREVIEW_RETURN_IMAGE_COUNT % EXECUTION_FREQUENCY != 0) {
                return;
            }
            PREVIEW_RETURN_IMAGE_COUNT = 0;
            //获取队列中最新一帧的图像，将旧的丢掉
            final Image image = imageReader.acquireLatestImage();
            if (image != null && i == 1) {
                System.out.println("image宽高:" + image.getWidth() + " " + image.getHeight());
                i++;
            }
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (image == null) {
                        Log.e(TAG, "读取到图片为空，返回...");
                        return;
                    }
                    if (isComputing) {
                        image.close();
                        return;
                    }
                    isComputing = true;
                    mImage = image;

//                    rgba = new JavaCamera2Frame().rgba();
                    Mat rgba = new JavaImageUtil().rgba(image);
                    matAddr = rgba.getNativeObjAddr();
                    imgStd = new Live().stdDet(matAddr);
                    System.out.println(imgStd);
                    mDetectorHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            System.out.println(Environment.getExternalStorageDirectory().getPath());
                        }
                    });
                    drawText();
                    image.close();
                    isComputing = false;
                    rgba.release();
                    System.gc();
                }
            });
        }
    };

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        System.out.println("调用finalize()");
    }

    private void drawText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ClearDraw();
                canvas = surfaceHolder.lockCanvas();
                canvas.drawText("清晰度值：" + imgStd, 10, 1550, paint);
                if (imgStd > 15) {
                    canvas.drawText("图像清晰！", 10, 1600, paint);
                } else {
                    canvas.drawText("图像不清晰！", 10, 1600, paint);
                }
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        });
    }

    /**
     * 清空上次的框
     */
    private void ClearDraw() {
        try {
            canvas = surfaceHolder.lockCanvas(null);
            canvas.drawColor(Color.WHITE);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private Image mImage;
    /**
     * 有问题↓
     * private Mat mRgba;
     */
    private Mat mGray;

    private class JavaCamera2Frame implements CameraBridgeViewBase.CvCameraViewFrame {
        Mat mRgba = new Mat();

        @Override
        public Mat gray() {
            Image.Plane[] planes = mImage.getPlanes();
            int w = mImage.getWidth();
            int h = mImage.getHeight();
            assert (planes[0].getPixelStride() == 1);
            ByteBuffer y_plane = planes[0].getBuffer();
            int y_plane_step = planes[0].getRowStride();
            mGray = new Mat(h, w, CvType.CV_8UC1, y_plane, y_plane_step);
            return mGray;
        }

        @Override
        public Mat rgba() {
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

    /**
     * 打开摄像头
     */
    private void openCamera() {
        Log.e(TAG, "openCamera: ");
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //设置摄像头特性
        setCameraCharacteristics(manager);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                //提示用户开启权限
                String[] perms = {"android.permission.CAMERA"};
                ActivityCompat.requestPermissions(SharpnessDetActivity.this, perms, RESULT_CODE_CAMERA);
            } else {
                manager.openCamera(mCameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * 设置摄像头的参数
     */
    private void setCameraCharacteristics(CameraManager manager) {
        Log.e(TAG, "setCameraCharacteristics: ");
        try {
            // 获取指定摄像头的特性,设置前后置摄像头.
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            // 获取摄像头支持的配置属性
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // 获取摄像头支持的最大尺寸
            assert map != null;
            Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
            System.out.println(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)));
            // 创建一个ImageReader对象，用于获取摄像头的图像数据
//            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
//                    ImageFormat.YUV_420_888, 3);
            // 获取最佳的预览尺寸
//            previewSize = CameraUtils.getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);
//            Size[] choosePhotoSize = new SharpnessDetection().choosePhotoSize(map.getOutputSizes(ImageFormat.YUV_420_888),tv);
//            previewSize = new SharpnessDetection().chooseLargestSize(choosePhotoSize);


//            mImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
//                    ImageFormat.YUV_420_888, 2);
            Log.e(TAG, "width:" + width + "  height:" + height + "   previewSize.width:" + previewSize.getWidth() + "  previewSize.height:" + previewSize.getHeight());
            mImageReader = ImageReader.newInstance(height, width, ImageFormat.YUV_420_888, 2);
            //设置获取图片的监听
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            //得到相机帧率范围
            fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            System.out.println("相机帧率范围： " + Arrays.toString(fpsRanges));

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Log.e(TAG, e.toString());
        }
    }

    private static Size chooseOptimalSize(Size[] choices
            , int width, int height, Size aspectRatio) {
        // 收集摄像头支持的大过预览Surface的分辨率
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        // 如果找到多个预览尺寸，获取其中面积最小的
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new SharpnessDetActivity.CompareSizesByArea());
        } else {
            //没有合适的预览尺寸
            return choices[0];
        }
    }

    // 为Size定义一个比较器Comparator
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // 强转为long保证不会发生溢出
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /**
     * 摄像头状态的监听
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        // 摄像头被打开时触发该方法
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            SharpnessDetActivity.this.cameraDevice = cameraDevice;
            // 开始预览
            textureViewTakePreview();
        }

        // 摄像头断开连接时触发该方法
        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            SharpnessDetActivity.this.cameraDevice.close();
            SharpnessDetActivity.this.cameraDevice = null;
        }

        // 打开摄像头出现错误时触发该方法
        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            SharpnessDetActivity.this.cameraDevice.close();
        }
    };

    /**
     * 开始预览
     */
    private void textureViewTakePreview() {
        Log.e(TAG, "textureViewTakePreview: 开始预览...");
        SurfaceTexture mSurfaceTexture = tv.getSurfaceTexture();
        assert mSurfaceTexture != null;
        //调用前置摄像头的话就把textureView左右镜像
        if ("1".equals(mCameraId)) {
            tv.setScaleX(-1);
        }
        //设置TextureView的缓冲区大小,height和width是手机屏幕物理像素大小，全局变量
        mSurfaceTexture.setDefaultBufferSize(height, width);
        //获取Surface显示预览数据
        Surface mSurface = new Surface(mSurfaceTexture);
        try {
            //创建预览请求
            mCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 设置自动对焦模式
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //设置最大帧率
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRanges[fpsRanges.length - 1]);
            //设置Surface作为预览数据的显示界面
            mCaptureRequestBuilder.addTarget(mSurface);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());

            //把ImageReader的surface添加给CaptureRequest.Builder，使预览surface和ImageReader同时收到数据回调。
            //这里有点问题！！！！！！！！！！！！！！！！！！！！
            //mCaptureRequestBuilder.addTarget(mImageReader.getSurface());

            // 创建相机捕获会话，第一个参数是捕获数据的输出Surface列表，第二个参数是CameraCaptureSession的状态回调接口，当它创建好后会回调onConfigured方法，
            // 第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
            cameraDevice.createCaptureSession(Arrays.asList(mSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        //开始预览
                        mCaptureRequest = mCaptureRequestBuilder.build();
                        mPreviewSession = session;
                        //设置反复捕获数据的请求，这样预览界面就会一直有数据显示
                        mPreviewSession.setRepeatingRequest(mCaptureRequest, null, mBackgroundHandler);

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int permsRequestCode, String[] permissions, int[] grantResults) {
        switch (permsRequestCode) {
            case RESULT_CODE_CAMERA:
                boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (cameraAccepted) {
                    //授权成功之后，调用系统相机进行拍照操作等
                    openCamera();
                } else {
                    //用户授权拒绝之后，友情提示一下
                    Toast.makeText(SharpnessDetActivity.this, "请开启应用拍照权限", Toast.LENGTH_SHORT).show();
                }
                break;
            default:

        }
    }

    /**
     * 启动拍照
     */
    private void startCamera() {
        if (tv.isAvailable()) {
            if (cameraDevice == null) {
                openCamera();
            }
        } else {
            tv.setSurfaceTextureListener(surfaceTextureListener);


        }
    }

    /**
     * 停止拍照释放资源
     */
    private void stopCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

    }
}
