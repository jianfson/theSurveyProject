package com.hxzuicool;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hxzuicool.chooseimage.ChooseImageActivity;
import com.hxzuicool.utils.ImgService;
import com.hxzuicool.utils.JavaImageUtil;
import com.mv.livebodyexample.AliveDetMainActivity;
import com.mv.livebodyexample.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.os.Build.VERSION_CODES.N;

/**
 * @author: hxzuicool~
 * @date: 2020/10/28
 */
public class ChooseActivity extends AppCompatActivity {

    private static final String TAG = "ChooseActivity";

    static {
        System.loadLibrary("opencv_java3");
    }

    static final int REQUEST_IMAGE_CAPTURE = 1;
    private File photoFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose);
        // 人脸检测
        findViewById(R.id.FaceDetBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View inflate = View.inflate(ChooseActivity.this, R.layout.users, null);
                final EditText groupText = inflate.findViewById(R.id.users);
                Button confirmBtn = inflate.findViewById(R.id.confirmBtn);
                final AlertDialog.Builder builder = new AlertDialog.Builder(ChooseActivity.this).setTitle("输入用户组").setView(inflate);
                final AlertDialog alertDialog = builder.show();
                confirmBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (groupText.getText().length() != 0){
                            Log.e(TAG, "onClick:" + groupText.getText());
                            alertDialog.dismiss();
                            Intent intent = new Intent(ChooseActivity.this, AliveDetMainActivity.class);
                            intent.putExtra("userGroup", groupText.getText().toString());
                            startActivity(intent);
                        } else {
                            Toast.makeText(ChooseActivity.this,"必须要输入用户组才能进入人脸识别功能！\n" +
                                                                            "           输入错误可能导致无法上传！", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
        //清晰度检测
        findViewById(R.id.sharpnessDetBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ChooseActivity.this, SharpnessDetActivity.class));
            }
        });
        //岩心校正
        findViewById(R.id.galleryBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(ChooseActivity.this, "Pick one image", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(ChooseActivity.this, ImageCorrectionActivity.class));
            }
        });
        //岩心拼接
        findViewById(R.id.splitjoinBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ChooseActivity.this, ChooseImageActivity.class));
            }
        });
        //现场岩心拍照
        findViewById(R.id.takeStonePhoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentapiVersion = android.os.Build.VERSION.SDK_INT;
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                photoFile = new File(Environment.getExternalStorageDirectory().getPath() + "/hxzuicool/", timeStamp);
                if (currentapiVersion < N) {
                    Uri fileUri = Uri.fromFile(photoFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                } else {
                    ContentValues contentValues = new ContentValues(1);
                    contentValues.put(MediaStore.Images.Media.DATA, photoFile.getAbsolutePath());
                    Uri fileUri = getApplication().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                }
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Log.e(TAG, String.valueOf(photoFile));
            if (JavaImageUtil.isStoneSharpnessDet(photoFile.toString())) {
                String[] strings = ImgService.uploadImg(photoFile.toString(), 1);
                Log.e(TAG, "onActivityResult: " + strings[1] );
                if ("true".equals(strings[0])) {
                    Toast.makeText(this, "上传成功！", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "上传失败！", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "清晰度不够!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "没有拍照！", Toast.LENGTH_LONG).show();
        }
    }
}
