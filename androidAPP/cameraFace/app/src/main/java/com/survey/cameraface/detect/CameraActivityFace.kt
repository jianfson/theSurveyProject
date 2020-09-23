package com.survey.cameraface.detect

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.hardware.camera2.params.Face
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import com.survey.cameraface.R
import com.survey.cameraface.ResultActivity
import com.survey.cameraface.util.log
import kotlinx.android.synthetic.main.activity_camera_face.*
import org.opencv.android.OpenCVLoader

class CameraActivityFace : AppCompatActivity(), CameraHelperFace.FaceDetectListener {
    private lateinit var cameraHelperFace: CameraHelperFace         //延迟初始化CameraHelperFace

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_face)

        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        cameraHelperFace = CameraHelperFace(this, textureView)
        cameraHelperFace.setFaceDetectListener(this)

        //btnTakePic.setOnClickListener { cameraHelperFace.takePic() }            // 拍照
        //ivExchange.setOnClickListener { cameraHelperFace.exchangeCamera() }     // 切换摄像头

    }

    override fun onResume() {
        super.onResume()

        //初始化使用本地native库
        OpenCVLoader.initDebug()
    }

    override fun onFaceDetect(faces: Array<Face>, facesRect: ArrayList<RectF>) {
        faceView.setFaces(facesRect)
        if (CameraHelperFace.IsFaceDetected) {
            cameraHelperFace.takePic()
            startActivity(Intent(this, ResultActivity::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        log("onPause")
        cameraHelperFace.releaseCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy")
        cameraHelperFace.releaseThread()
    }

}