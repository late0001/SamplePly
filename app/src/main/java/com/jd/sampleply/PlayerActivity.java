package com.jd.sampleply;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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

    public void requestPermission(Context context) {
         SettingsCompat.requestPermission(context);
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
        requestPermission(this);
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