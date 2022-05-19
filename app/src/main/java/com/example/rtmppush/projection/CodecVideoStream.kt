package com.example.rtmppush.projection

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Bundle
import com.example.rtmppush.rtmp.PushTask
import java.io.IOException

/**
 * author: 80342867
 * created on: 2022/5/13 013 11:24
 * description:
 */
class CodecVideoStream(private val callback:((ByteArray,Long)->Unit)? = null) : Runnable {

    private lateinit var mVirtualDisplay: VirtualDisplay
    private lateinit var mCodec: MediaCodec

    private lateinit var mProjection: MediaProjection
    
    private var timeStamp: Long = 0
    private var startTime: Long = 0
    private var mbLiving: Boolean = false

    fun startPush(projection: MediaProjection?) {
        mProjection = projection!!
        val format: MediaFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 720, 1280)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        // 设置过低会影响清晰度
        format.setInteger(MediaFormat.KEY_BIT_RATE, 4000_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        try {
            mCodec = MediaCodec.createEncoderByType("video/avc")
            mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = mCodec.createInputSurface()
            mVirtualDisplay = mProjection.createVirtualDisplay(
                "screen-codec",
                720,
                1280,
                1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface,
                null,
                null
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }

        PushTask.getInstance().execute(this)
    }

    fun stopPush() {
        mbLiving = false
    }

    override fun run() {
        mbLiving = true
        mCodec.start()
        val bufferInfo = MediaCodec.BufferInfo()
        while (mbLiving) {
            //隔2s触发 I 帧
            if (System.currentTimeMillis() - timeStamp >= 2000) {
                val params = Bundle()
                params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                mCodec.setParameters(params)
                timeStamp = System.currentTimeMillis()
            }
            val index = mCodec.dequeueOutputBuffer(bufferInfo, 100_000)
            if (index >= 0) {
                if (startTime == 0L) {
                    startTime = bufferInfo.presentationTimeUs / 1000
                }

                val buffer = mCodec.getOutputBuffer(index)
                val outData = ByteArray(bufferInfo.size)
                buffer?.get(outData)

                callback?.invoke(outData, (bufferInfo.presentationTimeUs / 1000) - startTime)
                mCodec.releaseOutputBuffer(index, false)
            }
        }
        mbLiving = false
        if (this::mCodec.isInitialized) {
            mCodec.stop()
            mCodec.release()
        }
        if (this::mVirtualDisplay.isInitialized) {
            mVirtualDisplay.release()
        }
        if (this::mProjection.isInitialized) {
            mProjection.stop()
        }
        startTime = 0
    }
}