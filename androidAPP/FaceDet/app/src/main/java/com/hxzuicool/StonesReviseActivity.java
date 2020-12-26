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
import com.hxzuicool.utils.jscontent;
import com.lcw.library.imagepicker.ImagePicker;

import java.util.ArrayList;

/**
 * @author hxzuicool
 * 2020/12/18
 * @Description 批量校正
 */
public class StonesReviseActivity extends AppCompatActivity {

    private ArrayList<String> mImagePaths;
    private static final int REQUEST_SELECT_IMAGES_CODE = 0x01;
    private static final String TAG = "ImageCorrectionActivity";
    private Handler handler;
    private HandlerThread handlerThread;
    private MyDialogFragment myDialogFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handlerThread = new HandlerThread("uploadStoneImages");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        myDialogFragment = new MyDialogFragment();
        myDialogFragment.setCancelable(false);

        ImagePicker.getInstance()
                .setTitle("批量岩心校正")
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
                .setMaxCount(10)
                //设置最大选择图片数目(默认为1，单选)
                .setImagePaths(mImagePaths)
                //保存上一次选择图片的状态，如果不需要可以忽略
                .setImageLoader(new GlideLoader())
                //设置自定义图片加载器
                .start(StonesReviseActivity.this, REQUEST_SELECT_IMAGES_CODE);

        Toast.makeText(StonesReviseActivity.this, "选择照片", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_IMAGES_CODE && resultCode == RESULT_OK) {
            assert data != null;
            mImagePaths = data.getStringArrayListExtra(ImagePicker.EXTRA_SELECT_IMAGES);
        }
        if (mImagePaths == null) {
            Toast.makeText(this, "没有选择照片！", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        myDialogFragment.show(getSupportFragmentManager(), "dialogWait...");

        handler.post(new Runnable() {
            @Override
            public void run() {

                try {
                    for (int i = 0; i < mImagePaths.size(); i++) {
                        if (!JavaImageUtil.isStoneSharpnessDet(mImagePaths.get(i))) {
                            myDialogFragment.dismiss();
                            Toast.makeText(StonesReviseActivity.this, "选择的第" + (i + 1) + "张图片清晰度检测没有通过！", Toast.LENGTH_SHORT).show();
                            System.out.println("选择的第" + (i + 1) + "张图片清晰度检测没有通过！");
                            finish();
                            return;
                        }
                    }

                    String[] strings = ImgService.uploadImg(mImagePaths, 3, getApplicationContext());
                    if ("true".equals(strings[0])) {
                        myDialogFragment.dismiss();
//                        Toast.makeText(StonesReviseActivity.this, "上传成功！", Toast.LENGTH_SHORT).show();
                        if (jscontent.jsonObject.has("input_path") && jscontent.jsonObject.has("result_path")) {
                            Toast.makeText(StonesReviseActivity.this,
                                    "输入图片路径:"
                                    + jscontent.jsonObject.getString("input_path") + "\n"
                                    + "请从以下路径下载校正之后的图片:\n"
                                    + jscontent.jsonObject.getString("result_path"), Toast.LENGTH_LONG).show();
                        }
                        Log.e(TAG, jscontent.jsonObject.toString());
                    } else {
                        myDialogFragment.dismiss();
                        Toast.makeText(StonesReviseActivity.this, "上传失败！", Toast.LENGTH_SHORT).show();
                    }
                    // 无论选没有选择照片，都Finnish掉Activity
                    finish();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handler = null;
            handlerThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
