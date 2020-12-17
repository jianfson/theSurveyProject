package com.hxzuicool;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hxzuicool.chooseimage.GlideLoader;
import com.hxzuicool.utils.ImgService;
import com.hxzuicool.utils.JavaImageUtil;
import com.lcw.library.imagepicker.ImagePicker;

import java.util.ArrayList;

/**
 * @author hxzuicool
 * 2020/12/12
 */
public class ImageCorrectionActivity extends AppCompatActivity {

    private static final int REQUEST_SELECT_IMAGES_CODE = 0x01;
    private ArrayList<String> mImagePaths;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImagePicker.getInstance()
                .setTitle("岩心校正")
                //设置标题
                .showCamera(true)
                //设置是否显示拍照按钮
                .showImage(true)
                //设置是否展示图片
                .showVideo(true)
                //设置是否展示视频
                .filterGif(true)
                //设置是否过滤gif图片
                .setSingleType(true)
                //设置图片视频不能同时选择
                .setMaxCount(1)
                //设置最大选择图片数目(默认为1，单选)
                .setImagePaths(mImagePaths)
                //保存上一次选择图片的状态，如果不需要可以忽略
                .setImageLoader(new GlideLoader())
                //设置自定义图片加载器
                .start(ImageCorrectionActivity.this, REQUEST_SELECT_IMAGES_CODE);
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

        if (mImagePaths == null) {
            Toast.makeText(this,"没有选择照片！", Toast.LENGTH_SHORT).show();
            return;
        }

        if (JavaImageUtil.isStoneSharpnessDet(mImagePaths.get(0))) {
            Log.e("ImageCorrectionActivity", mImagePaths.get(0));
            String[] strings = ImgService.uploadImg(mImagePaths.get(0), 1);
            if ("true".equals(strings[0])) {
                Toast.makeText(this, "上传成功！", Toast.LENGTH_SHORT).show();
                Intent toCheckImg = new Intent(ImageCorrectionActivity.this, ReturnImageViewActivity.class);
                toCheckImg.putExtra("imgPath", strings[1]);
                startActivity(toCheckImg);
            } else {
                Toast.makeText(this, "上传失败！", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "清晰度不够!", Toast.LENGTH_SHORT).show();
        }
    }


}
