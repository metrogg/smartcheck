package com.seetatech.silentliving;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.seeta.sdk.FaceAntiSpoofing;
import com.seetatech.silentliving.mvp.MainFragment;
import com.seetatech.silentliving.mvp.PresenterImpl;
import com.seetatech.silentliving.utils.CachedStatusAndImage;
import com.seetatech.silentliving.utils.FileUtils;

import java.io.File;

public class AntiSpoofingComponent extends AppCompatActivity {

    public static final String TAG = "AntiSpoofingComponent";

    public static boolean Passed() {//获取活体状态
        return CachedStatusAndImage.fasStatus;
    }

    public static Bitmap GetDetectedBitmap()
    {
        return CachedStatusAndImage.detectedBitmap;
    }

    public static FaceAntiSpoofing.Status GetFASState()
    {
        return CachedStatusAndImage.status;
    }

    public static void SetTimeLimit(int time)//设置活体过程持续的时间
    {
        CachedStatusAndImage.lastTime = time;
    }

    public static void ResetStatus()
    {
        CachedStatusAndImage.status = FaceAntiSpoofing.Status.DETECTING;
        CachedStatusAndImage.currentFrameNum = 0;
    }

    public static String MODELPATH;

    public void copyModel()
    {
        //加入模型缓存
        String fdModel = "face_detector.csta";
        String pdModel = "face_landmarker_pts5.csta";
        String fasModel1 = "fas_first.csta";
        String fasModel2 = "fas_second.csta";

        File cacheDir = getExternalCacheDirectory(this, null);
        String modelPath = cacheDir.getAbsolutePath();
        Log.d("cacheDir", "" + modelPath);
        MODELPATH = modelPath;

        if(!isExists(modelPath, fdModel))
        {
            File fdFile = new File(cacheDir + "/" + fdModel);
            FileUtils.copyFromAsset(this, fdModel, fdFile, false);
        }
        if(!isExists(modelPath, pdModel))
        {
            File pdFile = new File(cacheDir + "/" + pdModel);
            FileUtils.copyFromAsset(this, pdModel, pdFile, false);
        }
        if(!isExists(modelPath, fasModel1))
        {
            File fasFile1 = new File(cacheDir + "/" + fasModel1);
            FileUtils.copyFromAsset(this, fasModel1, fasFile1, false);
        }
        if(!isExists(modelPath, fasModel2))
        {
            File fasFile2 = new File(cacheDir + "/" + fasModel2);
            FileUtils.copyFromAsset(this, fasModel2, fasFile2, false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        copyModel();

        Fragment fragment = FragmentFactory.create(this, BuildConfig.BUILD_TYPE);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, fragment)
                .commitNow();
        this.setFinishOnTouchOutside(false);
    }

    public boolean isExists(String path, String modelName)
    {
        File file = new File(path + "/" + modelName);
        if(file.exists()) return true;

        return false;
    }

    public static File getInternalCacheDirectory(Context context, String type) {
        File appCacheDir = null;
        if (TextUtils.isEmpty(type)){
            appCacheDir = context.getCacheDir();// /data/data/app_package_name/cache
        }else {
            appCacheDir = new File(context.getFilesDir(),type);// /data/data/app_package_name/files/type
        }

        if (!appCacheDir.exists()&&!appCacheDir.mkdirs()){
            Log.e("getInternalDirectory","getInternalDirectory fail ,the reason is make directory fail !");
        }
        return appCacheDir;
    }

    public static File getExternalCacheDirectory(Context context, String type) {
        File appCacheDir = null;
        if (TextUtils.isEmpty(type)){
            appCacheDir = context.getExternalCacheDir();// /sdcard/data/data/app_package_name/cache
        }else {
            appCacheDir = new File(context.getFilesDir(),type);// /data/data/app_package_name/files/type
        }

        if (!appCacheDir.exists()&&!appCacheDir.mkdirs()){
            Log.e("getInternalDirectory","getInternalDirectory fail ,the reason is make directory fail !");
        }
        return appCacheDir;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        View decorView = getWindow().getDecorView();
        int uiOptions = decorView.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
    }

    private static class FragmentFactory {
        public static Fragment create(Context context, String buildType) {

                MainFragment fragment = new MainFragment();
                new PresenterImpl(fragment);
                return fragment;

        }
    }
}
