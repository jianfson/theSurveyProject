package com.example.thesurveyproject

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

import com.example.thesurveyproject.PermissionUtils
import com.example.thesurveyproject.PermissionUtils.PERMISSION_REQUEST_CODE
import com.example.thesurveyproject.PermissionUtils.PERMISSION_SETTING_CODE

class MainActivity : AppCompatActivity() {

    private val permissionsList = arrayOf(Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*btCapture.setOnClickListener {
            PermissionUtils.checkPermission(this, permissionsList, Runnable {
                startActivity(Intent(this, CaptureActivity::class.java))
            })
        }
        btCamera.setOnClickListener {
            PermissionUtils.checkPermission(this, permissionsList, Runnable {
                val intent = Intent(this, CameraActivity::class.java)
                intent.putExtra(CameraActivity.TYPE_TAG, CameraActivity.TYPE_CAPTURE)
                startActivity(intent)
            })
        }
        btCameraRecord.setOnClickListener {
            PermissionUtils.checkPermission(this, permissionsList, Runnable {
                val intent = Intent(this, CameraActivity::class.java)
                intent.putExtra(CameraActivity.TYPE_TAG, CameraActivity.TYPE_RECORD)
                startActivity(intent)
            })
        }

        btCamera2.setOnClickListener {
            PermissionUtils.checkPermission(this, permissionsList, Runnable {
                val intent = Intent(this, CameraActivity::class.java)
                startActivity(intent)
            })
        }*/

        btCamera2Face.setOnClickListener {
            PermissionUtils.checkPermission(this, permissionsList, Runnable {
                startActivity(Intent(this, CameraActivityFace::class.java))
            })
        }

        PermissionUtils.checkPermission(this, permissionsList, Runnable {
        })
    }

    /**
     * 第四步，请求权限的结果回调
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.e("tag","onRequestPermissionsResult ")

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                var allGranted = true

                grantResults.forEach {
                    if (it != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false
                    }
                }

                if (allGranted) {  //已获得全部权限
                    Log.e("tag","onRequestPermissionsResult 已获得全部权限")
                } else {
                    Log.e("tag","权限请求被拒绝了,不能继续依赖该权限的相关操作了，展示setting ")

                    // 权限请求被拒绝了,不能继续依赖该权限的相关操作了
                    PermissionUtils.showPermissionSettingDialog(this)
                }
            }
        }
    }


    /**
     * 当从设置权限页面返回后，重新请求权限
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PERMISSION_SETTING_CODE -> { //第五步，当从设置权限页面返回后，重新请求权限
                Log.e("tag","从设置权限页面返回后，重新请求权限")
                PermissionUtils.checkPermission(this, permissionsList, Runnable {
                    val intent = Intent(this, CameraActivityFace::class.java)
                    startActivity(intent)
                })
            }

        }
    }

}