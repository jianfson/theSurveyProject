package com.survey.cameraface

import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.survey.cameraface.util.log
import kotlinx.android.synthetic.main.activity_result.*

class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        var bundle = this.intent.extras
        var mFacePath = bundle?.getString("ImagePath")

        var mFaceBitmap = BitmapFactory.decodeFile(mFacePath)
        imageView.setImageBitmap(mFaceBitmap)
    }
}