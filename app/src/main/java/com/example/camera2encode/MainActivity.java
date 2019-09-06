package com.example.camera2encode;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private Handler childHandler;
    private Handler mainHandler;
    private String mCameraId;
    private ImageReader mImageReader;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private MediaCodec mediaCodec;
    private BufferedOutputStream output;
    private Button button1;
    private Button button2;
    private TextView text;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initCamera2();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

    }

    void initView(){
        button1 = findViewById(R.id.button1);
        button2 = findViewById(R.id.button2);
        text = findViewById(R.id.text);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCamera();
            }
        });
    }
    void initEncoder() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/capture.h264";
        text.setText(path);
        File file = new File(path);
        if (!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            output = new BufferedOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mediaCodec = MediaCodec.createEncoderByType("video/avc");
                //Log.e("mediaCodec "," " + mediaCodec);
                MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",640,480);
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,250000);
                mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,15);
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
                mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,-1);
                mediaCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void onEncodeFrame(byte[] buffer, int offset, int length, int flag) throws IOException {
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0){
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            Log.i("zhou","input buffer size " + inputBuffer.remaining());
            inputBuffer.put(buffer,offset,length);
            mediaCodec.queueInputBuffer(inputBufferIndex,0,length,System.currentTimeMillis(),0);
        }else {
            return;
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
        while (outBufferIndex >= 0){

            ByteBuffer outBuffer = outputBuffers[outBufferIndex];
            byte[] outData = new byte[bufferInfo.size];
            outBuffer.get(outData);
            output.write(outData);
            mediaCodec.releaseOutputBuffer(outBufferIndex,true);
            outBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
        }
    }

    @SuppressLint("MissingPermission")
    void initCamera2(){
        HandlerThread handlerThread = new HandlerThread("camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());
        mCameraId = "" + CameraCharacteristics.LENS_FACING_BACK;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mImageReader = ImageReader.newInstance(640,480, ImageFormat.YUV_420_888,2);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireNextImage();
                    byte[] real_bytes = new byte[640 * 480 + 640 * 240];
                    int dest_pos = 0;
                    for (int i=0; i<2; i++) {
                        Image.Plane plane = image.getPlanes()[i];
                        ByteBuffer buffer = plane.getBuffer();
                        int pixelStride = plane.getPixelStride();
                        Log.i("zhou","buffer size  pixel stride " + buffer.remaining() + pixelStride);
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        System.arraycopy(bytes,0,real_bytes,dest_pos,bytes.length);
                        dest_pos = dest_pos + bytes.length;

                    }
                    try {
                        onEncodeFrame(real_bytes,0,real_bytes.length,0);
                    } catch (IOException e) {

                        e.printStackTrace();
                    }

                    image.close();
                }
            },mainHandler);

        }

    }
    @SuppressLint("MissingPermission")
    void openCamera(){
        initEncoder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        mCameraDevice = camera;
                        takePreview();
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        if (null != mCameraDevice){
                            mCameraDevice.close();
                            mCameraDevice = null;
                        }
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        Log.i("zhou","open camera failed!");
                    }
                },childHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
    void takePreview(){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(mImageReader.getSurface());
                mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        mCaptureSession = session;
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        try {
                            mCaptureSession.setRepeatingRequest(previewRequest, new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                                }

                                @Override
                                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                                    super.onCaptureProgressed(session, request, partialResult);
                                }

                                @Override
                                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                    super.onCaptureCompleted(session, request, result);
                                }

                                @Override
                                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                                    super.onCaptureFailed(session, request, failure);
                                }
                            },childHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {

                    }
                },childHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    void closeCamera(){

        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mediaCodec){
            mediaCodec.flush();
            //mediaCodec.stop();
        }
    }
}
