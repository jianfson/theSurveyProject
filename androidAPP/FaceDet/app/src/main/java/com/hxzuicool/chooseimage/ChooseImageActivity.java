package com.hxzuicool.chooseimage;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hxzuicool.ReturnImageViewActivity;
import com.hxzuicool.utils.ImgThread;
import com.hxzuicool.utils.JavaImageUtil;
import com.lcw.library.imagepicker.ImagePicker;

import java.util.ArrayList;

/**
 * @author hxzuicool
 * 2020/12/11
 */
public class ChooseImageActivity extends AppCompatActivity {

    private String TAG = "ChooseImageActivity";
    private static final int REQUEST_SELECT_IMAGES_CODE = 0x01;
    private ArrayList<String> mImagePaths;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImagePicker.getInstance()
                .setTitle("岩心拼接")
                //设置标题
                .showCamera(true)
                //设置是否显示拍照按钮
                .showImage(true)
                //设置是否展示图片
                .showVideo(true)
                //设置是否展示视频
                .filterGif(false)
                //设置是否过滤gif图片
                .setSingleType(true)
                //设置图片视频不能同时选择
                .setMaxCount(9)
                //设置最大选择图片数目(默认为1，单选)
                .setImagePaths(mImagePaths)
                //保存上一次选择图片的状态，如果不需要可以忽略
                .setImageLoader(new GlideLoader())
                //设置自定义图片加载器
                .start(ChooseImageActivity.this, REQUEST_SELECT_IMAGES_CODE);
        Toast.makeText(ChooseImageActivity.this,"请选择一张以上照片...",Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 无论选没有选择照片，都Finnish掉Activity
        finish();
        if (requestCode == REQUEST_SELECT_IMAGES_CODE && resultCode == RESULT_OK) {
            assert data != null;
            mImagePaths = data.getStringArrayListExtra(ImagePicker.EXTRA_SELECT_IMAGES);
        }
        System.out.println(mImagePaths);
        try {
            if (mImagePaths == null){
                Toast.makeText(this,"没有选择照片！", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadImg();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void uploadImg() throws InterruptedException {
        for (int i = 0; i < mImagePaths.size(); i++) {
            if (!JavaImageUtil.isStoneSharpnessDet(mImagePaths.get(i))) {
                Looper.prepare();
                Toast.makeText(ChooseImageActivity.this, "选择的第" + (i + 1) + "张图片清晰度检测没有通过！", Toast.LENGTH_SHORT).show();
                Looper.loop();
                System.out.println("选择的第" + (i + 1) + "张图片清晰度检测没有通过！");
                return;
            }
        }
        ImgThread imgThread = new ImgThread("http://47.108.134.136:5256/rock/split", 2, mImagePaths);
        imgThread.start();
        imgThread.join();
        if (imgThread.getResult()){
            Toast.makeText(this, "上传成功！", Toast.LENGTH_SHORT).show();
            Intent toCheckImg = new Intent(ChooseImageActivity.this, ReturnImageViewActivity.class);
            toCheckImg.putExtra("imgPath", imgThread.getReturnImgPath());
            startActivity(toCheckImg);
        }else {
            Toast.makeText(this, "上传失败！", Toast.LENGTH_SHORT).show();
        }
    }

}
