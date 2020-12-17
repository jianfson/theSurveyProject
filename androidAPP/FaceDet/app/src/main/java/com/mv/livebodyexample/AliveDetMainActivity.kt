@file:Suppress("DEPRECATION")

package com.mv.livebodyexample

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.hxzuicool.utils.ImgService
import com.hxzuicool.utils.JavaImageUtil
import com.hxzuicool.utils.jscontent
import com.hxzuicool.utils.toast
import com.mv.engine.FaceAngleDet
import com.mv.engine.Live
import com.mv.livebodyexample.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import org.opencv.android.Utils
import org.opencv.core.CvType.CV_8UC3
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.IOException

@ObsoleteCoroutinesApi
class AliveDetMainActivity : AppCompatActivity(),
    SetThresholdDialogFragment.ThresholdDialogListener {

    private var mActivity: Activity = this
    private var mAngleDetHandler: Handler? = null
    private var mAngleDetThread: HandlerThread? = null
    private var matAddr: Long = 0
    private var matImg: Mat? = null

    var isUpload = false
    var breakFace = false
    var currentTimeMillis1: Long = 0
    var isComputing = false
    var isFaceAngleComputing = false
    var isAngleDetPass = false

    // 统计识别时间，如果超过30s还未识别到人脸，自动退出
    var faceDetTime: Long = 0

    private lateinit var binding: ActivityMainBinding
    private var enginePrepared: Boolean = false
    private lateinit var engineWrapper: EngineWrapper
    private var threshold: Float = defaultThreshold

    private var camera: Camera? = null
    private var cameraId: Int = Camera.CameraInfo.CAMERA_FACING_FRONT
    private val previewWidth: Int = 1920
    private val previewHeight: Int = 1080

    private val frameOrientation: Int = 7

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var factorX: Float = 0F
    private var factorY: Float = 0F

    private val detectionContext = newSingleThreadContext("detection")
    private var working: Boolean = false

    private lateinit var scaleAnimator: ObjectAnimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isAnglePass = findViewById<TextView>(R.id.isAnglePass)
        val faceAngleText = findViewById<TextView>(R.id.faceAngleText)
        val faceDetRemind = findViewById<TextView>(R.id.faceDetRemind)
        if (hasPermissions()) {
            startAngleDetThread()
            init()
        } else {
            requestPermission()
        }

    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }


    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermission() = requestPermissions(permissions, permissionReqCode)


    private fun startAngleDetThread() {
        mAngleDetThread = HandlerThread("DetectorThread")
        mAngleDetThread!!.start()
        mAngleDetHandler = Handler(mAngleDetThread!!.getLooper())
    }

    private fun stopCameraThread() {
        mAngleDetThread?.quitSafely()
        try {
            mAngleDetThread?.join()
            mAngleDetThread = null
            mAngleDetHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    var pWidth: Int = 0
    var pHeight: Int = 0

    private fun init() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.result = DetectionResult()

        calculateSize()

        binding.surface.holder.let {
            it.setFormat(ImageFormat.NV21)
            it.addCallback(object : SurfaceHolder.Callback, Camera.PreviewCallback {
                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    if (holder?.surface == null) return

                    if (camera == null) return

                    try {
                        camera?.stopPreview()
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    }

                    val parameters = camera?.parameters
                    parameters?.setPreviewSize(previewWidth, previewHeight)

                    pWidth = previewWidth
                    pHeight = previewHeight

                    factorX = screenWidth / previewHeight.toFloat()
                    factorY = screenHeight / previewWidth.toFloat()

                    camera?.parameters = parameters

                    camera?.startPreview()
                    camera?.setPreviewCallback(this)

                    setCameraDisplayOrientation()
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    camera?.setPreviewCallback(null)
                    camera?.release()
                    camera = null
                }

                override fun surfaceCreated(holder: SurfaceHolder) {
                    try {
                        camera = Camera.open(cameraId)
                    } catch (e: Exception) {
                        cameraId = Camera.CameraInfo.CAMERA_FACING_BACK
                        camera = Camera.open(cameraId)
                    }

                    try {
                        camera!!.setPreviewDisplay(binding.surface.holder)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    mActivity.runOnUiThread { isAnglePass.text = "没有识别到人脸...无角度数据..." }
                    // 开始计时总检测时间
                    faceDetTime = System.currentTimeMillis()
                }


                override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
                    if (enginePrepared && data != null) {
                        if (!working) {
                            GlobalScope.launch(detectionContext) {
                                working = true
                                val result = engineWrapper.detect(
                                    data,
                                    previewWidth,
                                    previewHeight,
                                    frameOrientation
                                )
                                result.threshold = threshold

                                val rect = calculateBoxLocationOnScreen(
                                    result.left,
                                    result.top,
                                    result.right,
                                    result.bottom
                                )

                                binding.result = result.updateLocation(rect)

                                Log.d(
                                    tag,
                                    "threshold:${result.threshold}, confidence: ${result.confidence}"
                                )

                                if (!isComputing) {
                                    //                                val previewSize = camera!!.parameters.previewSize //获取尺寸,格式转换的时候要用到
                                    isComputing = true
                                    val newOpts = BitmapFactory.Options()
                                    newOpts.inJustDecodeBounds = true
                                    val yuvimage = YuvImage(
                                        data,
                                        ImageFormat.NV21,
                                        pWidth,
                                        pHeight,
                                        null
                                    )
                                    val baos = ByteArrayOutputStream()
                                    yuvimage.compressToJpeg(
                                        Rect(0, 0, pWidth, pHeight),
                                        100,
                                        baos
                                    )// 80--JPG图片的质量[0-100],100最高
                                    val rawImage = baos.toByteArray()
                                    //将rawImage转换成bitmap
                                    val options = BitmapFactory.Options()
                                    options.inPreferredConfig = Bitmap.Config.RGB_565
                                    val bitmap = BitmapFactory.decodeByteArray(
                                        rawImage,
                                        0,
                                        rawImage.size,
                                        options
                                    )

                                    //旋转从摄像头读取的图像
                                    val rotateBitmap = JavaImageUtil.rotateBitmap(bitmap)

                                    matImg = Mat(rotateBitmap.width, rotateBitmap.height, CV_8UC3)
                                    Utils.bitmapToMat(rotateBitmap, matImg)
                                    //人脸图像的清晰度检测
                                    val faceSharpnessDet = JavaImageUtil.isFaceSharpnessDet(matImg)
                                    Imgproc.cvtColor(matImg, matImg, Imgproc.COLOR_RGB2GRAY)
                                    matAddr = matImg!!.nativeObjAddr
                                    if (!isFaceAngleComputing) {
                                        mAngleDetHandler?.post(Runnable {
                                            isFaceAngleComputing = true
                                            val start1 = System.currentTimeMillis()
                                            val faceAngle: IntArray =
                                                FaceAngleDet.faceAngleDet(matAddr)
                                            mActivity.runOnUiThread {
                                                faceAngleText.text =
                                                    faceAngle.contentToString()
                                            }
                                            println(faceAngle.contentToString())
                                            if (faceAngle[0] == 0 && faceAngle[1] == 0 && faceAngle[2] == 0) {
                                                mActivity.runOnUiThread {
                                                    isAnglePass.text = "没有识别到人脸...无角度数据..."
                                                }
                                                isAngleDetPass = false
                                            } else {
                                                if (faceAngle[2] > -40 && faceAngle[2] < 40) {
                                                    mActivity.runOnUiThread {
                                                        isAnglePass.text = "人脸角度检测通过..."
                                                    }
                                                    isAngleDetPass = true
                                                } else {
                                                    mActivity.runOnUiThread {
                                                        isAnglePass.text =
                                                            "人脸角度检测没有通过...请保持人脸正对摄像头..."
                                                    }
                                                    isAngleDetPass = false
                                                }
                                            }
                                            val end1 = System.currentTimeMillis()
                                            println("处理图像耗时：" + (end1 - start1))
                                            isFaceAngleComputing = false
                                        })
                                    }

                                    if (result.confidence > 0.915 && isAngleDetPass) {
                                        if (!breakFace) {
                                            breakFace = true
                                            currentTimeMillis1 = System.currentTimeMillis()
                                        }
                                        if (!isUpload) {
                                            mActivity.runOnUiThread {
                                                faceDetRemind.text = "识别中，请保持人脸居中..."
                                            }
                                        }
                                        val currentTimeMillis2 = System.currentTimeMillis()
                                        // 识别到人脸后2秒,且条件满足,上传服务器
                                        if ((currentTimeMillis2 - currentTimeMillis1) > 2000 && !isUpload) {
                                            if (faceSharpnessDet) {
                                                val face128Vector: DoubleArray =
                                                    Live().face128VectorDet(matAddr)
                                                var face128String = ""
                                                for (i in 0..127) {
                                                    face128String += face128Vector[i]
                                                    face128String += ","
                                                }
                                                face128String = face128String.substring(
                                                    0,
                                                    face128String.length - 1
                                                )
                                                println(face128String)
                                                val userGroup =
                                                    intent.getStringExtra("userGroup")
                                                println("!!!!!!!!!!!!!!!!!$userGroup")
                                                if (ImgService.uploadFaceVector(face128String, userGroup)) {
                                                    mActivity.runOnUiThread {
                                                        faceDetRemind.text = "上传成功"
                                                    }
                                                    if (jscontent.jsonObject.has("distance")) {
                                                        mActivity.runOnUiThread {
                                                            val distanceText =
                                                                jscontent.jsonObject.getString("distance")
                                                            val distanceInt: Double =
                                                                distanceText.toDouble()
                                                            faceDetRemind.text = distanceText
                                                            if (distanceInt < 0.5) {
                                                                mActivity.runOnUiThread {
                                                                    Toast.makeText(this@AliveDetMainActivity, "上传服务器成功,人脸比对成功！\n"
                                                                                + "                 用户为：" + jscontent.jsonObject.getString("uid"), Toast.LENGTH_LONG).show()
                                                                }
                                                            } else {
                                                                mActivity.runOnUiThread {
                                                                    mActivity.toast(
                                                                        "上传服务器成功,人脸比对失败！"
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        mActivity.runOnUiThread {
                                                            val toStringText =
                                                                jscontent.jsonObject.toString()
                                                            faceDetRemind.text = toStringText
                                                            mActivity.runOnUiThread {
                                                                mActivity.toast(
                                                                    "上传成功,    $toStringText"
                                                                )
                                                            }
                                                        }
                                                    }
                                                    isUpload = true
                                                    finish()
                                                } else {
                                                    mActivity.runOnUiThread {
                                                        faceDetRemind.text = "上传失败！"
                                                        mActivity.toast("上传失败！")
                                                        finish()
                                                    }
                                                    isUpload = false
                                                    breakFace = false
                                                }
                                            } else {
                                                mActivity.runOnUiThread { mActivity.toast("清晰度检测没有通过") }
                                                isUpload = false
                                                breakFace = false
                                            }
                                        }
                                    } else {
                                        breakFace = false
                                    }
                                }
                                isComputing = false
                                binding.rectView.postInvalidate()
                                working = false
                                if (System.currentTimeMillis() - faceDetTime > 30000) {
                                    mActivity.runOnUiThread { mActivity.toast("检测超时！") }
                                    finish()
                                }
                            }
                        }
                    }
                }
            })
        }

        scaleAnimator = ObjectAnimator.ofFloat(binding.scan, View.SCALE_Y, 1F, -1F, 1F).apply {
            this.duration = 3000
            this.repeatCount = ValueAnimator.INFINITE
            this.repeatMode = ValueAnimator.REVERSE
            this.interpolator = LinearInterpolator()
            this.start()
        }

    }

    private fun calculateSize() {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
    }

    private fun calculateBoxLocationOnScreen(left: Int, top: Int, right: Int, bottom: Int): Rect =
        Rect(
            (left * factorX).toInt(),
            (top * factorY).toInt(),
            (right * factorX).toInt(),
            (bottom * factorY).toInt()
        )

    private fun setCameraDisplayOrientation() {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation = windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        camera!!.setDisplayOrientation(result)
    }

    fun setting(@Suppress("UNUSED_PARAMETER") view: View) =
        SetThresholdDialogFragment().show(supportFragmentManager, "dialog")

    override fun onDialogPositiveClick(t: Float) {
        threshold = t
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == permissionReqCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init()
            } else {
                Toast.makeText(this, "请授权相机权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        engineWrapper = EngineWrapper(assets)
        enginePrepared = engineWrapper.init()

        if (!enginePrepared) {
            Toast.makeText(this, "Engine init failed.", Toast.LENGTH_LONG).show()
        }

        super.onResume()
    }

    override fun onDestroy() {
        stopCameraThread()
        engineWrapper.destroy()
        scaleAnimator.cancel()
        super.onDestroy()
    }

    companion object {
        const val tag = "AliveDetMainActivity"
        const val defaultThreshold = 0.915F

        val permissions: Array<String> = arrayOf(Manifest.permission.CAMERA)
        const val permissionReqCode = 1
    }

}
