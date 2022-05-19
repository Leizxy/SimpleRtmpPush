package com.example.rtmppush

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.rtmppush.camera.CameraPreviewHelper
import com.example.rtmppush.databinding.ActivityMainBinding
import com.example.rtmppush.projection.MediaProjectionPush
import com.example.rtmppush.video.VideoStream

class MainActivity : AppCompatActivity() {

    private var mVideoStream: VideoStream? = null
    private var mProjectionPush: MediaProjectionPush? = null
    private lateinit var binding: ActivityMainBinding
    private val urlPre: String = "rtmp://live-push.bilivideo.com/live-bvc/"
    private val urlSuffix: String =
        "?streamname=live_8379703_1896767&key=e1fbbebffccce5bf50e4143974d2e359&schedule=rtmp&pflag=1"
    private lateinit var mMediaProjectionManager: MediaProjectionManager
    private val mbPushProjection: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btn.setOnClickListener { click() }

        if (!allPermissionGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        if (mbPushProjection) {
            CameraPreviewHelper(this, binding.viewFinder)
        } else {
            mVideoStream = VideoStream(this, binding.viewFinder, callback = this@MainActivity::changeBtn)
        }
    }

    private fun changeBtn(select: Boolean) {
        binding.btn.isSelected = select
    }

    private fun click() {
        if (mbPushProjection) {
            pushProjection()
        } else {
            pushVideo()
        }
    }

    private fun pushVideo() {
        mVideoStream?.run {
            if (binding.btn.isSelected) {
                stopPush()
            } else {
                startPush("$urlPre$urlSuffix")
            }
        }
    }

    private fun pushProjection() {
        if (binding.btn.isSelected) {
            mProjectionPush?.stopPush()
        } else {
            mMediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = mMediaProjectionManager.createScreenCaptureIntent()
            startActivityForResult(intent, REQUEST_CODE_MEDIA_PROJECTION)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.let {
            if (requestCode == REQUEST_CODE_MEDIA_PROJECTION && resultCode == RESULT_OK) {
                val mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, it)
                mProjectionPush = MediaProjectionPush(callback = this::changeBtn).apply {
                    startPush("$urlPre$urlSuffix", mediaProjection)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }


    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (REQUEST_CODE_PERMISSIONS == requestCode) {

        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_MEDIA_PROJECTION = 100
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            ).apply {
            }.toTypedArray()
    }
}