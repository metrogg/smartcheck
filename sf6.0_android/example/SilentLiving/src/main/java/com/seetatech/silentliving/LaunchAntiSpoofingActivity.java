package com.seetatech.silentliving;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.seetatech.silentliving.utils.CachedStatusAndImage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class LaunchAntiSpoofingActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView tvfacestatus;
    private Button btn_launch = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
        imageView = (ImageView) findViewById(R.id.best_image);
        tvfacestatus = (TextView) findViewById(R.id.facestatus);

        btn_launch = (Button)findViewById(R.id.btn_launch);

        btn_launch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AntiSpoofingComponent.ResetStatus();
                startActivityForResult(new Intent(LaunchAntiSpoofingActivity.this, AntiSpoofingComponent.class), 1);
            }
        });

    }

//    public void onClick(View view) {
//        startActivityForResult(new Intent(this, AntiSpoofingComponent.class), 1);
//    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        imageView.setImageBitmap(AntiSpoofingComponent.GetDetectedBitmap());
        switch (CachedStatusAndImage.status)
        {
            case DETECTING:
                tvfacestatus.setText("没有人脸");
                break;
            case REAL:
                tvfacestatus.setText("真人脸");
                break;
            case SPOOF:
                tvfacestatus.setText("假人脸");
                break;
            case FUZZY:
                tvfacestatus.setText("图像过于模糊");
                break;
        }

        AntiSpoofingComponent.ResetStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}