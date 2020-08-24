package com.example.thesurveyproject

import android.graphics.RectF
import android.hardware.camera2.params.Face
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import kotlinx.android.synthetic.main.activity_camera_face.*

class CameraActivityFace : AppCompatActivity(), CameraHelperFace.FaceDetectListener {
    private lateinit var cameraHelperFace: CameraHelperFace

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_face)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        cameraHelperFace = CameraHelperFace(this, textureView)
        cameraHelperFace.setFaceDetectListener(this)

        btnTakePic.setOnClickListener { cameraHelperFace.takePic() }
        ivExchange.setOnClickListener { cameraHelperFace.exchangeCamera() }
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