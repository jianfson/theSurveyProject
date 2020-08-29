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
import kotlinx.android.synthetic.main.activity_camera_face.*

class CameraActivityFace : AppCompatActivity(), CameraHelperFace.FaceDetectListener {
    private lateinit var cameraHelperFace: CameraHelperFace         // 初始化CameraHelperFace，延迟初始化

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_face)

        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        cameraHelperFace = CameraHelperFace(this, textureView)
        cameraHelperFace.setFaceDetectListener(this)

        //btnTakePic.setOnClickListener { cameraHelperFace.takePic() }            // 拍照
        //ivExchange.setOnClickListener { cameraHelperFace.exchangeCamera() }     // 切换摄像头

    }

    override fun onFaceDetect(faces: Array<Face>, facesRect: ArrayList<RectF>) {
        faceView.setFaces(facesRect)
        if (CameraHelperFace.IsFaceDetected) {
            cameraHelperFace.takePic()
            cameraHelperFace.releaseCamera()

            var intent = Intent(this, ResultActivity::class.java)
            var bundle = Bundle()
            bundle.putString("ImagePath", "/storage/emulated/0/CameraDemo/camera2/IMG_20200829_222552.jpg")               //图片地址
            intent.putExtras(intent)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelperFace.releaseCamera()
        cameraHelperFace.releaseThread()
    }

}