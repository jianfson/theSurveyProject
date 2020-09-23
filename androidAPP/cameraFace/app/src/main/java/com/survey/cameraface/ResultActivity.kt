package com.survey.cameraface

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.survey.cameraface.util.log
import kotlinx.android.synthetic.main.activity_result.*

class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)



        backHome_btn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}