package com.survey.cameraface

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

import com.survey.cameraface.util.PermissionUtils
import com.survey.cameraface.util.PermissionUtils.PERMISSION_REQUEST_CODE
import com.survey.cameraface.util.PermissionUtils.PERMISSION_SETTING_CODE

import com.survey.cameraface.detect.CameraActivityFace

//继承父类AppCompatActivity(){...}
class MainActivity : AppCompatActivity() {

    //权限列表，不可变变量，类似final关键字
    private val permissionsList = arrayOf(
        Manifest.permission.READ_PHONE_STATE,           //只读获取手机状态，包括蜂窝网络使用、正在进行的任何调用等
        Manifest.permission.CAMERA,                     //访问设备摄像头
        Manifest.permission.RECORD_AUDIO                //音频权限
    )

    //重写onCreate()函数
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //监听器
        btCamera2Face.setOnClickListener {
            //检查权限列表是否满足
            PermissionUtils.checkPermission(this, permissionsList, Runnable {
                startActivity(Intent(this, CameraActivityFace::class.java))
            })
        }

        //检查权限列表是否满足
        PermissionUtils.checkPermission(this, permissionsList, Runnable {
        })
    }

    /**
     * 第四步，请求权限的结果回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        Log.e("tag", "onRequestPermissionsResult ")

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                var allGranted = true

                grantResults.forEach {
                    if (it != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false
                    }
                }

                if (allGranted) {  //已获得全部权限
                    Log.e("tag", "onRequestPermissionsResult 已获得全部权限")
                } else {
                    Log.e("tag", "权限请求被拒绝了,不能继续依赖该权限的相关操作了，展示setting ")

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
                Log.e("tag", "从设置权限页面返回后，重新请求权限")
                PermissionUtils.checkPermission(this, permissionsList, Runnable {
                    val intent = Intent(this, CameraActivityFace::class.java)
                    startActivity(intent)
                })
            }

        }
    }

}