package com.example.rtmppush.projection

import android.media.projection.MediaProjection
import com.example.rtmppush.rtmp.PushRtmp
import com.example.rtmppush.rtmp.PushTask
import com.example.rtmppush.rtmp.RTMPPackage
import java.util.concurrent.LinkedBlockingQueue

/**
 * author: 80342867
 * created on: 2022/5/13 013 11:03
 * description:
 */
class MediaProjectionPush(private val callback: ((Boolean)->Unit)? = null) : Runnable {
    private var mCodecVideoStream: CodecVideoStream? = null
    private var mUrl:String? = null
    private var mProjection: MediaProjection? = null
    private val queue: LinkedBlockingQueue<RTMPPackage> = LinkedBlockingQueue()
    private var mbLiving: Boolean = false

    fun startPush(url: String, mediaProjection: MediaProjection?) {
        mUrl = url
        mProjection = mediaProjection
        PushTask.getInstance().execute(this)
        callback?.invoke(true)
    }

    fun stopPush() {
        callback?.invoke(false)
        mCodecVideoStream?.stopPush()
        mbLiving = false
    }

    private fun addPackage(data: ByteArray, time: Long) {
        if (!mbLiving) return
        queue.add(RTMPPackage(data, time))
    }

    override fun run() {
        if (mUrl == null) {
            return
        }

        if (!PushRtmp.getInstance().connectServer(mUrl!!)) {
            return
        }

        mbLiving = true

        mCodecVideoStream = CodecVideoStream(this::addPackage).apply { startPush(mProjection) }

        while (mbLiving) {
            var rtmpPackage: RTMPPackage? = null
            try {
                rtmpPackage = queue.take()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            rtmpPackage?.apply {
                buffer?.let {
                    if (it.isNotEmpty()) {
                        PushRtmp.getInstance().sendVideoData(it, it.size, this.time)
                    }
                }
            }
        }
    }
}