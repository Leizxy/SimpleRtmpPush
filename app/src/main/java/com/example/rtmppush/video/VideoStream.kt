package com.example.rtmppush.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.view.PreviewView
import com.example.rtmppush.camera.CameraPreviewHelper
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

/**
 * author: 80342867
 * created on: 2022/5/16 016 11:10
 * description: 
 */
class VideoStream(
    mContext: AppCompatActivity,
    mPreviewView: PreviewView,
    mLensFacing: Int = CameraSelector.LENS_FACING_BACK,
    private var mWidth: Int = 640,
    private var mHeight: Int = 480,
    private var callback: ((Boolean) -> Unit)? = null
) : CameraPreviewHelper(mContext, mPreviewView, mLensFacing), ImageAnalysis.Analyzer {

    private var timeStamp: Long = 0
    private var mVideoPush: VideoPush? = null

    private var mVideoCodec: MediaCodec? = null
    private var mbLiving: Boolean = false
    private val lock: ReentrantLock = ReentrantLock()

    private lateinit var y: ByteArray
    private lateinit var u: ByteArray
    private lateinit var v: ByteArray
    private var mStartTime: Long = -1

    override fun getUseCases(): Array<UseCase> {
        val imageAnalysis =
            ImageAnalysis.Builder().build().also { it.setAnalyzer(Executors.newSingleThreadExecutor(), this) }
        return super.getUseCases().plus(imageAnalysis)
    }

    override fun analyze(image: ImageProxy) {
        mWidth = image.width
        mHeight = image.height

        if (!mbLiving) {
            image.close()
            return
        }

        val planes = image.planes
        if (!this::y.isInitialized) {
            y = ByteArray(planes[0].buffer.limit() - planes[0].buffer.position())
            u = ByteArray(planes[1].buffer.limit() - planes[1].buffer.position())
            v = ByteArray(planes[2].buffer.limit() - planes[2].buffer.position())
        }

        if (image.planes[0].buffer.remaining() == y.size) {
            planes[0].buffer.get(y)
            planes[1].buffer.get(u)
            planes[2].buffer.get(v)

            val nv21 = ByteArray(mHeight * mWidth * 3 / 2)
            yuvToNv21(y, u, v, nv21, mWidth, mHeight)

            encodeNv21(nv21)
        }

        image.close()
    }

    private fun encodeNv21(nv21: ByteArray) {
        if (mbLiving) {
            mVideoCodec?.let {
                //隔2s触发 I 帧
                if (System.currentTimeMillis() - timeStamp >= 2000) {
                    val params = Bundle()
                    params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                    it.setParameters(params)
                    timeStamp = System.currentTimeMillis()
                }

                val inputBufferIndex = it.dequeueInputBuffer(0)
                val length = nv21.size
                var inputBuffer: ByteBuffer? = null
                if (inputBufferIndex >= 0) {
                    inputBuffer = it.getInputBuffer(inputBufferIndex)?.also { buffer ->
                        buffer.clear()
                        buffer.put(nv21)
                    }

                    it.queueInputBuffer(inputBufferIndex, 0, length, System.nanoTime(), 0)
                }

                val bufferInfo = MediaCodec.BufferInfo()
                var outBufferIndex = it.dequeueOutputBuffer(bufferInfo, 0)

                if (MediaCodec.INFO_OUTPUT_FORMAT_CHANGED == outBufferIndex) {
                    val data = it.outputFormat.getByteBuffer("csd-0").array().plus(it.outputFormat.getByteBuffer("csd-1").array())
                    mVideoPush?.addSpsAndPps(data)
                }

                while (outBufferIndex >= 0) {
                    if (mStartTime == -1L) {
                        mStartTime = bufferInfo.presentationTimeUs / 1000
                    }

                    var outputBuffer: ByteBuffer? = null

                    try {
                        outputBuffer = mVideoCodec?.getOutputBuffer(outBufferIndex)
                        val outData = ByteArray(bufferInfo.size)
                        outputBuffer?.get(outData)
                        mVideoPush?.addVideoData(outData, (bufferInfo.presentationTimeUs / 1000) - mStartTime)
                    } catch (e: Exception) {
                    }

                    mVideoCodec?.releaseOutputBuffer(outBufferIndex, false)
                    outBufferIndex = it.dequeueOutputBuffer(bufferInfo, 0)
                }

                inputBuffer?.clear()
            }
        }
    }

    fun startPush(url: String) {
        if (mVideoPush == null) {
            mVideoPush = VideoPush(url)
            mVideoPush!!.startPush()
        }
        // h264
        mVideoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(getVideoFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
            mbLiving = true
        }

        callback?.invoke(true)
    }

    private fun getVideoFormat(): MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 25)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

    fun stopPush() {
        callback?.invoke(false)
        mbLiving = false

        if (mVideoPush != null) {
            mVideoPush!!.stopPush()
            mVideoPush = null
        }

        mVideoCodec?.let {
            mStartTime = -1
            it.stop()
            it.release()
        }
    }

    private fun yuvToNv21(
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
        nv21: ByteArray,
        stride: Int,
        height: Int,
    ) {
        System.arraycopy(y, 0, nv21, 0, y.size)
        val length = y.size + u.size / 2 + v.size / 2
        var uIndex = 0
        var vIndex = 0
        for (i in stride * height until length step 2) {
            nv21[i] = v[vIndex]
            nv21[i + 1] = u[uIndex]
            vIndex += 2
            uIndex += 2
        }
    }
}