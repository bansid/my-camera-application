package com.mycamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_my_camera.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MyCameraActivity : AppCompatActivity(), View.OnClickListener, TextureView.SurfaceTextureListener {

    companion object {
        const val TAG = "MyCameraActivity"
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }

    private lateinit var textureView: TextureView
    private lateinit var cameraId: String
    var cameraDevice: CameraDevice? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraStateCallbacks: CameraDevice.StateCallback
    private val defaultFacing = CameraCharacteristics.LENS_FACING_BACK


    private lateinit var previewSize: Size
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private val mediaRecorder: MediaRecorder by lazy {
        MediaRecorder()
    }
    private var isVideoRecording = false


    private var backgroundHandlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_camera)
        textureView = findViewById(R.id.myTextureView)
        setListener()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        initializeCameraCallback()

    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            setupCamera(textureView.width, textureView.height)
            connectToCamera()
        } else {
            textureView.surfaceTextureListener = this
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }


    private fun initializeCameraCallback() {
        cameraStateCallbacks = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startCameraPreview()
                Log.d(TAG, "Preview is starting")
                Toast.makeText(this@MyCameraActivity, "Preview is starting", Toast.LENGTH_SHORT).show()
            }

            override fun onDisconnected(camera: CameraDevice) {}

            override fun onError(camera: CameraDevice, error: Int) {}
        }
    }

    private fun setupCamera(width: Int, height: Int) {
        for (cId in cameraManager.cameraIdList) {
            val characteristic = cameraManager.getCameraCharacteristics(cId)
            if (characteristic.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                cameraId = cId
                val configMap = characteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                previewSize = configMap!!.getOutputSizes(SurfaceTexture::class.java)[0]
            }
        }
    }


    private fun connectToCamera() {
        try {
            cameraManager.openCamera(cameraId, cameraStateCallbacks, backgroundHandler)
        } catch (e: SecurityException) {
        }
    }

    private fun startCameraPreview() {
        val previewSurfaceTexture = textureView.surfaceTexture
        previewSurfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(previewSurfaceTexture)

        captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(previewSurface)


        cameraDevice?.createCaptureSession(
            arrayListOf(previewSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d(TAG, "Camera configuration failed")
                    Toast.makeText(this@MyCameraActivity, "Camera configuration failed", Toast.LENGTH_SHORT).show()
                }
            },
            null
        )
    }

    private fun closeCamera() {

        if (cameraDevice != null) {
            cameraDevice!!.close()
            cameraDevice = null
        }

    }

    private fun getGalleryFolder(): File? {
        val storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        Log.d(TAG, "$storageDirectory")
        val gallery = File(storageDirectory, "CamPractice")
        if (!gallery.exists()) {
            gallery.mkdirs()
        }
        return gallery
    }

    private fun createMediaFile(type: Int): File {
        val prefix = if (type == TYPE_IMAGE) "IMG_" else "VID_"
        val suffix = if (type == TYPE_IMAGE) ".jpg" else ".mp4"

        val time: String =
            SimpleDateFormat("yyyyMMdd_HHMMSS", Locale.getDefault()).format(Date())
        val fileImageVideo = prefix + time
        Log.d(TAG, "$fileImageVideo")
        return File.createTempFile(fileImageVideo, suffix, getGalleryFolder())

    }

    private fun setupVideoRecorder() {
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(createMediaFile(TYPE_VIDEO).absolutePath)
            setVideoEncodingBitRate(1000000)
            setVideoFrameRate(30)
            setVideoSize(previewSize.width, previewSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOrientationHint(90)
        }
        mediaRecorder.prepare()
    }

    private fun startVideoRecording() {
        setupVideoRecorder()
        val previewSurfaceTexture = textureView.surfaceTexture
        previewSurfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(previewSurfaceTexture)
        val recordSurface = mediaRecorder.surface

        captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilder.addTarget(previewSurface)
        captureRequestBuilder.addTarget(recordSurface)


        cameraDevice!!.createCaptureSession(
            mutableListOf(previewSurface, recordSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d(TAG, "Camera Configuration FAiled")
                    Toast.makeText(this@MyCameraActivity, "Camera Configuration FAiled", Toast.LENGTH_SHORT).show()
                }
            },
            null
        )
    }

    private fun capturePicture() {
        val fos = FileOutputStream(createMediaFile(TYPE_IMAGE))
        textureView.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread!!.quitSafely()
        backgroundHandlerThread!!.join()
        backgroundHandlerThread = null
        backgroundHandler = null
    }

    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("Camera Background")
        backgroundHandlerThread!!.start()
        backgroundHandler = Handler(backgroundHandlerThread!!.looper)
    }

    private fun setListener() {
        buttonClickPicture.setOnClickListener(this)
        buttonRecordVideo.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.buttonClickPicture -> {
                capturePicture()
            }
            R.id.buttonRecordVideo -> {
                if (isVideoRecording) {
                    stopVideoRecording()
                    handleVideoRecordButtonState()
                    //startCameraPreview()
                } else {
                    startVideoRecording()
                    mediaRecorder.start()
                    isVideoRecording = true
                    handleVideoRecordButtonState()
                }

            }
        }
    }

    private fun stopVideoRecording() {
        isVideoRecording = false
        mediaRecorder.stop()
        mediaRecorder.reset()

    }

    private fun handleVideoRecordButtonState() {
        if (isVideoRecording) {
            buttonRecordVideo.setBackgroundDrawable(resources.getDrawable(R.drawable.ic_pause, null))
        } else {
            buttonRecordVideo.setBackgroundDrawable(resources.getDrawable(R.drawable.ic_play, null))
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        Log.d(TAG, "Surface Texture Available")
        Toast.makeText(this@MyCameraActivity, "Surface Texture Available", Toast.LENGTH_SHORT).show()
        setupCamera(width, height)
        connectToCamera()
    }
}
