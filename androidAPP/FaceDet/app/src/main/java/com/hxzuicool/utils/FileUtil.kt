package com.hxzuicool.utils

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileUtil {
    private val rootFolderPath = Environment.getExternalStorageDirectory().absolutePath + File.separator + "CameraDemo"

    fun createImageFile(isCrop: Boolean = false): File? {
        return try {
            var rootFile = File(rootFolderPath + File.separator + "capture")
            if (!rootFile.exists())
                rootFile.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = if (isCrop) "IMG_${timeStamp}_CROP.jpg" else "IMG_$timeStamp.jpg"
            File(rootFile.absolutePath + File.separator + fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createCameraFile(folderName: String = "camera1"): File? {
        return try {
            val rootFile = File(rootFolderPath + File.separator + "$folderName")
            if (!rootFile.exists())
                rootFile.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = "IMG_$timeStamp.jpg"
            File(rootFile.absolutePath + File.separator + fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createVideoFile(): File? {
        return try {
            var rootFile = File(rootFolderPath + File.separator + "video")
            if (!rootFile.exists())
                rootFile.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = "VIDEO_$timeStamp.mp4"
            File(rootFile.absolutePath + File.separator + fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 根据byte数组，生成文件
     */
//    fun getFile(
//        bfile: ByteArray?,
//        filePath: String,
//        fileName: String
//    ) {
//        var bos: BufferedOutputStream? = null
//        var fos: FileOutputStream? = null
//        var file: File? = null
//        try {
//            val dir = File(filePath)
//            if (!dir.exists() && dir.isDirectory) { //判断文件目录是否存在
//                dir.mkdirs()
//            }
//            file = File(filePath  + fileName)
//            fos = FileOutputStream(file)
//            bos = BufferedOutputStream(fos)
//            bos.write(bfile)
//        } catch (e: java.lang.Exception) {
//            e.printStackTrace()
//        } finally {
//            if (bos != null) {
//                try {
//                    bos.close()
//                } catch (e1: IOException) {
//                    e1.printStackTrace()
//                }
//            }
//            if (fos != null) {
//                try {
//                    fos.close()
//                } catch (e1: IOException) {
//                    e1.printStackTrace()
//                }
//            }
//        }
//    }

}