package com.survey.cameraface.detect

import android.graphics.RectF
import android.hardware.camera2.params.Face
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import com.survey.cameraface.R
import kotlinx.android.synthetic.main.activity_camera_face.*

class CameraActivityFace : AppCompatActivity(), CameraHelperFace.FaceDetectListener {
    private lateinit var cameraHelperFace: CameraHelperFace         // 初始化CameraHelperFace

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_face)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        cameraHelperFace = CameraHelperFace(this, textureView)
        cameraHelperFace.setFaceDetectListener(this)

        btnTakePic.setOnClickListener { cameraHelperFace.takePic() }            // 拍照
        ivExchange.setOnClickListener { cameraHelperFace.exchangeCamera() }     // 切换摄像头
    }

    override fun onFaceDetect(faces: Array<Face>, facesRect: ArrayList<RectF>) {
        faceView.setFaces(facesRect)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelperFace.releaseCamera()
        cameraHelperFace.releaseThread()
    }

}