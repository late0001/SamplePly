package com.jd.sampleply;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.AudioTrack;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Class for directly managing both audio and video playback by
 * using {@link MediaCodec} and {@link AudioTrack}.
 */
public class CodecState {
    private static final String TAG = CodecState.class.getSimpleName();

    private boolean mSawInputEOS, mSawOutputEOS;
    private boolean mLimitQueueDepth;
    private boolean mIsAudio;
    private ByteBuffer[] mCodecInputBuffers;
    private ByteBuffer[] mCodecOutputBuffers;
    private int mTrackIndex;
    private LinkedList<Integer> mAvailableInputBufferIndices;
    private LinkedList<Integer> mAvailableOutputBufferIndices;
    private LinkedList<MediaCodec.BufferInfo> mAvailableOutputBufferInfos;
    private long mPresentationTimeUs;
    private long mSampleBaseTimeUs;
    private MediaCodec mCodec;
    private MediaTimeProvider mMediaTimeProvider;
    private MediaExtractor mExtractor;
    private MediaFormat mFormat;
    private MediaFormat mOutputFormat;
    private NonBlockingAudioTrack mAudioTrack;

    /**
     * Manages audio and video playback using MediaCodec and AudioTrack.
     */
    public CodecState(
            MediaTimeProvider mediaTimeProvider,
            MediaExtractor extractor,
            int trackIndex,
            MediaFormat format,
            MediaCodec codec,
            boolean limitQueueDepth) {
        mMediaTimeProvider = mediaTimeProvider;
        mExtractor = extractor;
        mTrackIndex = trackIndex;
        mFormat = format;
        mSawInputEOS = mSawOutputEOS = false;
        mLimitQueueDepth = limitQueueDepth;
        mSampleBaseTimeUs = -1;

        mCodec = codec;

        mAvailableInputBufferIndices = new LinkedList<Integer>();
        mAvailableOutputBufferIndices = new LinkedList<Integer>();
        mAvailableOutputBufferInfos = new LinkedList<MediaCodec.BufferInfo>();

        mPresentationTimeUs = 0;

        String mime = mFormat.getString(MediaFormat.KEY_MIME);
        Log.d(TAG, "CodecState::onOutputFormatChanged " + mime);
        mIsAudio = mime.startsWith("audio/");
    }

    public void release() {
        mCodec.stop();
        mCodecInputBuffers = null;
        mCodecOutputBuffers = null;
        mOutputFormat = null;

        mAvailableInputBufferIndices.clear();
        mAvailableOutputBufferIndices.clear();
        mAvailableOutputBufferInfos.clear();

        mAvailableInputBufferIndices = null;
        mAvailableOutputBufferIndices = null;
        mAvailableOutputBufferInfos = null;

        mCodec.release();
        mCodec = null;

        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    public void start() {
        mCodec.start();
        mCodecInputBuffers = mCodec.getInputBuffers();
        mCodecOutputBuffers = mCodec.getOutputBuffers();

        if (mAudioTrack != null) {
            mAudioTrack.play();
        }
    }

    public void pause() {
        if (mAudioTrack != null) {
            mAudioTrack.pause();
        }
    }

    public long getCurrentPositionUs() {
        return mPresentationTimeUs;
    }

    public void flush() {
        mAvailableInputBufferIndices.clear();
        mAvailableOutputBufferIndices.clear();
        mAvailableOutputBufferInfos.clear();

        mSawInputEOS = false;
        mSawOutputEOS = false;

        if (mAudioTrack != null
                && mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.flush();
        }

        mCodec.flush();
    }

    public boolean isEnded() {
        return mSawInputEOS && mSawOutputEOS;
    }
    final static int YUV420P = 0;
    final static int YUV420SP = 1;
    final static int NV21 = 2;
    static int width ;
    static int height ;
    //根据image获取yuv值-------------------NEW
    public static byte[] getBytesFromImageAsType(Image image, int type){
        try {
            //获取源数据，如果是YUV格式的数据planes.length = 3
            //plane[i]里面的实际数据可能存在byte[].length <= capacity (缓冲区总大小)
            final Image.Plane[] planes = image.getPlanes();

            //数据有效宽度，一般的，图片width <= rowStride，这也是导致byte[].length <= capacity的原因
            // 所以我们只取width部分
            width = image.getWidth();
            height = image.getHeight();

            //此处用来装填最终的YUV数据，需要1.5倍的图片大小，因为Y U V 比例为 4:1:1 （这里是YUV_420_888）
            byte[] yuvBytes = new byte[width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
            //目标数组的装填到的位置
            int dstIndex = 0;

            //临时存储uv数据的
            byte uBytes[] = new byte[width * height / 4];
            byte vBytes[] = new byte[width * height / 4];
            int uIndex = 0;
            int vIndex = 0;

            int pixelsStride, rowStride;
            for (int i = 0; i < planes.length; i++) {
                pixelsStride = planes[i].getPixelStride();
                rowStride = planes[i].getRowStride();

                ByteBuffer buffer = planes[i].getBuffer();

                //如果pixelsStride==2，一般的Y的buffer长度=640*480，UV的长度=640*480/2-1
                //源数据的索引，y的数据是byte中连续的，u的数据是v向左移以为生成的，两者都是偶数位为有效数据
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);

                int srcIndex = 0;
                if (i == 0) {
                    //直接取出来所有Y的有效区域，也可以存储成一个临时的bytes，到下一步再copy
                    for (int j = 0; j < height; j++) {
                        System.arraycopy(bytes, srcIndex, yuvBytes, dstIndex, width);
                        srcIndex += rowStride;
                        dstIndex += width;
                    }
                } else if (i == 1) {
                    //根据pixelsStride取相应的数据
                    for (int j = 0; j < height / 2; j++) {
                        for (int k = 0; k < width / 2; k++) {
                            uBytes[uIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width;
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2;
                        }
                    }
                } else if (i == 2) {
                    //根据pixelsStride取相应的数据
                    for (int j = 0; j < height / 2; j++) {
                        for (int k = 0; k < width / 2; k++) {
                            vBytes[vIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width;
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2;
                        }
                    }
                }
            }
            //   image.close();
            //根据要求的结果类型进行填充
            switch (type) {
                case  YUV420P:
                    System.arraycopy(uBytes, 0, yuvBytes, dstIndex, uBytes.length);
                    System.arraycopy(vBytes, 0, yuvBytes, dstIndex + uBytes.length, vBytes.length);
                    break;
                case YUV420SP:
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = uBytes[i];
                        yuvBytes[dstIndex++] = vBytes[i];
                    }
                    break;
                case NV21:
                    for (int i = 0; i < vBytes.length; i++) {
                        yuvBytes[dstIndex++] = vBytes[i];
                        yuvBytes[dstIndex++] = uBytes[i];
                    }
                    break;
            }
            return yuvBytes;
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
        }
        return null;
    }

    private Bitmap getBitmapFromYUV(byte[] data, int width, int height, int rotation) {
        //使用YuvImage---》NV21
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21,width,height,null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        yuvImage.compressToJpeg(new Rect(0,0,width,height),100,baos);
        byte[] jdata =baos.toByteArray();
        BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
        bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        bitmapFatoryOptions.inSampleSize = 1;
        if(rotation == 0){
            Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
            return bmp;
        }else {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap bmp = BitmapFactory.decodeByteArray(jdata,0,jdata.length,bitmapFatoryOptions);
            Bitmap bml = Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true);
            return bml;
        }
    }


    static int imageNum = 0;
    /**
     * doSomeWork() is the worker function that does all buffer handling and decoding works.
     * It first reads data from {@link MediaExtractor} and pushes it into {@link MediaCodec};
     * it then dequeues buffer from {@link MediaCodec}, consumes it and pushes back to its own
     * buffer queue for next round reading data from {@link MediaExtractor}.
     */
    public void doSomeWork() {
        int indexInput = mCodec.dequeueInputBuffer(0 /* timeoutUs */);

        if (indexInput != MediaCodec.INFO_TRY_AGAIN_LATER) {
            mAvailableInputBufferIndices.add(indexInput);
        }

        while (feedInputBuffer()) {
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int indexOutput = mCodec.dequeueOutputBuffer(info, 0 /* timeoutUs */);

        if (indexOutput == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            mOutputFormat = mCodec.getOutputFormat();
            onOutputFormatChanged();
        } else if (indexOutput == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            mCodecOutputBuffers = mCodec.getOutputBuffers();
        } else if (indexOutput != MediaCodec.INFO_TRY_AGAIN_LATER) {
            mAvailableOutputBufferIndices.add(indexOutput);
            mAvailableOutputBufferInfos.add(info);
        //}else{


        }

        while (drainOutputBuffer()) {
        }
    }

    /** Returns true if more input data could be fed. */
    private boolean feedInputBuffer() throws MediaCodec.CryptoException, IllegalStateException {
        if (mSawInputEOS || mAvailableInputBufferIndices.isEmpty()) {
            return false;
        }

        // stalls read if audio queue is larger than 2MB full so we will not occupy too much heap
        if (mLimitQueueDepth && mAudioTrack != null &&
                mAudioTrack.getNumBytesQueued() > 2 * 1024 * 1024) {
            return false;
        }

        int index = mAvailableInputBufferIndices.peekFirst().intValue();

        ByteBuffer codecData = mCodecInputBuffers[index];

        int trackIndex = mExtractor.getSampleTrackIndex();

        if (trackIndex == mTrackIndex) {
            int sampleSize =
                mExtractor.readSampleData(codecData, 0 /* offset */);

            long sampleTime = mExtractor.getSampleTime();

            int sampleFlags = mExtractor.getSampleFlags();

            if (sampleSize <= 0) {
                Log.d(TAG, "sampleSize: " + sampleSize + " trackIndex:" + trackIndex +
                        " sampleTime:" + sampleTime + " sampleFlags:" + sampleFlags);
                mSawInputEOS = true;
                return false;
            }

            if (!mIsAudio) {
                if (mSampleBaseTimeUs == -1) {
                    mSampleBaseTimeUs = sampleTime;
                }
                sampleTime -= mSampleBaseTimeUs;
                // this is just used for getCurrentPosition, not used for avsync
                mPresentationTimeUs = sampleTime;
            }

            if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                MediaCodec.CryptoInfo info = new MediaCodec.CryptoInfo();
                mExtractor.getSampleCryptoInfo(info);

                mCodec.queueSecureInputBuffer(
                        index, 0 /* offset */, info, sampleTime, 0 /* flags */);
            } else {
                mCodec.queueInputBuffer(
                        index, 0 /* offset */, sampleSize, sampleTime, 0 /* flags */);
            }

            mAvailableInputBufferIndices.removeFirst();
            mExtractor.advance();

            return true;
        } else if (trackIndex < 0) {
            Log.d(TAG, "saw input EOS on track " + mTrackIndex);

            mSawInputEOS = true;

            mCodec.queueInputBuffer(
                    index, 0 /* offset */, 0 /* sampleSize */,
                    0 /* sampleTime */, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            mAvailableInputBufferIndices.removeFirst();
        }

        return false;
    }

    private void onOutputFormatChanged() {
        String mime = mOutputFormat.getString(MediaFormat.KEY_MIME);
        Log.d(TAG, "CodecState::onOutputFormatChanged " + mime);

        mIsAudio = false;
        if (mime.startsWith("audio/")) {
            mIsAudio = true;
            int sampleRate =
                mOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

            int channelCount =
                mOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            Log.d(TAG, "CodecState::onOutputFormatChanged Audio" +
                    " sampleRate:" + sampleRate + " channels:" + channelCount);
            // We do sanity check here after we receive data from MediaExtractor and before
            // we pass them down to AudioTrack. If MediaExtractor works properly, this
            // sanity-check is not necessary, however, in our tests, we found that there
            // are a few cases where ch=0 and samplerate=0 were returned by MediaExtractor.
            if (channelCount < 1 || channelCount > 8 ||
                    sampleRate < 8000 || sampleRate > 128000) {
                return;
            }
            mAudioTrack = new NonBlockingAudioTrack(sampleRate, channelCount);
            mAudioTrack.play();
        }

        if (mime.startsWith("video/")) {
            int width = mOutputFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mOutputFormat.getInteger(MediaFormat.KEY_HEIGHT);
            Log.d(TAG, "CodecState::onOutputFormatChanged Video" +
                    " width:" + width + " height:" + height);
        }
    }

    /** Returns true if more output data could be drained. */
    //audio and video belongs to different codecstate, one has audio, the other one dont
    //so there exists two mPresentationTimeUs, one for audio, the other one for video
    //however, audio and video draining works in the same thread
    private boolean drainOutputBuffer() {
        if (mSawOutputEOS || mAvailableOutputBufferIndices.isEmpty()) {
            return false;
        }

        int index = mAvailableOutputBufferIndices.peekFirst().intValue();
        MediaCodec.BufferInfo info = mAvailableOutputBufferInfos.peekFirst();

        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(TAG, "saw output EOS on track " + mTrackIndex);

            mSawOutputEOS = true;

            return false;
        }

        if (mAudioTrack != null) {
            ByteBuffer buffer = mCodecOutputBuffers[index];
            buffer.clear();
            ByteBuffer audioBuffer = ByteBuffer.allocate(buffer.remaining());
            audioBuffer.put(buffer);
            audioBuffer.clear();

            mAudioTrack.write(audioBuffer, info.size, info.presentationTimeUs*1000);

            mCodec.releaseOutputBuffer(index, false /* render */);

            mPresentationTimeUs = info.presentationTimeUs;

            mAvailableOutputBufferIndices.removeFirst();
            mAvailableOutputBufferInfos.removeFirst();
            return true;
        } else {
            // video
            boolean render;
            long twiceVsyncDurationUs = 2 * mMediaTimeProvider.getVsyncDurationNs()/1000;

            long realTimeUs =
                    mMediaTimeProvider.getRealTimeUsForMediaTime(info.presentationTimeUs); //映射到nowUs时间轴上
            long nowUs = mMediaTimeProvider.getNowUs(); //audio play time
            //String streamType = mAudioTrack == null ? "video:":"audio:";
            //Log.d("avsync", streamType + " presentationUs is " + info.presentationTimeUs + ",realTimeUs is " + realTimeUs + ",nowUs is " + nowUs);
            long lateUs = System.nanoTime()/1000 - realTimeUs;

            if (lateUs < -twiceVsyncDurationUs) {
                // too early;
                return false;
            } else if (lateUs > 30000) {
                Log.d(TAG, "video late by " + lateUs + " us.");
                render = false;
            } else {
                render = true;
                mPresentationTimeUs = info.presentationTimeUs;
            }
            //==========================================================
            boolean doRender = (info.size !=0);
            //获取图片并保存,getOutputImage格式是YUV_420_888
            Image image = mCodec.getOutputImage(index);
           // mCodec.getOutputBuffer(index);
            Log.d("Test","==> 成功获取到图片");
            imageNum++;
            //dateFromImage(image);
            //使用新方法来获取yuv数据
            byte[] bytes = getBytesFromImageAsType(image,2);

            //根据yuv数据获取Bitmap
            Bitmap bitmap = getBitmapFromYUV(bytes,width,height, 0 /*rotation*/);
            //保存图片
            if(bitmap != null){
                //显示图片
                String businesslogofile= Environment.getExternalStorageDirectory()+"/SGPictures/logo"+imageNum+".png";
                File file = new File(businesslogofile);
                File parentFile = file.getParentFile();
                if (!parentFile.exists()) {
                    parentFile.mkdirs();
                }
                try {
                    bitmap.compress(Bitmap.CompressFormat.JPEG,100,new FileOutputStream(file));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                Log.d("Test","<== 图片保存成功");
            }

            //=========================================================

            //mCodec.releaseOutputBuffer(index, render);
            mCodec.releaseOutputBuffer(index, realTimeUs*1000);
            mAvailableOutputBufferIndices.removeFirst();
            mAvailableOutputBufferInfos.removeFirst();
            return true;
        }
    }

    public long getAudioTimeUs() {
        if (mAudioTrack == null) {
            return 0;
        }

        return mAudioTrack.getAudioTimeUs();
    }

    public void process() {
        if (mAudioTrack != null) {
            mAudioTrack.process();
        }
    }
}
