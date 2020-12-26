package com.hxzuicool;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hxzuicool.chooseimage.GlideLoader;
import com.hxzuicool.dialog.MyDialogFragment;
import com.hxzuicool.utils.ImgService;
import com.hxzuicool.utils.JavaImageUtil;
import com.lcw.library.imagepicker.ImagePicker;

import java.util.ArrayList;

/**
 * @author hxzuicool
 * 2020/12/12
 */
public class StoneCoreReviseActivity extends AppCompatActivity {

    private static final int REQUEST_SELECT_IMAGES_CODE = 0x01;
    private static final String TAG = "ImageCorrectionActivity";
    private ArrayList<String> mImagePaths;
    private Handler handler;
    private HandlerThread handlerThread;
    private MyDialogFragment myDialogFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handlerThread = new HandlerThread("uploadStoneImage");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        myDialogFragment = new MyDialogFragment();
        myDialogFragment.setCancelable(false);

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
                .start(StoneCoreReviseActivity.this, REQUEST_SELECT_IMAGES_CODE);

        Toast.makeText(StoneCoreReviseActivity.this, "选择一张照片", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_IMAGES_CODE && resultCode == RESULT_OK) {
            assert data != null;
            mImagePaths = data.getStringArrayListExtra(ImagePicker.EXTRA_SELECT_IMAGES);
        }

        if (mImagePaths == null) {
            Toast.makeText(this,"没有选择照片！", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        myDialogFragment.show(getSupportFragmentManager(), "dialog1");
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (JavaImageUtil.isStoneSharpnessDet(mImagePaths.get(0))) {
                    Log.e("ImageCorrectionActivity", mImagePaths.get(0));
                    String[] strings = ImgService.uploadImg(mImagePaths.get(0), 1, getApplicationContext());
                    if ("true".equals(strings[0]) && strings[1] != null) {
                        myDialogFragment.dismiss();
                        Toast.makeText(StoneCoreReviseActivity.this, "上传成功！", Toast.LENGTH_SHORT).show();
                        Intent toCheckImg = new Intent(StoneCoreReviseActivity.this, ReturnImageViewActivity.class);
                        toCheckImg.putExtra("imgPath", strings[1]);
                        startActivity(toCheckImg);
                    } else {
                        myDialogFragment.dismiss();
                        Toast.makeText(StoneCoreReviseActivity.this, "上传失败！", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    myDialogFragment.dismiss();
                    Toast.makeText(StoneCoreReviseActivity.this, "清晰度不够!", Toast.LENGTH_SHORT).show();
                }
                // 无论选没有选择照片，都Finnish掉Activity
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy: ...");
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handler = null;
            handlerThread = null;
        } catch (InterruptedException e) {
            System.out.println("StoneCoreReviseActivity : onDestroy()");
            e.printStackTrace();
        }
    }
}
