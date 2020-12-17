package com.hxzuicool;

import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bm.library.PhotoView;
import com.mv.livebodyexample.R;

/**
 * @author hxzuicool
 * 2020/12/12
 */
public class ReturnImageViewActivity extends AppCompatActivity {

    private static final String TAG = "ReturnImageView";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imageview);

        PhotoView imageView = findViewById(R.id.imageView);
        imageView.enable();
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String imgPath = getIntent().getStringExtra("imgPath");
        BitmapDrawable bitmapDrawable = new BitmapDrawable(BitmapFactory.decodeFile(imgPath));
        imageView.setImageDrawable(bitmapDrawable);
    }
}
