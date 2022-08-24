package com.jd.sampleply;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static android.os.Environment.getExternalStorageDirectory;

public class PlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "PlayerActivity";
    private MediaCodecPlayer mMediaCodecPlayer;
    private SurfaceView mSurfaceV;
    private SurfaceHolder mSurfaceHolder;
    private MediaController mediaController;
    private Uri mFileUrl;
    private static final int SLEEP_TIME_MS = 1000;
    private static final long PLAY_TIME_MS = TimeUnit.MILLISECONDS.convert(4, TimeUnit.MINUTES);

    public boolean checkPermission(Context context) {
        return SettingsCompat.checkPermission(context);
    }

    public void requestPermission(Context context) {
         //SettingsCompat.requestPermission(context);
        if (Build.VERSION.SDK_INT >= 23) {// 6.0
            String[] perms = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE};
            for (String p : perms) {
                int f = ContextCompat.checkSelfPermission(PlayerActivity.this, p);
                Log.d("---", String.format("%s - %d", p, f));
                if (f != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(perms, 0XCF);
                    break;
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ) {// android 11  且 不是已经被拒绝
            // 先判断有没有权限
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1024);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        mSurfaceV = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceV.getHolder().addCallback(this);
        View root = findViewById(R.id.root);
        mediaController = new MediaController(this);
        mediaController.setAnchorView(root);
        root.setOnKeyListener(new View.OnKeyListener(){

            @Override
            public boolean onKey(View view, int i, KeyEvent event) {
                return mediaController.dispatchKeyEvent(event);
            }
        });
        //Intent intent = getIntent();
        //mFileUrl = intent.getData();
        mFileUrl = getFile();
        if(!checkPermission(this)) {
            requestPermission(this);
        }
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        this.sendBroadcast(i);
    }

    public Uri getFile(){

        String path = getExternalStorageDirectory().toString();
        path +="/Movies/Telegram/";
        path += "VID_20220805_215345_727.mp4";
        // getFilesDir().getAbsolutePath();// /data/user/0/com.jd.sampleply/files
        // getExternalStorageDirectory().toString();// /storage/emulated/0

        Log.d("Test", path);

        File file = new File(path);
        return Uri.fromFile(file);
    }
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        this.mSurfaceHolder = holder;
        mSurfaceHolder.setKeepScreenOn(true);
        new DecodeTask().execute();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        if (mMediaCodecPlayer != null) {
            mMediaCodecPlayer.reset();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mMediaCodecPlayer != null) {
            mMediaCodecPlayer.reset();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mMediaCodecPlayer != null) {
            mMediaCodecPlayer.reset();
        }
    }

    public class DecodeTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            //this runs on a new thread
            initializePlayer();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            //this runs on ui thread
        }
    }

    private void initializePlayer() {
        mMediaCodecPlayer = new MediaCodecPlayer(mSurfaceHolder, getApplicationContext());

        mMediaCodecPlayer.setAudioDataSource(mFileUrl, null);
        mMediaCodecPlayer.setVideoDataSource(mFileUrl, null);
        mMediaCodecPlayer.start(); //from IDLE to PREPARING
        try {
            mMediaCodecPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // starts video playback
        mMediaCodecPlayer.startThread();

        long timeOut = System.currentTimeMillis() + 4*PLAY_TIME_MS;
        while (timeOut > System.currentTimeMillis() && !mMediaCodecPlayer.isEnded()) {
            try {
                Thread.sleep(SLEEP_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mMediaCodecPlayer.getCurrentPosition() >= mMediaCodecPlayer.getDuration() ) {
                Log.d(TAG, "testVideoPlayback -- current pos = " +
                        mMediaCodecPlayer.getCurrentPosition() +
                        ">= duration = " + mMediaCodecPlayer.getDuration());
                break;
            }
        }

        if (timeOut > System.currentTimeMillis()) {
            Log.e(TAG, "video playback timeout exceeded!");
            return;
        }

        Log.d(TAG, "playVideo player.reset()");
        mMediaCodecPlayer.reset();
    }
}